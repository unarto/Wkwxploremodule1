// [Jalur Class/Modul]: file-operations-bridge/src/main/kotlin/com/wakwau/xplore/fileoperations/bridge/FileOperationBridge.kt
// [Penjelasan]: Kontrak boundary bridge antara caller/presenter dan background operation executor tanpa business logic atau UI.
package com.wakwau.xplore.fileoperations.bridge

import com.wakwau.xplore.core.storage.operation.BackgroundOperationType
import com.wakwau.xplore.fileoperations.conflict.ResolvedTransferItem

import com.wakwau.xplore.core.storage.operation.FileOperationResult
import com.wakwau.xplore.core.storage.operation.FileOperationProgress
import kotlinx.coroutines.flow.Flow

interface FileOperationBridge {
    fun startResolvedOperation(type: BackgroundOperationType, resolvedItems: List<ResolvedTransferItem>)
    fun cancelOperation()
    fun observeProgress(): Flow<FileOperationResult<FileOperationProgress>>
}
