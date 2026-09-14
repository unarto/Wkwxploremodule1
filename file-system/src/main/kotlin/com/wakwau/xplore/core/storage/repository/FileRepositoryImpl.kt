// [Modul: :file-system] [Jalur Class]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/repository/FileRepositoryImpl.kt
// [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API
package com.wakwau.xplore.core.storage.repository

import com.wakwau.xplore.core.storage.api.error.StorageErrorMapper
import com.wakwau.xplore.core.storage.filesystem.LocalFileSystemContract
import com.wakwau.xplore.core.storage.filesystem.RootFileSystemContract
import com.wakwau.xplore.core.storage.filesystem.SafFileSystemContract
import com.wakwau.xplore.core.storage.filesystem.ShizukuFileSystemContract
import com.wakwau.xplore.core.storage.filesystem.StorageBackendClassifier
import com.wakwau.xplore.core.storage.filesystem.StorageBackendType
import com.wakwau.xplore.core.storage.filesystem.bridge.CrossFilesystemTransferBridge
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.FileOperationProgress
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FileRepositoryImpl(
    private val localFileSystem: LocalFileSystemContract,
    private val safFileSystem: SafFileSystemContract,
    private val safShizukuFileSystem: ShizukuFileSystemContract,
    private val rootFileSystem: RootFileSystemContract,
    private val crossFilesystemTransferBridge: CrossFilesystemTransferBridge? = null,
    private val backendClassifier: StorageBackendClassifier,
    private val storageErrorMapper: StorageErrorMapper,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : FileRepository {

    // [Modul: :file-system] [Jalur Class]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/repository/FileRepositoryImpl.kt
    // [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API
    override suspend fun createDirectory(location: StorageLocation, name: String): FileOperationResult<FileItem> = withContext(ioDispatcher) {
        try {
            val fileItem = when (backendClassifier.classify(location)) {
                StorageBackendType.ROOT -> rootFileSystem.createDirectory(location, name)
                StorageBackendType.SHIZUKU -> safShizukuFileSystem.createDirectory(location, name)
                StorageBackendType.SAF -> safFileSystem.createDirectory(location, name)
                StorageBackendType.LOCAL -> localFileSystem.createDirectory(location, name)
            }
            FileOperationResult.Success(fileItem)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FileOperationResult.Failure(storageErrorMapper.map(e))
        }
    }

    override suspend fun delete(location: StorageLocation): FileOperationResult<Unit> = withContext(ioDispatcher) {
        try {
            when (backendClassifier.classify(location)) {
                StorageBackendType.ROOT -> rootFileSystem.delete(location)
                StorageBackendType.SAF -> safFileSystem.delete(location)
                StorageBackendType.SHIZUKU -> safShizukuFileSystem.delete(location)
                StorageBackendType.LOCAL -> localFileSystem.delete(location)
            }
            FileOperationResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FileOperationResult.Failure(storageErrorMapper.map(e))
        }
    }

    override suspend fun rename(location: StorageLocation, newName: String): FileOperationResult<FileItem> = withContext(ioDispatcher) {
        try {
            val fileItem = when (backendClassifier.classify(location)) {
                StorageBackendType.ROOT -> rootFileSystem.rename(location, newName)
                StorageBackendType.SAF -> safFileSystem.rename(location, newName)
                StorageBackendType.SHIZUKU -> safShizukuFileSystem.rename(location, newName)
                StorageBackendType.LOCAL -> localFileSystem.rename(location, newName)
            }
            FileOperationResult.Success(fileItem)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FileOperationResult.Failure(storageErrorMapper.map(e))
        }
    }

    override fun copy(source: StorageLocation, destination: StorageLocation): Flow<FileOperationResult<FileOperationProgress>> =
        flow {
            val sourceType = backendClassifier.classify(source)
            val destType = backendClassifier.classify(destination)

            val progressFlow = if (sourceType == destType) {
                when (sourceType) {
                    StorageBackendType.LOCAL -> localFileSystem.copy(source, destination)
                    StorageBackendType.SAF -> safFileSystem.copy(source, destination)
                    StorageBackendType.SHIZUKU -> safShizukuFileSystem.copy(source, destination)
                    StorageBackendType.ROOT -> rootFileSystem.copy(source, destination)
                }
            } else {
                val bridge = crossFilesystemTransferBridge ?: throw IllegalStateException("Cross-filesystem transfer bridge is not available")
                bridge.copyCross(source, destination, sourceType, destType)
            }

            emitAll(progressFlow.map<FileOperationProgress, FileOperationResult<FileOperationProgress>> { progress ->
                FileOperationResult.Success(progress)
            })
        }
            .catch { error ->
                when (error) {
                    is CancellationException -> throw error
                    is Exception -> emit(FileOperationResult.Failure(storageErrorMapper.map(error)))
                    else -> throw error
                }
            }
            .flowOn(ioDispatcher)

    override fun move(source: StorageLocation, destination: StorageLocation): Flow<FileOperationResult<FileOperationProgress>> =
        flow {
            val sourceType = backendClassifier.classify(source)
            val destType = backendClassifier.classify(destination)

            val progressFlow = if (sourceType == destType) {
                when (sourceType) {
                    StorageBackendType.LOCAL -> localFileSystem.move(source, destination)
                    StorageBackendType.SAF -> safFileSystem.move(source, destination)
                    StorageBackendType.SHIZUKU -> safShizukuFileSystem.move(source, destination)
                    StorageBackendType.ROOT -> rootFileSystem.move(source, destination)
                }
            } else {
                val bridge = crossFilesystemTransferBridge ?: throw IllegalStateException("Cross-filesystem transfer bridge is not available")
                bridge.moveCross(source, destination, sourceType, destType)
            }

            emitAll(progressFlow.map<FileOperationProgress, FileOperationResult<FileOperationProgress>> { progress ->
                FileOperationResult.Success(progress)
            })
        }
            .catch { error ->
                when (error) {
                    is CancellationException -> throw error
                    is Exception -> emit(FileOperationResult.Failure(storageErrorMapper.map(error)))
                    else -> throw error
                }
            }
            .flowOn(ioDispatcher)

}
