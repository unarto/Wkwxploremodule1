// [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/filesystem/shizuku/ShizukuFileTransferHandler.kt
// [Penjelasan]: Mengelola operasi streaming byte untuk copy dan move lintas/dalam Shizuku filesystem, mem-bypass limit IPC dengan ParcelFileDescriptor, mencegah TransactionTooLargeException.
package com.wakwau.xplore.core.storage.filesystem.shizuku

import com.wakwau.xplore.core.storage.constant.StorageConstants
import com.wakwau.xplore.core.storage.shizuku.IPrivilegedFileService
import com.wakwau.xplore.core.storage.shizuku.ShizukuIpcConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.isActive
import java.util.UUID

class ShizukuFileTransferHandler {

    suspend fun copySingleFile(
        service: IPrivilegedFileService,
        sourcePath: String,
        destPath: String,
        totalBytes: Long,
        onProgress: suspend (Long, String) -> Unit
    ) {
        val temporaryPath = "$destPath.wkw-${UUID.randomUUID()}.tmp"
        val readFd = service.openFileForRead(sourcePath) 
            ?: throw IOException("Failed to open source file for reading in root: $sourcePath")
            
        val writeFd = service.openFileForWrite(temporaryPath)
            ?: run {
                readFd.close()
                service.delete(temporaryPath)
                throw IOException("Failed to open destination file for writing in root: $destPath")
            }

        var sourceName = sourcePath.substringAfterLast("/")
        if (sourceName.isEmpty()) sourceName = StorageConstants.DEFAULT_UNKNOWN_FILE_NAME

        try {
            FileInputStream(readFd.fileDescriptor).use { input ->
                FileOutputStream(writeFd.fileDescriptor).use { output ->
                    val buffer = ByteArray(StorageConstants.Buffer.DEFAULT_I_O_BUFFER_SIZE_BYTES)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } >= 0) {
                        if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                            throw CancellationException("Copy cancelled")
                        }
                        output.write(buffer, 0, bytesRead)
                        onProgress(bytesRead.toLong(), sourceName)
                    }
                    output.flush()
                }
            }
            val isDir = service.isDirectory(sourcePath)
            if (!isDir) {
                val srcLen = service.length(sourcePath)
                val destLen = service.length(temporaryPath)
                if (destLen != srcLen) {
                    throw IOException("Partial copy detected: destination size ($destLen) does not match source size ($srcLen)")
                }
            }
            replaceSafely(service, temporaryPath, destPath)
        } catch (e: Throwable) {
            try { service.delete(temporaryPath) } catch (e: Exception) { android.util.Log.w("FileSystem", "Failed to clean temporary file", e) }
            throw e
        } finally {
            try { readFd.close() } catch (e: Exception) { /* ignore */ }
            try { writeFd.close() } catch (e: Exception) { /* ignore */ }
        }
    }

    private fun replaceSafely(service: IPrivilegedFileService, temporaryPath: String, destinationPath: String) {
        if (!service.exists(destinationPath)) {
            if (!service.rename(temporaryPath, destinationPath)) throw IOException("Failed to publish Shizuku destination: $destinationPath")
            return
        }

        val backupPath = "$destinationPath.wkw-${UUID.randomUUID()}.bak"
        if (!service.rename(destinationPath, backupPath)) throw IOException("Failed to preserve existing Shizuku destination: $destinationPath")
        try {
            if (!service.rename(temporaryPath, destinationPath)) throw IOException("Failed to publish Shizuku destination: $destinationPath")
            if (!service.delete(backupPath) && service.exists(backupPath)) throw IOException("Failed to remove Shizuku destination backup: $backupPath")
        } catch (error: Throwable) {
            if (service.exists(destinationPath)) service.delete(destinationPath)
            service.rename(backupPath, destinationPath)
            throw error
        }
    }

    suspend fun copyDirectoryTransactionally(
        service: IPrivilegedFileService,
        sourcePath: String,
        destinationPath: String,
        totalBytes: Long,
        afterPublish: suspend () -> Unit = {},
        onProgress: suspend (Long, String) -> Unit
    ) {
        val staging = "$destinationPath.wkw-${java.util.UUID.randomUUID()}.tmp"
        try {
            if (!service.createDirectory(staging)) throw IOException("Failed to create Shizuku staging directory: $staging")
            copyDirectoryRecursively(service, sourcePath, staging, totalBytes, onProgress)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            publishDirectory(service, staging, destinationPath, afterPublish)
        } catch (error: Throwable) {
            try { if (service.exists(staging) && !service.delete(staging)) throw IOException("Failed to remove Shizuku staging directory: $staging") }
            catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
            throw error
        }
    }

    private suspend fun publishDirectory(service: IPrivilegedFileService, staging: String, destination: String, afterPublish: suspend () -> Unit) {
        val backup = if (service.exists(destination)) {
            "$destination.wkw-${java.util.UUID.randomUUID()}.bak".also {
                if (!service.rename(destination, it)) throw IOException("Failed to preserve Shizuku destination: $destination")
            }
        } else null
        try {
            if (!service.rename(staging, destination)) throw IOException("Failed to publish Shizuku directory: $destination")
            afterPublish()
        } catch (error: Throwable) {
            if (service.exists(destination) && !service.delete(destination)) error.addSuppressed(IOException("Failed to remove failed Shizuku publish: $destination"))
            if (backup != null && !service.rename(backup, destination)) error.addSuppressed(IOException("Failed to restore Shizuku destination: $destination"))
            throw error
        }
        if (backup != null && !service.delete(backup) && service.exists(backup)) {
            throw IOException("Move committed but failed to remove Shizuku directory backup: $backup")
        }
    }

    suspend fun validateDirectoryTree(service: IPrivilegedFileService, source: String, destination: String) {
        if (directoryManifest(service, source) != directoryManifest(service, destination)) {
            throw IOException("Shizuku directory move validation failed: destination subtree differs from source")
        }
    }

    private suspend fun directoryManifest(service: IPrivilegedFileService, root: String): Map<String, Pair<Boolean, Long>> {
        val result = linkedMapOf<String, Pair<Boolean, Long>>()
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add(root to "")
        while (queue.isNotEmpty()) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val (directory, prefix) = queue.removeFirst()
            if (!service.exists(directory) || !service.isDirectory(directory)) throw IOException("Expected Shizuku directory during validation: $directory")
            for (entry in service.listDirectory(directory)) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val name = entry.getString(ShizukuIpcConstants.KEY_NAME)
                    ?: throw IOException("Invalid Shizuku entry during validation: $directory")
                if (name == "." || name == "..") continue
                val path = entry.getString(ShizukuIpcConstants.KEY_PATH)
                    ?: throw IOException("Invalid Shizuku path during validation: $directory/$name")
                val isDirectory = entry.getBoolean(ShizukuIpcConstants.KEY_IS_DIRECTORY)
                val relative = if (prefix.isEmpty()) name else "$prefix/$name"
                result[relative] = isDirectory to if (isDirectory) 0L else service.length(path)
                if (isDirectory) queue.add(path to relative)
            }
        }
        return result
    }

    suspend fun copyDirectoryRecursively(
        service: IPrivilegedFileService,
        sourcePath: String,
        destPath: String,
        totalBytes: Long,
        onProgress: suspend (Long, String) -> Unit
    ) {
        if (!service.exists(destPath)) {
            val created = service.createDirectory(destPath)
            if (!created) {
                throw IOException("Failed to create destination directory: $destPath")
            }
        }

        val children = service.listDirectory(sourcePath)
        for (bundle in children) {
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                throw CancellationException("Copy cancelled")
            }
            
            val childName = bundle.getString(ShizukuIpcConstants.KEY_NAME)
                ?: throw IOException("Invalid directory entry without a name: $sourcePath")
            if (childName == "." || childName == "..") continue
            
            val childSourcePath = bundle.getString(ShizukuIpcConstants.KEY_PATH)
                ?: throw IOException("Invalid directory entry without a path: $sourcePath/$childName")
            val isDirectory = bundle.getBoolean(ShizukuIpcConstants.KEY_IS_DIRECTORY)
            
            val childDestPath = if (destPath.endsWith("/")) "$destPath$childName" else "$destPath/$childName"

            if (isDirectory) {
                copyDirectoryRecursively(service, childSourcePath, childDestPath, totalBytes, onProgress)
            } else {
                copySingleFile(service, childSourcePath, childDestPath, totalBytes, onProgress)
            }
        }
    }

    fun calculateTotalSize(service: IPrivilegedFileService, path: String): Long {
        if (!service.exists(path)) return 0L
        if (!service.isDirectory(path)) {
            return service.length(path)
        }
        var size = 0L
        val queue = ArrayDeque<String>()
        queue.add(path)
        while (queue.isNotEmpty()) {
            val currentPath = queue.removeFirst()
            val children = service.listDirectory(currentPath)
            for (bundle in children) {
                val childPath = bundle.getString(ShizukuIpcConstants.KEY_PATH)
                    ?: throw IOException("Invalid directory entry while calculating size: $currentPath")
                val isDirectory = bundle.getBoolean(ShizukuIpcConstants.KEY_IS_DIRECTORY)
                if (isDirectory) {
                    queue.add(childPath)
                } else {
                    size += bundle.getLong(ShizukuIpcConstants.KEY_SIZE)
                }
            }
        }
        return size
    }
}
