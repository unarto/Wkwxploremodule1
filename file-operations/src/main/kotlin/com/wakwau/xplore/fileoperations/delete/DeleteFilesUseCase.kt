// [Jalur Class/Modul]: file-operations/src/main/kotlin/com/wakwau/xplore/fileoperations/delete/DeleteFilesUseCase.kt
// [Penjelasan]: UseCase operasi penghapusan berkas di modul file-operations dalam package khusus delete sesuai ownership.md yang bergantung pada kontrak BackgroundOperationClient di domain :file-operations sesuai DIP.
package com.wakwau.xplore.fileoperations.delete

import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.fileoperations.client.BackgroundOperationClient
import com.wakwau.xplore.core.storage.operation.BackgroundOperationType

class DeleteFilesUseCase(private val backgroundOperationClient: BackgroundOperationClient) {
    fun invoke(sources: List<StorageLocation>): String =
        backgroundOperationClient.enqueueOperation(
            BackgroundOperationType.DELETE,
            sources
        )
}
