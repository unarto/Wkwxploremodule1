// [Jalur Class/Modul]: file-operations/src/main/kotlin/com/wakwau/xplore/fileoperations/copy/CopyFilesUseCase.kt
// [Penjelasan]: UseCase operasi penyalinan berkas di modul file-operations dalam package khusus copy sesuai ownership.md yang bergantung pada kontrak BackgroundOperationClient di domain :file-operations sesuai DIP.
package com.wakwau.xplore.fileoperations.copy

import com.wakwau.xplore.fileoperations.conflict.ResolvedTransferItem
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.fileoperations.client.BackgroundOperationClient
import com.wakwau.xplore.core.storage.operation.BackgroundOperationType

class CopyFilesUseCase(private val backgroundOperationClient: BackgroundOperationClient) {
    fun invoke(sources: List<StorageLocation>, destinationDir: StorageLocation): String =
        backgroundOperationClient.enqueueOperation(BackgroundOperationType.COPY, sources, destinationDir)

    fun invoke(resolvedItems: List<ResolvedTransferItem>): String =
        backgroundOperationClient.enqueueResolvedOperation(BackgroundOperationType.COPY, resolvedItems)
}
