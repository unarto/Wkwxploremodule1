// [Jalur Class/Modul]: file-operations/src/main/kotlin/com/wakwau/xplore/fileoperations/move/MoveFilesUseCase.kt
// [Penjelasan]: UseCase operasi pemindahan berkas di modul file-operations dalam package khusus move sesuai ownership.md yang bergantung pada kontrak BackgroundOperationClient di domain :file-operations sesuai DIP.
package com.wakwau.xplore.fileoperations.move

import com.wakwau.xplore.fileoperations.conflict.ResolvedTransferItem
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.fileoperations.client.BackgroundOperationClient
import com.wakwau.xplore.core.storage.operation.BackgroundOperationType

class MoveFilesUseCase(private val backgroundOperationClient: BackgroundOperationClient) {
    fun invoke(sources: List<StorageLocation>, destinationDir: StorageLocation): String =
        backgroundOperationClient.enqueueOperation(BackgroundOperationType.MOVE, sources, destinationDir)

    fun invoke(resolvedItems: List<ResolvedTransferItem>): String =
        backgroundOperationClient.enqueueResolvedOperation(BackgroundOperationType.MOVE, resolvedItems)
}
