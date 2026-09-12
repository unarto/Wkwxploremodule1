// [Modul: :filemanager] [Jalur Class]: filemanager/src/main/kotlin/com/wakwau/xplore/filemanager/usecase/CreateDirectoryUseCase.kt
// [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API
package com.wakwau.xplore.filemanager.usecase

import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.FileOperationProgress
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import com.wakwau.xplore.core.storage.repository.DirectoryRepository
import com.wakwau.xplore.core.storage.repository.FileRepository
import kotlinx.coroutines.flow.Flow

class CreateDirectoryUseCase(private val fileRepository: FileRepository) {

    // [Modul: :filemanager] [Jalur Class]: filemanager/src/main/kotlin/com/wakwau/xplore/filemanager/usecase/CreateDirectoryUseCase.kt
    // [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API (konstruktor sekunder kompatibilitas)
    constructor(directoryRepository: DirectoryRepository) : this(
        object : FileRepository {
            override suspend fun createDirectory(location: StorageLocation, name: String): FileOperationResult<FileItem> =
                directoryRepository.create(location, name)
            override suspend fun delete(location: StorageLocation): FileOperationResult<Unit> =
                throw UnsupportedOperationException()
            override suspend fun rename(location: StorageLocation, newName: String): FileOperationResult<FileItem> =
                throw UnsupportedOperationException()
            override fun copy(source: StorageLocation, destination: StorageLocation): Flow<FileOperationResult<FileOperationProgress>> =
                throw UnsupportedOperationException()
            override fun move(source: StorageLocation, destination: StorageLocation): Flow<FileOperationResult<FileOperationProgress>> =
                throw UnsupportedOperationException()
        }
    )

    suspend operator fun invoke(location: StorageLocation, name: String): FileOperationResult<FileItem> {
        return fileRepository.createDirectory(location, name)
    }
}
