// [Jalur Class/Modul]: core-storage/src/main/kotlin/com/wakwau/xplore/core/storage/db/dao/FileIndexDao.kt
// [Penjelasan]: Data Access Object (DAO) untuk query Room tabel file_index dengan dukungan sanitasi escape wildcard SQL, batch query, dan transaksi atomik.
package com.wakwau.xplore.core.storage.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.wakwau.xplore.core.storage.db.entity.FileIndexEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

@Dao
interface FileIndexDao {
    @Query("""
        SELECT * FROM file_index 
        WHERE filePath LIKE :locationPrefix || '%' ESCAPE '\'
        AND LOWER(fileName) LIKE '%' || LOWER(:keyword) || '%' ESCAPE '\'
        AND (:minSize IS NULL OR size >= :minSize)
        AND (:maxSize IS NULL OR size <= :maxSize)
        AND (:extension IS NULL OR LOWER(extension) = LOWER(:extension))
        ORDER BY dateModified DESC
    """)
    fun searchFilesInternal(
        locationPrefix: String,
        keyword: String,
        minSize: Long?,
        maxSize: Long?,
        extension: String?
    ): Flow<List<FileIndexEntity>>

    fun searchFiles(
        locationPrefix: String,
        keyword: String,
        minSize: Long?,
        maxSize: Long?,
        extension: String?
    ): Flow<List<FileIndexEntity>> {
        return searchFilesInternal(
            locationPrefix = escapeSqlLikeWildcards(locationPrefix),
            keyword = escapeSqlLikeWildcards(keyword),
            minSize = minSize,
            maxSize = maxSize,
            extension = extension
        )
    }

    @Query("SELECT * FROM file_index WHERE category = :category ORDER BY dateModified DESC")
    fun getFilesByCategory(category: String): Flow<List<FileIndexEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: FileIndexEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(entities: List<FileIndexEntity>)

    @Query("DELETE FROM file_index WHERE filePath = :filePath")
    suspend fun deleteByPath(filePath: String)

    @Query("DELETE FROM file_index WHERE filePath IN (:filePaths)")
    suspend fun deleteByPaths(filePaths: List<String>)

    @Query("DELETE FROM file_index WHERE filePath LIKE :locationPrefix || '%' ESCAPE '\\'")
    suspend fun deleteByPrefixInternal(locationPrefix: String)

    suspend fun deleteByPrefix(locationPrefix: String) {
        deleteByPrefixInternal(escapeSqlLikeWildcards(locationPrefix))
    }

    @Query("DELETE FROM file_index")
    suspend fun clearAll()

    @Transaction
    suspend fun deletePathAndChildren(filePath: String) {
        deleteByPath(filePath)
        val prefixWithSlash = if (filePath.endsWith("/")) filePath else "$filePath/"
        deleteByPrefix(prefixWithSlash)
    }

    @Transaction
    suspend fun insertOrUpdateBatch(entities: List<FileIndexEntity>) {
        if (entities.isEmpty()) return
        entities.chunked(500).forEach { chunk ->
            insertOrUpdateAll(chunk)
        }
    }

    @Transaction
    suspend fun deleteBatch(filePaths: List<String>) {
        if (filePaths.isEmpty()) return
        filePaths.chunked(500).forEach { chunk ->
            deleteByPaths(chunk)
        }
    }

    @Transaction
    suspend fun deleteByPrefixes(prefixes: List<String>) {
        if (prefixes.isEmpty()) return
        prefixes.forEach { prefix ->
            deleteByPrefix(prefix)
            val prefixWithSlash = if (prefix.endsWith("/")) prefix else "$prefix/"
            deleteByPrefix(prefixWithSlash)
        }
    }

    @Transaction
    suspend fun replacePrefixIndex(locationPrefix: String, entities: List<FileIndexEntity>) {
        deleteByPrefix(locationPrefix)
        val prefixWithSlash = if (locationPrefix.endsWith("/")) locationPrefix else "$locationPrefix/"
        deleteByPrefix(prefixWithSlash)
        if (entities.isNotEmpty()) {
            insertOrUpdateBatch(entities)
        }
    }

    @Transaction
    suspend fun replacePrefixIndexBatched(
        locationPrefix: String,
        batches: Flow<List<FileIndexEntity>>
    ) {
        deleteByPrefix(locationPrefix)
        val prefixWithSlash = if (locationPrefix.endsWith("/")) locationPrefix else "$locationPrefix/"
        deleteByPrefix(prefixWithSlash)
        batches.collect { batch ->
            insertOrUpdateBatch(batch)
        }
    }

    @Transaction
    suspend fun syncRenameAtomic(oldPath: String, newEntity: FileIndexEntity) {
        deletePathAndChildren(oldPath)
        insertOrUpdate(newEntity)
    }

    @Transaction
    suspend fun syncMoveAtomic(sourcePath: String, destinationEntity: FileIndexEntity) {
        deletePathAndChildren(sourcePath)
        insertOrUpdate(destinationEntity)
    }
}

internal fun escapeSqlLikeWildcards(input: String): String {
    if (input.isEmpty()) return input
    return input
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
}
