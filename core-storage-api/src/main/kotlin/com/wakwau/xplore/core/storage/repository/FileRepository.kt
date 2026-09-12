// [Modul: :core-storage-api] [Jalur Class]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/repository/FileRepository.kt
// [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API
package com.wakwau.xplore.core.storage.repository

import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.FileOperationProgress
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import kotlinx.coroutines.flow.Flow

interface FileRepository {
    // [Modul: :core-storage-api] [Jalur Class]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/repository/FileRepository.kt
    // [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API
    suspend fun createDirectory(location: StorageLocation, name: String): FileOperationResult<FileItem>
    suspend fun delete(location: StorageLocation): FileOperationResult<Unit>
    suspend fun rename(location: StorageLocation, newName: String): FileOperationResult<FileItem>
    fun copy(source: StorageLocation, destination: StorageLocation): Flow<FileOperationResult<FileOperationProgress>>
    fun move(source: StorageLocation, destination: StorageLocation): Flow<FileOperationResult<FileOperationProgress>>
}
