// [Jalur Class/Modul]: core-storage/src/main/kotlin/com/wakwau/xplore/core/storage/db/repository/FileIndexRepositoryImpl.kt
// [Penjelasan]: Implementasi FileIndexRepository yang mengeksekusi query pencarian dan transaksi indeks berkas via FileIndexDao dengan pemetaan model domain FileIndexItem dan dukungan atomisitas Room.
package com.wakwau.xplore.core.storage.db.repository

import com.wakwau.xplore.core.storage.db.dao.FileIndexDao
import com.wakwau.xplore.core.storage.db.mapper.toDomain
import com.wakwau.xplore.core.storage.db.mapper.toEntity
import com.wakwau.xplore.core.storage.model.FileIndexItem
import com.wakwau.xplore.core.storage.repository.FileIndexRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FileIndexRepositoryImpl(
    private val fileIndexDao: FileIndexDao
) : FileIndexRepository {

    override fun searchFiles(
        locationPrefix: String,
        keyword: String,
        minSize: Long?,
        maxSize: Long?,
        extension: String?
    ): Flow<List<FileIndexItem>> {
        return fileIndexDao.searchFiles(
            locationPrefix = locationPrefix,
            keyword = keyword,
            minSize = minSize,
            maxSize = maxSize,
            extension = extension
        ).map { list -> list.map { it.toDomain() } }
    }

    override fun getFilesByCategory(category: String): Flow<List<FileIndexItem>> {
        return fileIndexDao.getFilesByCategory(category).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun addOrUpdateIndex(item: FileIndexItem) {
        fileIndexDao.insertOrUpdate(item.toEntity())
    }

    override suspend fun addOrUpdateIndexBatch(items: List<FileIndexItem>) {
        fileIndexDao.insertOrUpdateBatch(items.map { it.toEntity() })
    }

    override suspend fun removeIndex(filePath: String) {
        fileIndexDao.deletePathAndChildren(filePath)
    }

    override suspend fun removeIndexBatch(filePaths: List<String>) {
        fileIndexDao.deleteBatch(filePaths)
    }

    override suspend fun removeIndexByPrefix(locationPrefix: String) {
        fileIndexDao.deleteByPrefix(locationPrefix)
    }

    override suspend fun removeIndexByPrefixes(locationPrefixes: List<String>) {
        fileIndexDao.deleteByPrefixes(locationPrefixes)
    }

    override suspend fun replacePrefixIndex(locationPrefix: String, items: List<FileIndexItem>) {
        fileIndexDao.replacePrefixIndex(locationPrefix, items.map { it.toEntity() })
    }

    override suspend fun replacePrefixIndexBatched(
        locationPrefix: String,
        batches: Flow<List<FileIndexItem>>
    ) {
        fileIndexDao.replacePrefixIndexBatched(
            locationPrefix,
            batches.map { batch -> batch.map { it.toEntity() } }
        )
    }

    override suspend fun syncRename(oldPath: String, newItem: FileIndexItem) {
        fileIndexDao.syncRenameAtomic(oldPath, newItem.toEntity())
    }

    override suspend fun syncMove(sourcePath: String, destinationItem: FileIndexItem) {
        fileIndexDao.syncMoveAtomic(sourcePath, destinationItem.toEntity())
    }

    override suspend fun clearIndex() {
        fileIndexDao.clearAll()
    }
}
