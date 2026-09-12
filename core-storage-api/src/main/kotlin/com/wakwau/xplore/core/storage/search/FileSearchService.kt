// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/search/FileSearchService.kt
// [Penjelasan]: Antarmuka kontrak service pencarian berkas dan manajemen indeks lokal.
package com.wakwau.xplore.core.storage.search

import com.wakwau.xplore.core.storage.model.FileItem
import kotlinx.coroutines.flow.Flow

interface FileSearchService {
    fun search(query: FileSearchQuery): Flow<List<FileItem>>
    suspend fun removeIndexByPrefix(locationPrefix: String)
    suspend fun addOrUpdateIndexBatch(items: List<FileItem>)
}
