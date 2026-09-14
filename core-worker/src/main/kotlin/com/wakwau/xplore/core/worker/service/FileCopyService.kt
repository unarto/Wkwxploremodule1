// [Jalur Class/Modul]: core-worker/src/main/kotlin/com/wakwau/xplore/core/worker/service/FileCopyService.kt
// [Penjelasan]: Foreground Service untuk mengelola dan memantau proses penyalinan/pemindahan berkas di latar belakang.
package com.wakwau.xplore.core.worker.service

import kotlinx.coroutines.CancellationException
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.wakwau.xplore.fileoperations.conflict.ConflictChoice
import com.wakwau.xplore.fileoperations.conflict.ResolvedTransferItem
import com.wakwau.xplore.core.storage.constant.StorageConstants
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.BackgroundOperationType
import com.wakwau.xplore.core.storage.operation.FileOperationComponentProvider
import com.wakwau.xplore.core.storage.operation.FileOperationError
import com.wakwau.xplore.core.storage.operation.FileOperationProgress
import com.wakwau.xplore.core.storage.operation.FileOperationProgressDispatcher
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import com.wakwau.xplore.core.storage.repository.FileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
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
                val type = BackgroundOperationType.valueOf(typeStr)
                val sourcesJson = intent.getStringExtra(KEY_SOURCES)
                val destJson = intent.getStringExtra(KEY_DESTINATION)
                val resolvedJson = intent.getStringExtra(KEY_RESOLVED_ITEMS)
                val notification = notificationManager.createNotification("Memproses Berkas...", 0, 100)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(notificationManager.notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                    } else {
                        startForeground(notificationManager.notificationId, notification)
                    }
                } catch (e: Exception) {
            if (e is CancellationException) throw e
                    Log.e(TAG, "Gagal mengaktifkan startForeground service: ${e.message}", e)
                }
                
                if (resolvedJson != null) {
                    val resolvedItems = FileOperationIntentParser.parseResolvedItems(resolvedJson)
                    startResolvedOperation(type, resolvedItems)
                } else if (sourcesJson != null) {
                    val sources = FileOperationIntentParser.parseStorageLocations(sourcesJson)
                    val destination = destJson?.let { FileOperationIntentParser.parseStorageLocation(JSONObject(it)) }
                    startOperation(type, sources, destination)
                }
            }
            ACTION_CANCEL -> {
                currentOperationJob?.cancel()
                serviceScope.launch {
                    resolveProgressDispatcher()?.emitProgress(FileOperationResult.Cancelled)
                    stopForeground(true)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startOperation(type: BackgroundOperationType, sources: List<StorageLocation>, destination: StorageLocation?) {
        currentOperationJob?.cancel()
        currentOperationJob = serviceScope.launch {
            val fileRepo = resolveFileRepository()
            val dispatcher = resolveProgressDispatcher()
            if (fileRepo == null || dispatcher == null) {
                dispatcher?.emitProgress(FileOperationResult.Failure(FileOperationError.UNKNOWN))
                stopSelf()
                return@launch
            }
            var isFailedOrCancelled = false
            try {
                when (type) {
                    BackgroundOperationType.COPY, BackgroundOperationType.MOVE -> {
                        if (destination == null) {
                            dispatcher.emitProgress(FileOperationResult.Failure(FileOperationError.INVALID_LOCATION))
                            stopSelf()
                            return@launch
                        }
                        for (source in sources) {
                            if (!currentCoroutineContext().isActive) {
                                dispatcher.emitProgress(FileOperationResult.Cancelled)
                                break
                            }
                            val destLoc = createTargetLocation(source, destination)
                            val src = source.path
                            val dest = destLoc.path
                            // [Jalur Class/Modul]: core-worker/src/main/kotlin/com/wakwau/xplore/core/worker/service/FileCopyService.kt
                            // [Penjelasan]: Logging verifikasi eksekusi penulisan file fisik pada loop eksekusi service
                            Log.e("COPY_REAL_EXEC", "Service executing file write from $src to $dest")
                            val flow = if (type == BackgroundOperationType.COPY) fileRepo.copy(source, destLoc) else fileRepo.move(source, destLoc)
                            flow.collect { result ->
                                handleProgress(result)
                                if (result is FileOperationResult.Failure || result is FileOperationResult.Cancelled) {
                                    isFailedOrCancelled = true
                                }
                            }
                            if (isFailedOrCancelled) break
                        }
                    }
                    BackgroundOperationType.DELETE -> {
                        val totalCount = sources.size.toLong()
                        var deletedCount = 0L
                        for (source in sources) {
                            if (!currentCoroutineContext().isActive) {
                                dispatcher.emitProgress(FileOperationResult.Cancelled)
                                break
                            }
                            when (val result = fileRepo.delete(source)) {
                                is FileOperationResult.Failure -> {
                                    dispatcher.emitProgress(FileOperationResult.Failure(result.error))
                                    isFailedOrCancelled = true
                                    break
                                }
                                is FileOperationResult.Cancelled -> {
                                    dispatcher.emitProgress(FileOperationResult.Cancelled)
                                    isFailedOrCancelled = true
                                    break
                                }
                                else -> {
                                    deletedCount++
                                    val fileName = source.path.trimEnd('/').substringAfterLast('/')
                                    handleProgress(FileOperationResult.Success(FileOperationProgress(deletedCount, totalCount, fileName)))
                                }
                            }
                        }
                    }
                }
            } finally {
                // [CopyFix]: Perbaikan manifest service path & emisi status selesai berdasarkan copy.md
                if (!isFailedOrCancelled) {
                    dispatcher.emitProgress(FileOperationResult.Completed(type))
                }
                stopForeground(true)
                stopSelf()
            }
        }
    }

    private fun startResolvedOperation(type: BackgroundOperationType, resolvedItems: List<ResolvedTransferItem>) {
        currentOperationJob?.cancel()
        currentOperationJob = serviceScope.launch {
            val fileRepo = resolveFileRepository()
            val dispatcher = resolveProgressDispatcher()
            if (fileRepo == null || dispatcher == null) {
                dispatcher?.emitProgress(FileOperationResult.Failure(FileOperationError.UNKNOWN))
                stopSelf()
                return@launch
            }
            var isFailedOrCancelled = false
            try {
                for (item in resolvedItems) {
                    if (!currentCoroutineContext().isActive) {
                        dispatcher.emitProgress(FileOperationResult.Cancelled)
                        break
                    }
                    if (item.choice == ConflictChoice.SKIP) continue
                    val src = item.source.path
                    val dest = item.targetLocation.path
                    // [Jalur Class/Modul]: core-worker/src/main/kotlin/com/wakwau/xplore/core/worker/service/FileCopyService.kt
                    // [Penjelasan]: Logging verifikasi eksekusi penulisan file fisik pada loop eksekusi resolved transfer service
                    Log.e("COPY_REAL_EXEC", "Service executing file write from $src to $dest")
                    val flow = if (type == BackgroundOperationType.COPY) {
                        fileRepo.copy(item.source, item.targetLocation)
                    } else {
                        fileRepo.move(item.source, item.targetLocation)
                    }
                    flow.collect { result ->
                        handleProgress(result)
                        if (result is FileOperationResult.Failure || result is FileOperationResult.Cancelled) {
                            isFailedOrCancelled = true
                        }
                    }
                    if (isFailedOrCancelled) break
                }
            } finally {
                // [CopyFix]: Perbaikan manifest service path & emisi status selesai berdasarkan copy.md
                if (!isFailedOrCancelled) {
                    dispatcher.emitProgress(FileOperationResult.Completed(type))
                }
                stopForeground(true)
                stopSelf()
            }
        }
    }

    private suspend fun handleProgress(result: FileOperationResult<FileOperationProgress>) {
        val progressDispatcher = resolveProgressDispatcher()
        if (result is FileOperationResult.Success) {
            val currentTime = System.currentTimeMillis()
            val p = result.data
            val isComplete = p.bytesWritten >= p.totalBytes
            if (isComplete || currentTime - lastProgressUpdateTime > 200) {
                lastProgressUpdateTime = currentTime
                progressDispatcher?.emitProgress(result)
                val progressPercentage = if (p.totalBytes > 0L) {
                    ((p.bytesWritten.toDouble() / p.totalBytes.toDouble()) * 100.0).toInt()
                } else {
                    0
                }
                val notification = notificationManager.createNotification(p.fileName, progressPercentage, 100)
                notificationManager.notifyProgress(notification)
            }
        } else {
            progressDispatcher?.emitProgress(result)
        }
    }

    private fun createTargetLocation(source: StorageLocation, destination: StorageLocation): StorageLocation {
        val sourceName = source.path.trimEnd('/').substringAfterLast('/')
        return if (destination.path.startsWith(StorageConstants.CONTENT_SCHEME_PREFIX)) {
            val cleanDest = destination.path.substringBefore("#")
            StorageLocation(path = "$cleanDest#$sourceName", rootId = destination.rootId)
        } else {
            val cleanDestPath = if (destination.path.endsWith("/")) "${destination.path}$sourceName" else "${destination.path}/$sourceName"
            StorageLocation(path = cleanDestPath, rootId = destination.rootId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
