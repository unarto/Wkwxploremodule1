// [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/repository/DirectoryRepositoryImpl.kt
// [Penjelasan]: Implementasi DirectoryRepository dengan delegasi bersih ke RootFileSystemContract, ShizukuFileSystemContract, SafFileSystemContract, dan LocalFileSystemContract berdasarkan StorageBackendClassifier serta otomatis memperbarui indeks Room DB tanpa menyamarkan error.
package com.wakwau.xplore.core.storage.repository

import com.wakwau.xplore.core.storage.mapper.toIndexItem
import com.wakwau.xplore.core.storage.api.error.StorageErrorMapper
import com.wakwau.xplore.core.storage.filesystem.LocalFileSystemContract
import com.wakwau.xplore.core.storage.filesystem.RootFileSystemContract
import com.wakwau.xplore.core.storage.filesystem.SafFileSystemContract
import com.wakwau.xplore.core.storage.filesystem.ShizukuFileSystemContract
import com.wakwau.xplore.core.storage.filesystem.StorageBackendClassifier
import com.wakwau.xplore.core.storage.filesystem.StorageBackendType
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import com.wakwau.xplore.core.storage.repository.FileIndexRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DirectoryRepositoryImpl(
    private val localFileSystem: LocalFileSystemContract,
    private val safFileSystem: SafFileSystemContract,
    private val safShizukuFileSystem: ShizukuFileSystemContract,
    private val rootFileSystem: RootFileSystemContract,
    private val backendClassifier: StorageBackendClassifier,
    private val storageErrorMapper: StorageErrorMapper,
    private val fileIndexRepository: FileIndexRepository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DirectoryRepository {

    override suspend fun list(location: StorageLocation, showHidden: Boolean): FileOperationResult<List<FileItem>> = withContext(ioDispatcher) {
        try {
            val fileItems = when (backendClassifier.classify(location)) {
                StorageBackendType.ROOT -> rootFileSystem.listFiles(location, showHidden = showHidden)
                StorageBackendType.SHIZUKU -> safShizukuFileSystem.listFiles(location, showHidden = showHidden)
                StorageBackendType.SAF -> safFileSystem.listFiles(location, showHidden = showHidden)
                StorageBackendType.LOCAL -> localFileSystem.listFiles(location, showHidden = showHidden)
            }
            FileOperationResult.Success(fileItems)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FileOperationResult.Failure(storageErrorMapper.map(e))
        }
    }

    override suspend fun create(location: StorageLocation, name: String): FileOperationResult<FileItem> = withContext(ioDispatcher) {
        try {
            val fileItem = when (backendClassifier.classify(location)) {
                StorageBackendType.ROOT -> rootFileSystem.createDirectory(location, name)
                StorageBackendType.SHIZUKU -> safShizukuFileSystem.createDirectory(location, name)
                StorageBackendType.SAF -> safFileSystem.createDirectory(location, name)
                StorageBackendType.LOCAL -> localFileSystem.createDirectory(location, name)
            }
            fileIndexRepository?.addOrUpdateIndex(fileItem.toIndexItem())
            FileOperationResult.Success(fileItem)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FileOperationResult.Failure(storageErrorMapper.map(e))
        }
    }
}


