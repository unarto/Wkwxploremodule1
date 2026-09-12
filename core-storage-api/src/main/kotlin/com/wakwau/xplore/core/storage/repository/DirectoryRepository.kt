// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/repository/DirectoryRepository.kt
// [Penjelasan]: Antarmuka kontrak repository untuk operasi direktori (listing berkas dan pembuatan folder).
package com.wakwau.xplore.core.storage.repository

import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.FileOperationResult

interface DirectoryRepository {
    suspend fun list(location: StorageLocation, showHidden: Boolean): FileOperationResult<List<FileItem>>
    suspend fun create(location: StorageLocation, name: String): FileOperationResult<FileItem>
}
