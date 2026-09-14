// [Jalur Class/Modul]: core-worker/src/main/kotlin/com/wakwau/xplore/core/worker/service/FileCopyService.kt
// [Penjelasan]: Foreground Service untuk mengelola dan memantau proses penyalinan/pemindahan berkas di latar belakang.
package com.wakwau.xplore.core.worker.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.wakwau.xplore.core.storage.operation.BackgroundOperationType
import com.wakwau.xplore.core.storage.operation.BackgroundOperationEvent
import com.wakwau.xplore.core.storage.operation.FileOperationComponentProvider
import com.wakwau.xplore.core.storage.operation.FileOperationError
import com.wakwau.xplore.core.storage.operation.FileOperationProgress
import com.wakwau.xplore.core.storage.operation.FileOperationProgressDispatcher
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import com.wakwau.xplore.core.storage.repository.FileRepository
import com.wakwau.xplore.fileoperations.executor.FileOperationExecutor
import com.wakwau.xplore.fileoperations.executor.FileOperationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

class FileCopyService(
    private val injectedRepository: FileRepository? = null,
    private val injectedProgressDispatcher: FileOperationProgressDispatcher? = null
) : Service() {

    constructor() : this(null, null)

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var currentOperationJob: Job? = null
    private lateinit var notificationManager: FileOperationNotificationManager
    private var lastProgressUpdateTime = 0L

    companion object {
        private const val TAG = "FileCopyService"
        const val ACTION_START = "ACTION_START"
        const val ACTION_CANCEL = "ACTION_CANCEL"
        const val KEY_OPERATION_TYPE = "KEY_OPERATION_TYPE"
        const val KEY_OPERATION_ID = "KEY_OPERATION_ID"
        const val KEY_SOURCES = "KEY_SOURCES"
        const val KEY_DESTINATION = "KEY_DESTINATION"
        const val KEY_RESOLVED_ITEMS = "KEY_RESOLVED_ITEMS"
    }

    private fun resolveFileRepository(): FileRepository? =
        injectedRepository ?: (applicationContext as? FileOperationComponentProvider)?.fileRepository

    private fun resolveProgressDispatcher(): FileOperationProgressDispatcher? =
        injectedProgressDispatcher ?: (applicationContext as? FileOperationComponentProvider)?.operationProgressDispatcher

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = FileOperationNotificationManager(this)
        notificationManager.createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val typeStr = intent.getStringExtra(KEY_OPERATION_TYPE) ?: return START_NOT_STICKY
                val operationId = intent.getStringExtra(KEY_OPERATION_ID) ?: return START_NOT_STICKY
                val type = BackgroundOperationType.valueOf(typeStr)
                val sourcesJson = intent.getStringExtra(KEY_SOURCES)
                val destJson = intent.getStringExtra(KEY_DESTINATION)
                val resolvedJson = intent.getStringExtra(KEY_RESOLVED_ITEMS)
                if (resolvedJson != null) {
                    val resolvedItems = FileOperationIntentParser.parseResolvedItems(resolvedJson)
                    launchOperation(
                        operationId,
                        FileOperationRequest(type = type, resolvedItems = resolvedItems)
                    )
                } else if (sourcesJson != null) {
                    val sources = FileOperationIntentParser.parseStorageLocations(sourcesJson)
                    val destination = destJson?.let { FileOperationIntentParser.parseStorageLocation(JSONObject(it)) }
                    launchOperation(
                        operationId,
                        FileOperationRequest(type = type, sources = sources, destination = destination)
                    )
                }
            }
            ACTION_CANCEL -> {
                val operation = currentOperationJob
                if (operation != null) operation.cancel() else stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun launchOperation(
        operationId: String,
        request: FileOperationRequest
    ) {
        currentOperationJob?.cancel()
        currentOperationJob = serviceScope.launch {
            val fileRepo = resolveFileRepository()
            val dispatcher = resolveProgressDispatcher()
            if (fileRepo == null || dispatcher == null) {
                dispatcher?.emitProgress(BackgroundOperationEvent(operationId, FileOperationResult.Failure(FileOperationError.UNKNOWN)))
                stopSelf()
                return@launch
            }

            val notification = notificationManager.createNotification("Memproses Berkas...", 0, 100)
            val executor = FileOperationExecutor(fileRepo)
            ForegroundOperationExecutionGate.execute(
                operationId = operationId,
                operationType = request.type,
                dispatcher = dispatcher,
                startForeground = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            notificationManager.notificationId,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        )
                    } else {
                        startForeground(notificationManager.notificationId, notification)
                    }
                },
                operation = {
                    executor.execute(request) { result -> handleProgress(operationId, result) }
                },
                onStartupFailure = { error ->
                    Log.e(TAG, "Gagal mengaktifkan startForeground service: ${error.message}", error)
                },
                cleanup = {
                    stopForeground(true)
                    stopSelf()
                }
            )
        }
    }

    private suspend fun handleProgress(operationId: String, result: FileOperationResult<FileOperationProgress>) {
        val progressDispatcher = resolveProgressDispatcher()
        if (result is FileOperationResult.Success) {
            val currentTime = System.currentTimeMillis()
            val p = result.data
            val isComplete = p.bytesWritten >= p.totalBytes
            if (isComplete || currentTime - lastProgressUpdateTime > 200) {
                lastProgressUpdateTime = currentTime
                progressDispatcher?.emitProgress(BackgroundOperationEvent(operationId, result))
                val progressPercentage = if (p.totalBytes > 0L) {
                    ((p.bytesWritten.toDouble() / p.totalBytes.toDouble()) * 100.0).toInt()
                } else {
                    0
                }
                val notification = notificationManager.createNotification(p.fileName, progressPercentage, 100)
                notificationManager.notifyProgress(notification)
            }
        } else {
            progressDispatcher?.emitProgress(BackgroundOperationEvent(operationId, result))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
