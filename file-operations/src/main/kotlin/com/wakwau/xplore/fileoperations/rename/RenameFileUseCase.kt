// [Jalur Class/Modul]: file-operations/src/main/kotlin/com/wakwau/xplore/fileoperations/rename/RenameFileUseCase.kt
// [Penjelasan]: UseCase operasi penggantian nama berkas di modul file-operations dalam package khusus rename sesuai ownership.md.
package com.wakwau.xplore.fileoperations.rename

import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import com.wakwau.xplore.core.storage.repository.FileRepository

class RenameFileUseCase(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(location: StorageLocation, newName: String): FileOperationResult<FileItem> {
        return fileRepository.rename(location, newName)
    }
}
