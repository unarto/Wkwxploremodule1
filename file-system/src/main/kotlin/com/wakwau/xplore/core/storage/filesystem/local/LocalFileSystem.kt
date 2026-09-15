// [Modul: :file-system] [Jalur Class]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/filesystem/local/LocalFileSystem.kt
// [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API
package com.wakwau.xplore.core.storage.filesystem.local

import kotlinx.coroutines.CancellationException
import com.wakwau.xplore.core.storage.constant.StorageConstants
import com.wakwau.xplore.core.storage.filesystem.LocalFileSystemContract
import com.wakwau.xplore.core.storage.mapper.FileItemMapper
import com.wakwau.xplore.core.storage.metadata.FileMetadataReader
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.FileType
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.FileOperationProgress
import com.wakwau.xplore.core.storage.permission.StoragePermissionChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class LocalFileSystem(
    private val fileMetadataReader: FileMetadataReader = FileMetadataReader(),
    private val fileItemMapper: FileItemMapper = FileItemMapper(),
    private val streamTransferHelper: LocalStreamTransferHelper = LocalStreamTransferHelper(),
    private val directoryOperationHelper: LocalDirectoryOperationHelper = LocalDirectoryOperationHelper(streamTransferHelper)
) : LocalFileSystemContract {

    constructor(
        fileMetadataReader: FileMetadataReader = FileMetadataReader(),
        fileItemMapper: FileItemMapper = FileItemMapper(),
        streamTransferHelper: LocalStreamTransferHelper = LocalStreamTransferHelper(),
        storagePermissionChecker: StoragePermissionChecker?
    ) : this(
        fileMetadataReader = fileMetadataReader,
        fileItemMapper = fileItemMapper,
        streamTransferHelper = streamTransferHelper,
        directoryOperationHelper = LocalDirectoryOperationHelper(streamTransferHelper, storagePermissionChecker)
    )

    override suspend fun listFiles(location: StorageLocation, showHidden: Boolean): List<FileItem> =
        listFiles(path = location.path, showHidden = showHidden, rootId = location.rootId)

    override suspend fun createDirectory(location: StorageLocation, name: String): FileItem =
        createDirectory(parentPath = location.path, name = name, rootId = location.rootId)

    override suspend fun delete(location: StorageLocation) = delete(path = location.path)

    override suspend fun rename(location: StorageLocation, newName: String): FileItem =
        rename(path = location.path, newName = newName, rootId = location.rootId)

    override fun copy(source: StorageLocation, destination: StorageLocation): Flow<FileOperationProgress> =
        copy(sourcePath = source.path, destPath = destination.path)

    override fun move(source: StorageLocation, destination: StorageLocation): Flow<FileOperationProgress> =
        move(sourcePath = source.path, destPath = destination.path)

    override suspend fun getFileItem(location: StorageLocation): FileItem? =
        getFileItem(path = location.path, rootId = location.rootId)

    override suspend fun exists(location: StorageLocation): Boolean = exists(path = location.path)

    fun listFiles(
        path: String,
        showHidden: Boolean = true,
        rootId: String = StorageConstants.PRIMARY_INTERNAL_VOLUME_ID
    ): List<FileItem> {
        val directory = File(path)
        if (!directory.exists() || !directory.isDirectory) {
            throw FileNotFoundException("Directory not found or is not a directory: $path")
        }

        var files = directory.listFiles()?.toList()
            ?: throw IOException("Failed to list directory: $path")

        if (!showHidden) {
            files = files.filter { !it.isHidden && !it.name.startsWith(".") }
        }

        val sortedFiles = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        return sortedFiles.map { file ->
            val metadata = fileMetadataReader.readMetadata(file)
            val type = if (file.isDirectory) FileType.DIRECTORY else FileType.FILE
            val itemLocation = StorageLocation(path = file.absolutePath, rootId = rootId)
            fileItemMapper.map(
                id = file.absolutePath,
                name = file.name,
                location = itemLocation,
                type = type,
                metadata = metadata
            )
        }
    }

    fun createDirectory(
        parentPath: String,
        name: String,
        rootId: String = StorageConstants.PRIMARY_INTERNAL_VOLUME_ID
    ): FileItem {
        val dir = directoryOperationHelper.createDirectory(parentPath, name)
        val metadata = fileMetadataReader.readMetadata(dir)
        val newLocation = StorageLocation(path = dir.absolutePath, rootId = rootId)
        return fileItemMapper.map(
            id = dir.absolutePath,
            name = dir.name,
            location = newLocation,
            type = FileType.DIRECTORY,
            metadata = metadata
        )
    }

    suspend fun delete(path: String) {
        if (LocalPathSanitizer.isRootOrProtectedPath(path)) {
            throw SecurityException("Cannot delete root or protected storage path: $path")
        }
        val file = File(path)
        if (!file.exists()) {
            throw FileNotFoundException("File not found: $path")
        }
        val isSymlink = directoryOperationHelper.isSymbolicLink(file)
        if (isSymlink) {
            if (!file.delete()) {
                throw IOException("Failed to delete symlink: $path")
            }
        } else if (file.isDirectory) {
            directoryOperationHelper.deleteDirectoryRecursivelySafe(file)
        } else {
            if (!file.delete()) {
                throw IOException("Failed to delete file: $path")
            }
        }
    }

    fun rename(
        path: String,
        newName: String,
        rootId: String = StorageConstants.PRIMARY_INTERNAL_VOLUME_ID
    ): FileItem {
        val target = directoryOperationHelper.rename(path, newName)
        val metadata = fileMetadataReader.readMetadata(target)
        val type = if (target.isDirectory) FileType.DIRECTORY else FileType.FILE
        val newLocation = StorageLocation(path = target.absolutePath, rootId = rootId)
        return fileItemMapper.map(
            id = target.absolutePath,
            name = target.name,
            location = newLocation,
            type = type,
            metadata = metadata
        )
    }

    fun copy(sourcePath: String, destPath: String): Flow<FileOperationProgress> = flow {
        val sourceFile = File(sourcePath)
        val destFile = File(destPath)

        if (!sourceFile.exists()) {
            throw FileNotFoundException("Source not found: $sourcePath")
        }

        if (sourceFile.absolutePath == destFile.absolutePath) {
            throw IllegalArgumentException("Source and destination are the same")
        }

        if (sourceFile.isDirectory && destFile.absolutePath.startsWith(sourceFile.absolutePath + File.separator)) {
            throw IllegalArgumentException("Cannot copy a directory into itself")
        }

        emit(FileOperationProgress(0L, 0L, sourceFile.name))

        var totalCopied = 0L
        val totalBytes = if (sourceFile.isDirectory) directoryOperationHelper.calculateTotalSize(sourceFile) else sourceFile.length()

        if (sourceFile.isDirectory) {
            directoryOperationHelper.copyDirectoryTransactionally(sourceFile, destFile, totalBytes) { incrementalBytes, fileName ->
                totalCopied += incrementalBytes
                emit(FileOperationProgress(totalCopied, totalBytes, fileName))
            }
        } else {
            streamTransferHelper.copySingleFile(sourceFile, destFile, totalBytes) { incrementalBytes, fileName ->
                totalCopied += incrementalBytes
                emit(FileOperationProgress(totalCopied, totalBytes, fileName))
            }
        }
    }.flowOn(Dispatchers.IO)

    fun move(sourcePath: String, destPath: String): Flow<FileOperationProgress> = flow {
        val sourceFile = File(sourcePath)
        val destFile = File(destPath)

        if (!sourceFile.exists()) {
            throw FileNotFoundException("Source not found: $sourcePath")
        }

        val sourceCanonical = sourceFile.canonicalPath
        val destCanonical = destFile.canonicalPath

        if (sourceCanonical == destCanonical) {
            throw IOException("Source and destination are the same")
        }

        if (sourceFile.isDirectory && destCanonical.startsWith(sourceCanonical + File.separator)) {
            throw IOException("Cannot move a directory into itself")
        }

        emit(FileOperationProgress(0L, 0L, sourceFile.name))

        val sourceLength = if (sourceFile.isFile) sourceFile.length() else directoryOperationHelper.calculateTotalSize(sourceFile)
        val isSourceDir = sourceFile.isDirectory

        // Coba atomic rename terlebih dahulu
        val renamed = sourceFile.renameTo(destFile)
        if (renamed) {
            emit(FileOperationProgress(destFile.length(), destFile.length(), destFile.name))
            return@flow
        }

        if (isSourceDir) {
            var copied = 0L
            directoryOperationHelper.copyDirectoryTransactionally(sourceFile, destFile, sourceLength, afterPublish = {
                currentCoroutineContext().ensureActive()
                directoryOperationHelper.validateDirectoryTree(sourceFile, destFile)
                currentCoroutineContext().ensureActive()
                delete(sourcePath)
            }) { bytes, name ->
                copied += bytes
                emit(FileOperationProgress(copied, sourceLength, name))
            }
            return@flow
        }

        // Fallback ke copy lalu delete jika beda mount point
        copy(sourcePath, destPath).collect { progress ->
            emit(progress)
        }

        if (kotlinx.coroutines.currentCoroutineContext().isActive) {
            if (!destFile.exists()) {
                throw IOException("Move failed: destination does not exist after copy ($destPath)")
            }
            if (!isSourceDir && destFile.length() != sourceLength) {
                try { destFile.delete() } catch (e: Exception) {
            if (e is CancellationException) throw e; android.util.Log.w("FileSystem", "Failed to clean partial file", e) }
                throw IOException("Move failed: partial copy detected (destination size mismatch)")
            }
            delete(sourcePath)
        }
    }.flowOn(Dispatchers.IO)

    fun getFileItem(
        path: String,
        rootId: String = StorageConstants.PRIMARY_INTERNAL_VOLUME_ID
    ): FileItem? {
        val file = File(path)
        if (!file.exists()) return null
        val metadata = fileMetadataReader.readMetadata(file)
        val type = if (file.isDirectory) FileType.DIRECTORY else FileType.FILE
        val itemLocation = StorageLocation(path = file.absolutePath, rootId = rootId)
        return fileItemMapper.map(
            id = file.absolutePath,
            name = file.name,
            location = itemLocation,
            type = type,
            metadata = metadata
        )
    }

    fun exists(path: String): Boolean = File(path).exists()
}
