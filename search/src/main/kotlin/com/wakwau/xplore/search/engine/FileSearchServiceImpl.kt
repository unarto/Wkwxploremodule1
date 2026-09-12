// [Jalur Class/Modul]: search/src/main/kotlin/com/wakwau/xplore/search/engine/FileSearchServiceImpl.kt
// [Penjelasan]: Implementasi domain FileSearchService terkoordinasi yang mendelegasikan traversal ke FileSystemSearchTraversal dan sinkronisasi indeks ke FileIndexSynchronizer tanpa unconfined background scope.
package com.wakwau.xplore.search.engine

import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.search.FileSearchQuery
import com.wakwau.xplore.core.storage.search.FileSearchService
import com.wakwau.xplore.search.sync.FileIndexSynchronizer
import com.wakwau.xplore.search.traversal.FileSystemSearchTraversal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

class FileSearchServiceImpl(
    private val traversal: FileSystemSearchTraversal,
    private val indexSynchronizer: FileIndexSynchronizer? = null,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO
) : FileSearchService {

    override fun search(query: FileSearchQuery): Flow<List<FileItem>> {
        return traversal.traverse(query) { items ->
            indexSynchronizer?.syncBatch(items)
        }.flowOn(defaultDispatcher)
    }

    override suspend fun removeIndexByPrefix(locationPrefix: String) {
        indexSynchronizer?.removeByPrefix(locationPrefix)
    }

    override suspend fun addOrUpdateIndexBatch(items: List<FileItem>) {
        indexSynchronizer?.syncBatch(items)
    }
}
