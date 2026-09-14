// [Jalur Class/Modul]: file-operations/src/main/kotlin/com/wakwau/xplore/fileoperations/client/BackgroundOperationClient.kt
// [Penjelasan]: Kontrak antarmuka operasi latar belakang di domain :file-operations yang mendefinisikan abstraksi eksekusi mutasi (Copy, Move, Delete, Cancel) sesuai Dependency Inversion Principle (DIP).
package com.wakwau.xplore.fileoperations.client

import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.BackgroundOperationType
import com.wakwau.xplore.core.storage.operation.BackgroundOperationEvent
import com.wakwau.xplore.fileoperations.conflict.ResolvedTransferItem
import kotlinx.coroutines.flow.Flow

interface BackgroundOperationClient {
    fun enqueueOperation(type: BackgroundOperationType, sources: List<StorageLocation>, destination: StorageLocation? = null): String
    fun enqueueResolvedOperation(type: BackgroundOperationType, resolvedItems: List<ResolvedTransferItem>): String
    fun cancelOperation()
    fun observeProgress(): Flow<BackgroundOperationEvent>
}
