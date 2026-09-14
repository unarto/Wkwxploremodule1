// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/repository/FileIndexRepository.kt
// [Penjelasan]: Antarmuka kontrak repository indeks berkas untuk abstraksi persistensi pencarian cepat dan sinkronisasi berkas tanpa ketergantungan framework persistensi.
package com.wakwau.xplore.core.storage.repository

import com.wakwau.xplore.core.storage.model.FileIndexItem
import kotlinx.coroutines.flow.Flow

interface FileIndexRepository {
    fun searchFiles(
        locationPrefix: String,
        keyword: String,
        minSize: Long?,
        maxSize: Long?,
        extension: String?
    ): Flow<List<FileIndexItem>>

    fun getFilesByCategory(category: String): Flow<List<FileIndexItem>>

    suspend fun addOrUpdateIndex(item: FileIndexItem)

    suspend fun addOrUpdateIndexBatch(items: List<FileIndexItem>)

    suspend fun removeIndex(filePath: String)

    suspend fun removeIndexBatch(filePaths: List<String>)

    suspend fun removeIndexByPrefix(locationPrefix: String)

    suspend fun removeIndexByPrefixes(locationPrefixes: List<String>)

    suspend fun replacePrefixIndex(locationPrefix: String, items: List<FileIndexItem>)

    suspend fun replacePrefixIndexBatched(
        locationPrefix: String,
        batches: Flow<List<FileIndexItem>>
    )

    suspend fun syncRename(oldPath: String, newItem: FileIndexItem)

    suspend fun syncMove(sourcePath: String, destinationItem: FileIndexItem)

    suspend fun clearIndex()
}
