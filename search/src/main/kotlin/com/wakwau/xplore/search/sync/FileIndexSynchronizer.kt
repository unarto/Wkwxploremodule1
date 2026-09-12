// [Jalur Class/Modul]: search/src/main/kotlin/com/wakwau/xplore/search/sync/FileIndexSynchronizer.kt
// [Penjelasan]: Komponen sinkronisasi data index repository dengan dukungan batching bulk operation dan transaksi atomik untuk mencegah I/O thrashing saat mass operations.
package com.wakwau.xplore.search.sync

import com.wakwau.xplore.core.storage.model.FileIndexItem
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.FileType
import com.wakwau.xplore.core.storage.repository.FileIndexRepository
import com.wakwau.xplore.core.utils.mime.MimeTypeDetector
import java.util.Locale

class FileIndexSynchronizer(
    private val fileIndexRepository: FileIndexRepository
) {

    suspend fun syncBatch(items: List<FileItem>) {
        if (items.isEmpty()) return
        val indexBatch = items.map { createIndexItem(it) }
        fileIndexRepository.addOrUpdateIndexBatch(indexBatch)
    }

    suspend fun syncSingle(item: FileItem) {
        fileIndexRepository.addOrUpdateIndex(createIndexItem(item))
    }

    suspend fun removeSingle(filePath: String) {
        fileIndexRepository.removeIndex(filePath)
    }

    suspend fun removeBatch(filePaths: List<String>) {
        if (filePaths.isEmpty()) return
        fileIndexRepository.removeIndexBatch(filePaths)
    }

    suspend fun removeByPrefix(prefix: String) {
        fileIndexRepository.removeIndexByPrefix(prefix)
    }

    suspend fun removeByPrefixes(prefixes: List<String>) {
        if (prefixes.isEmpty()) return
        fileIndexRepository.removeIndexByPrefixes(prefixes)
    }

    suspend fun syncRename(oldPath: String, newItem: FileItem) {
        fileIndexRepository.syncRename(oldPath, createIndexItem(newItem))
    }

    suspend fun syncMove(sourcePath: String, destinationItem: FileItem) {
        fileIndexRepository.syncMove(sourcePath, createIndexItem(destinationItem))
    }

    suspend fun replacePrefixIndex(prefix: String, items: List<FileItem>) {
        val indexItems = items.map { createIndexItem(it) }
        fileIndexRepository.replacePrefixIndex(prefix, indexItems)
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
