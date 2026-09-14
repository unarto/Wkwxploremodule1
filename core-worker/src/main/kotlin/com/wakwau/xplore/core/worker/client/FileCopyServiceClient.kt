// [Jalur Class/Modul]: core-worker/src/main/kotlin/com/wakwau/xplore/core/worker/client/FileCopyServiceClient.kt
// [Penjelasan]: Klien eksekusi operasi berkas latar belakang di modul :core-worker yang mengimplementasikan kontrak BackgroundOperationClient dari :file-operations dan mendelegasikan ke FileCopyService melalui Android Service API tanpa pola Manager.
package com.wakwau.xplore.core.worker.client

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.wakwau.xplore.fileoperations.conflict.ResolvedTransferItem
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.fileoperations.client.BackgroundOperationClient
import com.wakwau.xplore.core.storage.operation.BackgroundOperationType
import com.wakwau.xplore.core.storage.operation.BackgroundOperationEvent
import com.wakwau.xplore.core.storage.operation.FileOperationProgressDispatcher
import com.wakwau.xplore.core.worker.service.FileCopyService
import com.wakwau.xplore.core.worker.service.FileOperationIntentParser
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class FileCopyServiceClient(
    private val context: Context,
    private val progressDispatcher: FileOperationProgressDispatcher
) : BackgroundOperationClient {

    override fun enqueueOperation(
        type: BackgroundOperationType,
        sources: List<StorageLocation>,
        destination: StorageLocation?
    ): String {
        val operationId = UUID.randomUUID().toString()
        val sourcesJson = JSONArray()
        sources.forEach { 
            val obj = JSONObject()
            obj.put(FileOperationIntentParser.KEY_PATH, it.path)
            obj.put(FileOperationIntentParser.KEY_ROOT_ID, it.rootId)
            sourcesJson.put(obj)
        }

        val intent = Intent(context, FileCopyService::class.java).apply {
            action = FileCopyService.ACTION_START
            putExtra(FileCopyService.KEY_OPERATION_ID, operationId)
            putExtra(FileCopyService.KEY_OPERATION_TYPE, type.name)
            putExtra(FileCopyService.KEY_SOURCES, sourcesJson.toString())
            if (destination != null) {
                val destObj = JSONObject()
                destObj.put(FileOperationIntentParser.KEY_PATH, destination.path)
                destObj.put(FileOperationIntentParser.KEY_ROOT_ID, destination.rootId)
                putExtra(FileCopyService.KEY_DESTINATION, destObj.toString())
            }
        }
        
        ContextCompat.startForegroundService(context, intent)
        return operationId
    }

    override fun enqueueResolvedOperation(
        type: BackgroundOperationType,
        resolvedItems: List<ResolvedTransferItem>
    ): String {
        val operationId = UUID.randomUUID().toString()
        val resolvedArray = JSONArray()
        resolvedItems.forEach { item ->
            val obj = JSONObject()
            obj.put(FileOperationIntentParser.KEY_PATH, item.source.path)
            obj.put(FileOperationIntentParser.KEY_ROOT_ID, item.source.rootId)
            obj.put(FileOperationIntentParser.KEY_DEST_DIR_PATH, item.destinationDir.path)
            obj.put(FileOperationIntentParser.KEY_DEST_DIR_ROOT_ID, item.destinationDir.rootId)
            obj.put(FileOperationIntentParser.KEY_TARGET_PATH, item.targetLocation.path)
            obj.put(FileOperationIntentParser.KEY_TARGET_ROOT_ID, item.targetLocation.rootId)
            obj.put(FileOperationIntentParser.KEY_ORIGINAL_NAME, item.originalName)
            obj.put(FileOperationIntentParser.KEY_TARGET_NAME, item.targetName)
            obj.put(FileOperationIntentParser.KEY_IS_DIRECTORY, item.isDirectory)
            obj.put(FileOperationIntentParser.KEY_CHOICE, item.choice.name)
            resolvedArray.put(obj)
        }

        val intent = Intent(context, FileCopyService::class.java).apply {
            action = FileCopyService.ACTION_START
            putExtra(FileCopyService.KEY_OPERATION_ID, operationId)
            putExtra(FileCopyService.KEY_OPERATION_TYPE, type.name)
            putExtra(FileCopyService.KEY_RESOLVED_ITEMS, resolvedArray.toString())
        }

        ContextCompat.startForegroundService(context, intent)
        return operationId
    }

    override fun cancelOperation() {
        val intent = Intent(context, FileCopyService::class.java).apply {
            action = FileCopyService.ACTION_CANCEL
        }
        context.startService(intent)
    }

    override fun observeProgress(): Flow<BackgroundOperationEvent> {
        return progressDispatcher.progressFlow
    }
}
