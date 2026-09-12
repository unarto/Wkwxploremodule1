// [Jalur Class/Modul]: file-operations-bridge/src/main/kotlin/com/wakwau/xplore/fileoperations/bridge/FileOperationBridgeAdapter.kt
// [Penjelasan]: Implementasi bridge adapter yang mendelegasikan perintah intent ke BackgroundOperationClient tanpa business logic atau UI.
package com.wakwau.xplore.fileoperations.bridge

import com.wakwau.xplore.core.storage.operation.BackgroundOperationType
import com.wakwau.xplore.fileoperations.client.BackgroundOperationClient
import com.wakwau.xplore.fileoperations.conflict.ResolvedTransferItem

import com.wakwau.xplore.core.storage.operation.FileOperationResult
import com.wakwau.xplore.core.storage.operation.FileOperationProgress
import kotlinx.coroutines.flow.Flow

class FileOperationBridgeAdapter(
    private val client: BackgroundOperationClient
) : FileOperationBridge {

    override fun startResolvedOperation(
        type: BackgroundOperationType,
        resolvedItems: List<ResolvedTransferItem>
    ) {
        client.enqueueResolvedOperation(type, resolvedItems)
    }

    override fun cancelOperation() {
        client.cancelOperation()
    }

    // [CopyFix]: Wiring bridge & sync ViewModel progress completion berdasarkan copy.md
    override fun observeProgress(): Flow<FileOperationResult<FileOperationProgress>> {
        return client.observeProgress()
    }
}
