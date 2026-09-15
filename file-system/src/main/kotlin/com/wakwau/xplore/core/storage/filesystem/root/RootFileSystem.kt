// [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/filesystem/root/RootFileSystem.kt
// [Penjelasan]: Implementasi fasad sistem berkas berbasis Root Superuser (SU) menggunakan API resmi libsu (Topjohnwu) yang mendelegasikan listing, transfer, dan operasi direktori ke helper khusus (< 250 LOC).
package com.wakwau.xplore.core.storage.filesystem.root

import kotlinx.coroutines.CancellationException
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.wakwau.xplore.core.storage.constant.StorageConstants
import com.wakwau.xplore.core.storage.filesystem.RootFileSystemContract
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.FileMetadata
import com.wakwau.xplore.core.storage.model.FileType
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.FileOperationProgress
import com.wakwau.xplore.core.storage.permission.SuPermissionChecker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class RootFileSystem(
    private val suPermissionChecker: SuPermissionChecker = SuPermissionChecker(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val streamTransferHelper: RootStreamTransferHelper = RootStreamTransferHelper(),
    private val directoryListingHelper: RootDirectoryListingHelper = RootDirectoryListingHelper(streamTransferHelper)
) : RootFileSystemContract {

    override fun isAvailable(): Boolean = suPermissionChecker.isSuAvailable()

    override suspend fun listFiles(
        location: StorageLocation,
        showHidden: Boolean
    ): List<FileItem> = withContext(ioDispatcher) {
        ensureRootAccess()
        directoryListingHelper.listFiles(location, showHidden)
    }

    override suspend fun createDirectory(
        location: StorageLocation,
        name: String
    ): FileItem = withContext(ioDispatcher) {
        ensureRootAccess()
        directoryListingHelper.createDirectory(location, name)
    }

    override suspend fun exists(location: StorageLocation): Boolean = withContext(ioDispatcher) {
        if (!isAvailable()) return@withContext false
        SuFile(location.path).exists()
    }

    override suspend fun delete(location: StorageLocation) = withContext(ioDispatcher) {
        ensureRootAccess()
        if (directoryListingHelper.isProtectedRootPath(location.path)) {
            throw SecurityException("Cannot delete root or protected storage path: ${location.path}")
        }

        val suFile = SuFile(location.path)
        if (!suFile.exists()) {
            throw FileNotFoundException("File not found in root: ${location.path}")
        }

        if (suFile.isDirectory) {
            directoryListingHelper.deleteDirectoryRecursively(suFile)
        } else {
            if (!suFile.delete() && suFile.exists()) {
                throw IOException("Failed to delete root file: ${location.path}")
            }
        }
    }

    override suspend fun rename(
        location: StorageLocation,
        newName: String
    ): FileItem = withContext(ioDispatcher) {
        ensureRootAccess()

        val trimmedName = newName.trim()
        if (trimmedName.isEmpty() || trimmedName.contains("/") || trimmedName.contains("\\") || trimmedName == ".." || trimmedName == ".") {
            throw IllegalArgumentException("Invalid name: $newName")
        }

        val sourceFile = SuFile(location.path)
        if (!sourceFile.exists()) {
            throw FileNotFoundException("File not found in root: ${location.path}")
        }

        val parentPath = if (location.path.contains("/")) location.path.substringBeforeLast("/") else ""
        val targetPath = if (parentPath.isEmpty()) "/$trimmedName" else "$parentPath/$trimmedName"
        val targetFile = SuFile(targetPath)

        if (targetFile.exists()) {
            throw IOException("Target already exists in root: $targetPath")
        }

        sourceFile.renameTo(targetFile) || Shell.cmd("mv ${directoryListingHelper.escapeShellArg(location.path)} ${directoryListingHelper.escapeShellArg(targetPath)}").exec().isSuccess

        if (!targetFile.exists()) {
            throw IOException("Failed to rename root file: ${location.path}")
        }

        val isDir = targetFile.isDirectory
        val metadata = FileMetadata(
            size = if (isDir) 0L else targetFile.length(),
            modifiedTime = targetFile.lastModified(),
            createdTime = null,
            isReadable = true,
            isWritable = true,
            isExecutable = true,
            isHidden = trimmedName.startsWith(".")
        )

        FileItem(
            id = targetPath,
            name = trimmedName,
            location = StorageLocation(path = targetPath, rootId = location.rootId),
            type = if (isDir) FileType.DIRECTORY else FileType.FILE,
            metadata = metadata
        )
    }

    override suspend fun getFileItem(location: StorageLocation): FileItem? = withContext(ioDispatcher) {
        if (!isAvailable()) return@withContext null
        val suFile = SuFile(location.path)
        if (!suFile.exists()) return@withContext null

        val isDir = suFile.isDirectory
        val name = suFile.name.ifEmpty { location.path.substringAfterLast("/") }
        val metadata = FileMetadata(
            size = if (isDir) 0L else suFile.length(),
            modifiedTime = suFile.lastModified(),
            createdTime = null,
            isReadable = suFile.canRead(),
            isWritable = suFile.canWrite(),
            isExecutable = suFile.canExecute(),
            isHidden = suFile.isHidden || name.startsWith(".")
        )

        FileItem(
            id = location.path,
            name = name.ifEmpty { StorageConstants.DEFAULT_UNKNOWN_FILE_NAME },
            location = StorageLocation(path = location.path, rootId = location.rootId),
            type = if (isDir) FileType.DIRECTORY else FileType.FILE,
            metadata = metadata
        )
    }

    override fun copy(
        source: StorageLocation,
        destination: StorageLocation
    ): Flow<FileOperationProgress> = flow {
        ensureRootAccess()

        val sourceFile = SuFile(source.path)
        val destFile = SuFile(destination.path)

        if (!sourceFile.exists()) {
            throw FileNotFoundException("Source not found in root: ${source.path}")
        }
        if (sourceFile.absolutePath == destFile.absolutePath) {
            throw IllegalArgumentException("Source and destination are the same")
        }
        if (sourceFile.isDirectory && destFile.absolutePath.startsWith(sourceFile.absolutePath + File.separator)) {
            throw IllegalArgumentException("Cannot copy a directory into itself")
        }

        var totalCopied = 0L
        val totalBytes = if (sourceFile.isDirectory) directoryListingHelper.calculateTotalSize(sourceFile) else sourceFile.length()

        if (totalBytes == 0L) {
            emit(FileOperationProgress(0L, 0L, sourceFile.name))
        }

        if (sourceFile.isDirectory) {
            directoryListingHelper.copyDirectoryTransactionally(sourceFile, destFile, totalBytes) { incrementalBytes, fileName ->
                totalCopied += incrementalBytes
                emit(FileOperationProgress(totalCopied, totalBytes, fileName))
            }
        } else {
            streamTransferHelper.copySingleFile(sourceFile, destFile, totalBytes) { incrementalBytes, fileName ->
                totalCopied += incrementalBytes
                emit(FileOperationProgress(totalCopied, totalBytes, fileName))
            }
        }
    }.flowOn(ioDispatcher)

    override fun move(
        source: StorageLocation,
        destination: StorageLocation
    ): Flow<FileOperationProgress> = flow {
        ensureRootAccess()

        val sourceFile = SuFile(source.path)
        val destFile = SuFile(destination.path)

        if (!sourceFile.exists()) {
            throw FileNotFoundException("Source not found in root: ${source.path}")
        }
        if (sourceFile.absolutePath == destFile.absolutePath) {
            throw IOException("Source and destination are the same")
        }
        if (sourceFile.isDirectory && destFile.absolutePath.startsWith(sourceFile.absolutePath + File.separator)) {
            throw IOException("Cannot move a directory into itself")
        }

        val sourceLength = if (sourceFile.isFile) sourceFile.length() else 0L
        val isSourceDir = sourceFile.isDirectory

        val renamed = sourceFile.renameTo(destFile) || Shell.cmd("mv ${directoryListingHelper.escapeShellArg(source.path)} ${directoryListingHelper.escapeShellArg(destination.path)}").exec().isSuccess

        if (destFile.exists() && !sourceFile.exists()) {
            emit(FileOperationProgress(destFile.length(), destFile.length(), destFile.name))
            return@flow
        }

        if (isSourceDir) {
            val totalBytes = directoryListingHelper.calculateTotalSize(sourceFile)
            var copied = 0L
            directoryListingHelper.copyDirectoryTransactionally(sourceFile, destFile, totalBytes, afterPublish = {
                currentCoroutineContext().ensureActive()
                directoryListingHelper.validateDirectoryTree(sourceFile, destFile)
                currentCoroutineContext().ensureActive()
                delete(source)
            }) { bytes, name ->
                copied += bytes
                emit(FileOperationProgress(copied, totalBytes, name))
            }
            return@flow
        }

        copy(source, destination).collect { progress ->
            emit(progress)
        }

        if (currentCoroutineContext().isActive) {
            if (!destFile.exists()) {
                throw IOException("Move failed: destination does not exist after copy (${destination.path})")
            }
            if (!isSourceDir && destFile.length() != sourceLength) {
                try { destFile.delete() } catch (e: Exception) {
            if (e is CancellationException) throw e; android.util.Log.w("FileSystem", "Failed to clean partial file", e) }
                throw IOException("Move failed: partial copy detected (destination size mismatch)")
            }
            delete(source)
        }
    }.flowOn(ioDispatcher)

    private fun ensureRootAccess() {
        if (!isAvailable()) {
            throw SecurityException("Root access is not available or permission denied")
        }
    }
}
