// [Jalur Class/Modul]: search/src/main/kotlin/com/wakwau/xplore/search/sync/FileIndexSynchronizer.kt
// [Penjelasan]: Komponen sinkronisasi data index repository dengan dukungan batching bulk operation dan transaksi atomik untuk mencegah I/O thrashing saat mass operations.
package com.wakwau.xplore.search.sync

import com.wakwau.xplore.core.storage.model.FileIndexItem
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.FileType
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import com.wakwau.xplore.core.storage.repository.DirectoryRepository
import com.wakwau.xplore.core.storage.repository.FileIndexRepository
import com.wakwau.xplore.core.utils.mime.MimeTypeDetector
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

class FileIndexSynchronizer(
    private val fileIndexRepository: FileIndexRepository,
    private val directoryRepository: DirectoryRepository
) {

    suspend fun syncBatch(items: List<FileItem>) {
        if (items.isEmpty()) return
        val indexBatch = items.map { createIndexItem(it) }
        fileIndexRepository.addOrUpdateIndexBatch(indexBatch)
    }

    suspend fun removeByPrefix(prefix: String) {
        fileIndexRepository.removeIndexByPrefix(prefix)
    }

    suspend fun syncCreated(item: FileItem) {
        syncSubtreeBatches(item)
    }

    suspend fun syncCopied(
        destinationDirectory: StorageLocation,
        targetLocation: StorageLocation,
        targetName: String
    ) {
        val destinationItem = findDestinationItem(destinationDirectory, targetLocation, targetName)
            ?: error("Copied destination was not found: ${targetLocation.path}")
        syncSubtreeBatches(destinationItem)
    }

    suspend fun syncMoved(
        source: StorageLocation,
        destinationDirectory: StorageLocation,
        targetLocation: StorageLocation,
        targetName: String
    ) {
        val destinationItem = findDestinationItem(destinationDirectory, targetLocation, targetName)
            ?: error("Moved destination was not found: ${targetLocation.path}")
        currentCoroutineContext().ensureActive()
        fileIndexRepository.replacePrefixIndexBatched(
            source.path,
            indexSubtreeBatches(destinationItem)
        )
    }

    suspend fun syncRenamed(oldLocation: StorageLocation, renamedItem: FileItem) {
        currentCoroutineContext().ensureActive()
        fileIndexRepository.replacePrefixIndexBatched(
            oldLocation.path,
            indexSubtreeBatches(renamedItem)
        )
    }

    private suspend fun syncSubtreeBatches(root: FileItem) {
        indexSubtreeBatches(root).collect { batch ->
            currentCoroutineContext().ensureActive()
            fileIndexRepository.addOrUpdateIndexBatch(batch)
        }
    }

    private fun indexSubtreeBatches(root: FileItem): Flow<List<FileIndexItem>> = flow {
        val stack = ArrayDeque<FileItem>()
        val batch = ArrayList<FileIndexItem>(INDEX_BATCH_SIZE)
        stack.addLast(root)

        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val item = stack.removeLast()
            batch += createIndexItem(item)

            if (item.type == FileType.DIRECTORY) {
                val children = when (val result = directoryRepository.list(item.location, showHidden = true)) {
                    is FileOperationResult.Success -> result.data
                    is FileOperationResult.Failure -> error("Unable to read indexed subtree: ${result.error}")
                    FileOperationResult.Cancelled -> throw CancellationException("Index subtree traversal was cancelled")
                    is FileOperationResult.Completed -> error("Unexpected completion while reading indexed subtree")
                }
                for (index in children.indices.reversed()) {
                    stack.addLast(children[index])
                }
            }

            if (batch.size == INDEX_BATCH_SIZE) {
                emit(batch.toList())
                batch.clear()
            }
        }

        if (batch.isNotEmpty()) emit(batch.toList())
    }

    private suspend fun findDestinationItem(
        destinationDirectory: StorageLocation,
        targetLocation: StorageLocation,
        targetName: String
    ): FileItem? = when (val result = directoryRepository.list(destinationDirectory, showHidden = true)) {
        is FileOperationResult.Success -> result.data.firstOrNull { item ->
            item.location == targetLocation || item.name == targetName
        }
        is FileOperationResult.Failure -> error("Unable to read copied destination: ${result.error}")
        FileOperationResult.Cancelled -> throw CancellationException("Destination index lookup was cancelled")
        is FileOperationResult.Completed -> null
    }

    private fun createIndexItem(item: FileItem): FileIndexItem {
        val isDir = item.type == FileType.DIRECTORY
        val extension = item.name.substringAfterLast('.', "").lowercase(Locale.getDefault())
        val category = MimeTypeDetector.getCategory(item.name, isDir).name

        return FileIndexItem(
            filePath = item.location.path,
            fileName = item.name,
            size = item.metadata.size,
            extension = extension,
            category = category,
            dateModified = item.metadata.modifiedTime,
            isDirectory = isDir
        )
    }

    private companion object {
        const val INDEX_BATCH_SIZE = 500
    }
}
