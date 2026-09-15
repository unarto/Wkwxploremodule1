// [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/filesystem/shizuku/SafShizukuFileSystem.kt
// [Penjelasan]: Implementasi fasad sistem berkas nyata untuk akses Privileged Root/Shizuku menggunakan IPC AIDL IPrivilegedFileService yang mendelegasikan pemetaan data Bundle ke ShizukuBundleMapper dan logika transfer ke ShizukuFileTransferHandler (< 250 LOC).
package com.wakwau.xplore.core.storage.filesystem.shizuku

import kotlinx.coroutines.CancellationException
import android.content.Context
import com.wakwau.xplore.core.storage.constant.StorageConstants
import com.wakwau.xplore.core.storage.filesystem.ShizukuFileSystemContract
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.FileMetadata
import com.wakwau.xplore.core.storage.model.FileType
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.FileOperationProgress
import com.wakwau.xplore.core.storage.shizuku.ShizukuHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.FileNotFoundException
import java.io.IOException

class SafShizukuFileSystem(
    private val context: Context,
    private val bundleMapper: ShizukuBundleMapper = ShizukuBundleMapper(),
    private val transferHandler: ShizukuFileTransferHandler = ShizukuFileTransferHandler()
) : ShizukuFileSystemContract {

    override suspend fun listFiles(location: StorageLocation, showHidden: Boolean): List<FileItem> {
        val service = ShizukuHelper.getPrivilegedService(context.packageName)
            ?: throw FileNotFoundException("Root/Shizuku service not available")

        if (!service.exists(location.path) || !service.isDirectory(location.path)) {
            throw FileNotFoundException("Directory not found or is not a directory: ${location.path}")
        }

        val bundles = service.listDirectory(location.path)
        val mappedItems = bundles.map { bundle ->
            bundleMapper.mapBundleToFileItem(bundle, location.rootId)
                ?: throw IOException("Invalid entry returned while listing privileged directory: ${location.path}")
        }
        
        return mappedItems.filter { item ->
            if (!showHidden) !item.metadata.isHidden else true
        }.sortedWith(compareBy({ it.type != FileType.DIRECTORY }, { it.name.lowercase() }))
    }

    override suspend fun createDirectory(location: StorageLocation, name: String): FileItem {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || trimmedName.contains("/") || trimmedName.contains("\\") || trimmedName == ".." || trimmedName == ".") {
            throw IllegalArgumentException("Invalid directory name: $name")
        }

        val service = ShizukuHelper.getPrivilegedService(context.packageName)
            ?: throw FileNotFoundException("Root/Shizuku service not available")

        val parentPath = location.path.trimEnd('/')
        val targetPath = if (parentPath.isEmpty()) "/$trimmedName" else "$parentPath/$trimmedName"

        if (service.exists(targetPath)) throw IOException("Directory already exists: $targetPath")
        if (!service.createDirectory(targetPath)) throw IOException("Failed to create privileged directory: $targetPath")

        return FileItem(
            id = targetPath,
            name = name,
            location = StorageLocation(targetPath, location.rootId),
            type = FileType.DIRECTORY,
            metadata = FileMetadata(0L, System.currentTimeMillis(), null, true, true, true, name.startsWith("."))
        )
    }

    override suspend fun delete(location: StorageLocation) {
        val pathClean = location.path.trim().trimEnd('/')
        if (pathClean.isEmpty() || pathClean == "/" || pathClean.equals("/storage", ignoreCase = true) || pathClean.equals("/storage/emulated", ignoreCase = true) || pathClean.equals("/system", ignoreCase = true)) {
            throw SecurityException("Cannot delete root or protected storage path: ${location.path}")
        }
        val service = ShizukuHelper.getPrivilegedService(context.packageName)
            ?: throw FileNotFoundException("Root/Shizuku service not available")

        if (!service.exists(location.path)) throw FileNotFoundException("File not found in root: ${location.path}")
        if (!service.delete(location.path)) throw IOException("Failed to delete root file: ${location.path}")
    }

    override suspend fun rename(location: StorageLocation, newName: String): FileItem {
        if (newName.contains("/") || newName.contains("\\") || newName == ".." || newName == ".") {
            throw IllegalArgumentException("Invalid name: $newName")
        }

        val service = ShizukuHelper.getPrivilegedService(context.packageName)
            ?: throw FileNotFoundException("Root/Shizuku service not available")

        if (!service.exists(location.path)) throw FileNotFoundException("File not found in root: ${location.path}")

        val parentPath = if (location.path.contains("/")) location.path.substringBeforeLast("/") else ""
        val targetPath = if (parentPath.isEmpty()) "/$newName" else "$parentPath/$newName"

        if (service.exists(targetPath)) throw IOException("Target already exists: $targetPath")
        if (!service.rename(location.path, targetPath)) throw IOException("Failed to rename root file to: $newName")

        val isDir = service.isDirectory(targetPath)
        return FileItem(
            id = targetPath,
            name = newName,
            location = StorageLocation(targetPath, location.rootId),
            type = if (isDir) FileType.DIRECTORY else FileType.FILE,
            metadata = FileMetadata(
                size = if (isDir) 0L else service.length(targetPath),
                modifiedTime = service.lastModified(targetPath),
                createdTime = null,
                isReadable = true,
                isWritable = true,
                isExecutable = true,
                isHidden = newName.startsWith(".")
            )
        )
    }

    override suspend fun getFileItem(location: StorageLocation): FileItem? {
        val service = ShizukuHelper.getPrivilegedService(context.packageName) ?: return null
        if (!service.exists(location.path)) return null
        val isDir = service.isDirectory(location.path)
        val name = if (location.path.contains("/")) location.path.substringAfterLast("/") else location.path
        return FileItem(
            id = location.path,
            name = name.ifEmpty { StorageConstants.DEFAULT_UNKNOWN_FILE_NAME },
            location = StorageLocation(location.path, location.rootId),
            type = if (isDir) FileType.DIRECTORY else FileType.FILE,
            metadata = FileMetadata(
                size = if (isDir) 0L else service.length(location.path),
                modifiedTime = service.lastModified(location.path),
                createdTime = null,
                isReadable = true,
                isWritable = true,
                isExecutable = true,
                isHidden = name.startsWith(".")
            )
        )
    }

    override suspend fun exists(location: StorageLocation): Boolean {
        return ShizukuHelper.getPrivilegedService(context.packageName)?.exists(location.path) == true
    }

    override fun copy(source: StorageLocation, destination: StorageLocation): Flow<FileOperationProgress> = flow {
        val service = ShizukuHelper.getPrivilegedService(context.packageName)
            ?: throw FileNotFoundException("Root/Shizuku service not available")

        if (!service.exists(source.path)) throw FileNotFoundException("Source not found in root: ${source.path}")
        if (source.path == destination.path) throw IllegalArgumentException("Source and destination are the same")
        if (service.isDirectory(source.path) && destination.path.startsWith(source.path + "/")) {
            throw IllegalArgumentException("Cannot copy a directory into itself")
        }

        var totalCopied = 0L
        val totalBytes = transferHandler.calculateTotalSize(service, source.path)

        if (totalBytes == 0L) {
            val sourceName = source.path.trimEnd('/').substringAfterLast('/')
            emit(FileOperationProgress(0L, 0L, sourceName.ifEmpty { StorageConstants.DEFAULT_UNKNOWN_FILE_NAME }))
        }

        if (service.isDirectory(source.path)) {
            transferHandler.copyDirectoryTransactionally(service, source.path, destination.path, totalBytes) { incrementalBytes, fileName ->
                totalCopied += incrementalBytes
                emit(FileOperationProgress(totalCopied, totalBytes, fileName))
            }
        } else {
            transferHandler.copySingleFile(service, source.path, destination.path, totalBytes) { incrementalBytes, fileName ->
                totalCopied += incrementalBytes
                emit(FileOperationProgress(totalCopied, totalBytes, fileName))
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun move(source: StorageLocation, destination: StorageLocation): Flow<FileOperationProgress> = flow {
        val service = ShizukuHelper.getPrivilegedService(context.packageName)
            ?: throw FileNotFoundException("Root/Shizuku service not available")

        if (!service.exists(source.path)) throw FileNotFoundException("Source not found in root: ${source.path}")
        if (source.path == destination.path) throw IOException("Source and destination are the same")
        if (service.isDirectory(source.path) && destination.path.startsWith(source.path + "/")) {
            throw IOException("Cannot move a directory into itself")
        }

        val sourceSize = if (!service.isDirectory(source.path)) service.length(source.path) else 0L
        val isSourceDir = service.isDirectory(source.path)

        val renamed = service.rename(source.path, destination.path)
        if (renamed) {
            val destSize = if (isSourceDir) 0L else service.length(destination.path)
            emit(FileOperationProgress(destSize, destSize, destination.path.trimEnd('/').substringAfterLast('/')))
            return@flow
        }

        if (isSourceDir) {
            val totalBytes = transferHandler.calculateTotalSize(service, source.path)
            var copied = 0L
            transferHandler.copyDirectoryTransactionally(service, source.path, destination.path, totalBytes, afterPublish = {
                currentCoroutineContext().ensureActive()
                transferHandler.validateDirectoryTree(service, source.path, destination.path)
                currentCoroutineContext().ensureActive()
                delete(source)
            }) { bytes, name ->
                copied += bytes
                emit(FileOperationProgress(copied, totalBytes, name))
            }
            return@flow
        }

        copy(source, destination).collect { emit(it) }

        if (currentCoroutineContext().isActive) {
            if (!service.exists(destination.path)) throw IOException("Move failed: destination does not exist after copy (${destination.path})")
            if (!isSourceDir && service.length(destination.path) != sourceSize) {
                try { service.delete(destination.path) } catch (e: Exception) {
            if (e is CancellationException) throw e; android.util.Log.w("FileSystem", "Failed to clean partial file", e) }
                throw IOException("Move failed: partial copy detected (destination size mismatch)")
            }
            delete(source)
        }
    }.flowOn(Dispatchers.IO)
}
