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
        syncBatch(collectSubtree(item))
    }

    suspend fun syncCopied(
        destinationDirectory: StorageLocation,
        targetLocation: StorageLocation,
        targetName: String
    ) {
        val destinationItem = findDestinationItem(destinationDirectory, targetLocation, targetName)
            ?: error("Copied destination was not found: ${targetLocation.path}")
        syncBatch(collectSubtree(destinationItem))
    }

    suspend fun syncMoved(
        source: StorageLocation,
        destinationDirectory: StorageLocation,
        targetLocation: StorageLocation,
        targetName: String
    ) {
        val destinationItem = findDestinationItem(destinationDirectory, targetLocation, targetName)
            ?: error("Moved destination was not found: ${targetLocation.path}")
        val destinationItems = collectSubtree(destinationItem)
        removeByPrefix(source.path)
        syncBatch(destinationItems)
    }

    suspend fun syncRenamed(oldLocation: StorageLocation, renamedItem: FileItem) {
        val renamedItems = collectSubtree(renamedItem)
        removeByPrefix(oldLocation.path)
        syncBatch(renamedItems)
    }

    private suspend fun collectSubtree(root: FileItem): List<FileItem> {
        if (root.type != FileType.DIRECTORY) return listOf(root)

        val descendants = when (val children = directoryRepository.list(root.location, showHidden = true)) {
            is FileOperationResult.Success -> children.data.flatMap { collectSubtree(it) }
            is FileOperationResult.Failure -> error("Unable to read indexed subtree: ${children.error}")
            FileOperationResult.Cancelled -> error("Index subtree traversal was cancelled")
            is FileOperationResult.Completed -> emptyList()
        }
        return listOf(root) + descendants
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
        FileOperationResult.Cancelled -> error("Destination index lookup was cancelled")
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
}
