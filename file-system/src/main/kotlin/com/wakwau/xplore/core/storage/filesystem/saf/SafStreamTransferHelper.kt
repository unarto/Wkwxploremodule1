// [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/filesystem/saf/SafStreamTransferHelper.kt
// [Penjelasan]: Helper terisolasi untuk menangani transfer I/O streaming DocumentFile SAF, pembuatan direktori rekursif, kalkulasi ukuran direktori SAF, dan deteksi pembatalan coroutine.
package com.wakwau.xplore.core.storage.filesystem.saf

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.wakwau.xplore.core.storage.constant.StorageConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID

class SafStreamTransferHelper(private val context: Context) {

    suspend fun copySingleFile(
        sourceDoc: DocumentFile,
        destDoc: DocumentFile,
        targetName: String?,
        totalBytes: Long,
        onProgress: suspend (Long, String) -> Unit
    ) {
        val fileName = targetName ?: sourceDoc.name ?: StorageConstants.DEFAULT_UNKNOWN_FILE_NAME
        val mimeType = sourceDoc.type ?: StorageConstants.DEFAULT_MIME_TYPE_ALL
        val parent = if (destDoc.isDirectory) destDoc else destDoc.parentFile
            ?: throw IOException("Cannot safely replace SAF destination without a parent: ${destDoc.uri}")
        val existingTarget = if (destDoc.isDirectory) parent.findFile(fileName) else destDoc
        val temporaryName = ".$fileName.wkw-${UUID.randomUUID()}.tmp"
        val temporaryFile = try {
            parent.createFile(mimeType, temporaryName)
        } catch (error: IllegalArgumentException) {
            throw IOException("Invalid argument when creating temporary SAF file: $temporaryName", error)
        } ?: throw IOException("Failed to create temporary SAF file: $temporaryName")

        val buffer = ByteArray(StorageConstants.Buffer.DEFAULT_I_O_BUFFER_SIZE_BYTES)
        try {
            val input = context.contentResolver.openInputStream(sourceDoc.uri)
                ?: throw FileNotFoundException("Cannot open input stream: ${sourceDoc.uri}")
            val output = context.contentResolver.openOutputStream(temporaryFile.uri)
                ?: run {
                    input.close()
                    throw FileNotFoundException("Cannot open output stream: ${temporaryFile.uri}")
                }
            input.use { inStream ->
                output.use { outStream ->
                    var bytesRead: Int
                    while (inStream.read(buffer).also { bytesRead = it } >= 0) {
                        if (!currentCoroutineContext().isActive) {
                            throw CancellationException("Copy cancelled")
                        }
                        outStream.write(buffer, 0, bytesRead)
                        onProgress(bytesRead.toLong(), fileName)
                    }
                    outStream.flush()
                }
            }
            if (sourceDoc.isFile && temporaryFile.length() != sourceDoc.length()) {
                throw IOException("Partial copy detected: temporary size (${temporaryFile.length()}) does not match source size (${sourceDoc.length()})")
            }
            replaceSafely(parent, temporaryFile, existingTarget, fileName)
        } catch (e: Throwable) {
            try {
                parent.findFile(temporaryName)?.delete()
            } catch (_: Exception) {
            }
            throw e
        }
    }

    private fun replaceSafely(
        parent: DocumentFile,
        temporary: DocumentFile,
        existing: DocumentFile?,
        finalName: String
    ) {
        if (existing == null) {
            if (!temporary.renameTo(finalName)) throw IOException("Failed to publish SAF destination: $finalName")
            return
        }

        val backupName = ".$finalName.wkw-${UUID.randomUUID()}.bak"
        if (!existing.renameTo(backupName)) throw IOException("Failed to preserve existing SAF destination: $finalName")
        try {
            if (!temporary.renameTo(finalName)) throw IOException("Failed to publish SAF destination: $finalName")
            if (!existing.delete() && existing.exists()) throw IOException("Failed to remove SAF destination backup: $backupName")
        } catch (error: Throwable) {
            parent.findFile(finalName)?.delete()
            existing.renameTo(finalName)
            throw error
        }
    }

    suspend fun copyDirectoryTransactionally(
        sourceDir: DocumentFile,
        destParentDir: DocumentFile,
        targetName: String?,
        totalBytes: Long,
        afterPublish: suspend (DocumentFile) -> Unit = {},
        onProgress: suspend (Long, String) -> Unit
    ) {
        val finalName = targetName ?: sourceDir.name ?: StorageConstants.DEFAULT_UNKNOWN_FILE_NAME
        val stagingName = ".$finalName.wkw-${UUID.randomUUID()}.tmp"
        val staging = destParentDir.createDirectory(stagingName)
            ?: throw IOException("Failed to create SAF staging directory: $stagingName")
        try {
            copyDirectoryContents(sourceDir, staging, totalBytes, onProgress)
            currentCoroutineContext().ensureActive()
            publishDirectory(destParentDir, staging, finalName, afterPublish)
        } catch (error: Throwable) {
            try { if (staging.exists() && !staging.delete()) throw IOException("Failed to remove SAF staging directory: $stagingName") }
            catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
            throw error
        }
    }

    private suspend fun publishDirectory(
        parent: DocumentFile,
        staging: DocumentFile,
        finalName: String,
        afterPublish: suspend (DocumentFile) -> Unit
    ) {
        val existing = parent.findFile(finalName)
        val backupName = existing?.let {
            ".$finalName.wkw-${UUID.randomUUID()}.bak".also { backup ->
                if (!existing.renameTo(backup)) throw IOException("Failed to preserve SAF destination: $finalName")
            }
        }
        try {
            if (!staging.renameTo(finalName)) throw IOException("Failed to publish SAF directory: $finalName")
            val published = parent.findFile(finalName)
                ?: throw IOException("Published SAF directory cannot be resolved: $finalName")
            afterPublish(published)
        } catch (error: Throwable) {
            parent.findFile(finalName)?.let { if (!it.delete()) error.addSuppressed(IOException("Failed to remove failed SAF publish: $finalName")) }
            if (existing != null && !existing.renameTo(finalName)) error.addSuppressed(IOException("Failed to restore SAF destination: $finalName"))
            throw error
        }
        if (existing != null && !existing.delete() && existing.exists()) {
            throw IOException("Move committed but failed to remove SAF directory backup: $backupName")
        }
    }

    private suspend fun copyDirectoryContents(
        sourceDir: DocumentFile,
        targetDir: DocumentFile,
        totalBytes: Long,
        onProgress: suspend (Long, String) -> Unit
    ) {
        val children = listChildrenOrThrow(sourceDir)
        for (child in children) {
            currentCoroutineContext().ensureActive()
            if (child.isDirectory) copyDirectoryRecursively(child, targetDir, null, totalBytes, onProgress)
            else copySingleFile(child, targetDir, null, totalBytes, onProgress)
        }
    }

    suspend fun validateDirectoryTree(source: DocumentFile, destination: DocumentFile) {
        if (directoryManifest(source) != directoryManifest(destination)) {
            throw IOException("SAF directory move validation failed: destination subtree differs from source")
        }
    }

    private suspend fun directoryManifest(root: DocumentFile): Map<String, Pair<Boolean, Long>> {
        val result = linkedMapOf<String, Pair<Boolean, Long>>()
        val queue = ArrayDeque<Pair<DocumentFile, String>>()
        queue.add(root to "")
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (directory, prefix) = queue.removeFirst()
            if (!directory.isDirectory) throw IOException("Expected SAF directory during validation: ${directory.uri}")
            for (child in listChildrenOrThrow(directory)) {
                currentCoroutineContext().ensureActive()
                val name = child.name ?: throw IOException("Unnamed SAF child during validation: ${child.uri}")
                val relative = if (prefix.isEmpty()) name else "$prefix/$name"
                result[relative] = child.isDirectory to if (child.isDirectory) 0L else child.length()
                if (child.isDirectory) queue.add(child to relative)
            }
        }
        return result
    }

    suspend fun copyDirectoryRecursively(
        sourceDir: DocumentFile,
        destParentDir: DocumentFile,
        targetName: String?,
        totalBytes: Long,
        onProgress: suspend (Long, String) -> Unit
    ) {
        val dirName = targetName ?: sourceDir.name ?: StorageConstants.DEFAULT_UNKNOWN_FILE_NAME
        val targetDir = try {
            destParentDir.findFile(dirName)?.takeIf { it.isDirectory }
                ?: destParentDir.createDirectory(dirName)
        } catch (e: IllegalArgumentException) {
            throw IOException("Invalid argument when creating SAF directory: $dirName", e)
        } ?: throw IOException("Failed to create SAF directory: $dirName")

        copyDirectoryContents(sourceDir, targetDir, totalBytes, onProgress)
    }

    fun calculateTotalSize(doc: DocumentFile): Long {
        var size = 0L
        val queue = ArrayDeque<DocumentFile>()
        queue.add(doc)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val children = listChildrenOrThrow(current)
            for (child in children) {
                if (child.isDirectory) {
                    queue.add(child)
                } else {
                    size += child.length()
                }
            }
        }
        return size
    }

    private fun listChildrenOrThrow(directory: DocumentFile): Array<DocumentFile> = try {
        directory.listFiles()
    } catch (error: SecurityException) {
        throw error
    } catch (error: Exception) {
        throw IOException("Failed to list SAF source directory: ${directory.uri}", error)
    }
}
