// [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/filesystem/saf/SafStreamTransferHelper.kt
// [Penjelasan]: Helper terisolasi untuk menangani transfer I/O streaming DocumentFile SAF, pembuatan direktori rekursif, kalkulasi ukuran direktori SAF, dan deteksi pembatalan coroutine.
package com.wakwau.xplore.core.storage.filesystem.saf

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.wakwau.xplore.core.storage.constant.StorageConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.io.FileNotFoundException
import java.io.IOException

class SafStreamTransferHelper(private val context: Context) {

    suspend fun copySingleFile(
        sourceDoc: DocumentFile,
        destDoc: DocumentFile,
        targetName: String?,
        totalBytes: Long,
        onProgress: suspend (Long, String) -> Unit
    ) {
        val isDestDir = destDoc.isDirectory

        val targetFile = if (isDestDir) {
            val mimeType = sourceDoc.type ?: StorageConstants.DEFAULT_MIME_TYPE_ALL
            val displayName = targetName ?: sourceDoc.name ?: StorageConstants.DEFAULT_UNKNOWN_FILE_NAME
            try {
                val existingFile = destDoc.findFile(displayName)
                if (existingFile != null && existingFile.isFile) {
                    existingFile
                } else {
                    destDoc.createFile(mimeType, displayName)
                }
            } catch (e: IllegalArgumentException) {
                throw IOException("Invalid argument when creating destination SAF file: $displayName", e)
            } ?: throw IOException("Failed to create destination SAF file: $displayName")
        } else {
            destDoc
        }

        val fileName = targetName ?: sourceDoc.name ?: StorageConstants.DEFAULT_UNKNOWN_FILE_NAME
        val input = context.contentResolver.openInputStream(sourceDoc.uri)
            ?: throw FileNotFoundException("Cannot open input stream: ${sourceDoc.uri}")
        val output = context.contentResolver.openOutputStream(targetFile.uri)
            ?: throw FileNotFoundException("Cannot open output stream: ${targetFile.uri}")

        val buffer = ByteArray(StorageConstants.Buffer.DEFAULT_I_O_BUFFER_SIZE_BYTES)
        try {
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
            if (sourceDoc.isFile && targetFile.length() != sourceDoc.length()) {
                throw IOException("Partial copy detected: destination size (${targetFile.length()}) does not match source size (${sourceDoc.length()})")
            }
        } catch (e: Throwable) {
            if (isDestDir) {
                try {
                    targetFile.delete()
                } catch (_: Exception) {
                }
            }
            throw e
        }
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

        val children = sourceDir.listFiles() ?: emptyArray()

        for (child in children) {
            if (!currentCoroutineContext().isActive) {
                throw CancellationException("Copy cancelled")
            }
            val isChildDir = child.isDirectory
            if (isChildDir) {
                copyDirectoryRecursively(child, targetDir, null, totalBytes, onProgress)
            } else {
                copySingleFile(child, targetDir, null, totalBytes, onProgress)
            }
        }
    }

    fun calculateTotalSize(doc: DocumentFile): Long {
        var size = 0L
        val queue = ArrayDeque<DocumentFile>()
        queue.add(doc)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val children = current.listFiles() ?: continue
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
}
