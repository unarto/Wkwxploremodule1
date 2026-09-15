// [Modul: :file-system] [Jalur Class]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/filesystem/local/LocalDirectoryOperationHelper.kt
// [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API
package com.wakwau.xplore.core.storage.filesystem.local

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import com.wakwau.xplore.core.storage.permission.StoragePermissionChecker

class LocalDirectoryOperationHelper(
    private val streamTransferHelper: LocalStreamTransferHelper = LocalStreamTransferHelper(),
    private val storagePermissionChecker: StoragePermissionChecker? = null,
    private val directoryEntries: (File) -> Array<File>? = { it.listFiles() }
) {

    fun createDirectory(
        parentPath: String,
        name: String
    ): File {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || trimmedName.contains("/") || trimmedName.contains("\\") || trimmedName == ".." || trimmedName == ".") {
            throw IllegalArgumentException("Invalid directory name: $name")
        }
        var parent = File(parentPath)
        if (parent.exists() && parent.isFile) {
            parent = parent.parentFile ?: parent
        }
        if (!parent.exists() || !parent.isDirectory) {
            throw FileNotFoundException("Parent directory not found: $parentPath")
        }
        val dir = File(parent, trimmedName)

        // Validasi Sanitasi Path Traversal
        val parentCanonical = parent.canonicalPath
        val dirCanonical = dir.canonicalPath
        if (!dirCanonical.startsWith(parentCanonical + File.separator) && dirCanonical != parentCanonical) {
            throw SecurityException("Path traversal attempt detected: $trimmedName")
        }

        if (dir.exists()) {
            throw IOException("Directory already exists: $trimmedName")
        }
        // [Modul: :file-system] [Jalur Class]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/filesystem/local/LocalDirectoryOperationHelper.kt
        // [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API (hindari canWrite() prematur, eksekusi mkdir/mkdirs aman)
        val created = try {
            dir.mkdir() || dir.mkdirs()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw IOException("Failed to create directory: $trimmedName", e)
        }
        if (!created && !dir.exists()) {
            // [Modul: :file-system] [Jalur Class]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/filesystem/local/LocalDirectoryOperationHelper.kt
            // [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API (evaluasi izin penyimpanan vs I/O error fisik riil)
            val hasStorageAccess = storagePermissionChecker?.hasAllFilesAccess() ?: try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    android.os.Environment.isExternalStorageManager()
                } else {
                    parent.canWrite()
                }
            } catch (t: Throwable) {
                parent.canWrite()
            }

            if (!hasStorageAccess || !parent.canWrite()) {
                throw SecurityException("Access denied: Permission MANAGE_EXTERNAL_STORAGE required or directory access denied for path: $parentPath")
            } else {
                throw IOException("Failed to create directory due to filesystem I/O error: $trimmedName")
            }
        }
        return dir
    }

    fun rename(
        path: String,
        newName: String
    ): File {
        if (newName.contains("/") || newName.contains("\\") || newName == ".." || newName == ".") {
            throw IllegalArgumentException("Invalid name: $newName")
        }
        val file = File(path)
        if (!file.exists()) {
            throw FileNotFoundException("File not found: $path")
        }
        val parent = file.parentFile ?: throw IOException("Parent directory not found for: $path")
        val target = File(parent, newName)

        // Validasi Sanitasi Path Traversal
        val parentCanonical = parent.canonicalPath
        val targetCanonical = target.canonicalPath
        if (!targetCanonical.startsWith(parentCanonical + File.separator) && targetCanonical != parentCanonical) {
            throw SecurityException("Path traversal attempt detected: $newName")
        }

        if (target.exists()) {
            throw IOException("Target already exists: $newName")
        }
        if (!file.renameTo(target)) {
            throw IOException("Failed to rename file to: $newName")
        }
        return target
    }

    suspend fun deleteDirectoryRecursivelySafe(dir: File) {
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        val filesToDelete = ArrayDeque<File>()

        while (stack.isNotEmpty()) {
            if (!currentCoroutineContext().isActive) throw CancellationException()
            val current = stack.removeLast()
            filesToDelete.addFirst(current)

            val isSymlink = isSymbolicLink(current)
            if (!isSymlink && current.isDirectory) {
                val children = current.listFiles() ?: continue
                for (child in children) {
                    stack.addLast(child)
                }
            }
        }

        for (file in filesToDelete) {
            if (!currentCoroutineContext().isActive) throw CancellationException()
            if (!file.delete() && file.exists()) {
                throw IOException("Failed to delete: ${file.absolutePath}")
            }
        }
    }

    suspend fun copyDirectoryTransactionally(
        sourceDir: File,
        destDir: File,
        totalBytes: Long,
        afterPublish: suspend () -> Unit = {},
        onProgress: suspend (Long, String) -> Unit
    ) {
        val parent = destDir.parentFile ?: throw IOException("Destination has no parent: ${destDir.absolutePath}")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Failed to create destination parent: ${parent.absolutePath}")
        val staging = File(parent, ".${destDir.name}.wkw-${java.util.UUID.randomUUID()}.tmp")
        try {
            if (!staging.mkdir()) throw IOException("Failed to create staging directory: ${staging.absolutePath}")
            copyDirectoryRecursively(sourceDir, staging, totalBytes, onProgress)
            currentCoroutineContext().ensureActive()
            publishDirectory(staging, destDir, afterPublish)
        } catch (error: Throwable) {
            try { if (staging.exists()) staging.deleteRecursively() } catch (cleanup: Throwable) {
                error.addSuppressed(cleanup)
            }
            throw error
        }
    }

    private suspend fun publishDirectory(staging: File, destination: File, afterPublish: suspend () -> Unit) {
        val backup = destination.takeIf { it.exists() }?.let {
            File(destination.parentFile, ".${destination.name}.wkw-${java.util.UUID.randomUUID()}.bak").also { backup ->
                if (!destination.renameTo(backup)) throw IOException("Failed to preserve destination: ${destination.absolutePath}")
            }
        }
        try {
            if (!staging.renameTo(destination)) throw IOException("Failed to publish directory: ${destination.absolutePath}")
            afterPublish()
        } catch (error: Throwable) {
            if (destination.exists()) destination.deleteRecursively()
            if (backup != null && !backup.renameTo(destination)) {
                error.addSuppressed(IOException("Failed to restore destination: ${destination.absolutePath}"))
            }
            throw error
        }
        if (backup != null && !backup.deleteRecursively() && backup.exists()) {
            throw IOException("Move committed but failed to remove directory backup: ${backup.absolutePath}")
        }
    }

    suspend fun validateDirectoryTree(source: File, destination: File) {
        val sourceManifest = directoryManifest(source)
        val destinationManifest = directoryManifest(destination)
        if (sourceManifest != destinationManifest) {
            throw IOException("Directory move validation failed: destination subtree differs from source")
        }
    }

    private suspend fun directoryManifest(root: File): Map<String, Pair<Boolean, Long>> {
        val result = linkedMapOf<String, Pair<Boolean, Long>>()
        val queue = ArrayDeque<Pair<File, String>>()
        queue.add(root to "")
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (directory, prefix) = queue.removeFirst()
            if (!directory.isDirectory) throw IOException("Expected directory during validation: ${directory.absolutePath}")
            val children = directoryEntries(directory)
                ?: throw IOException("Failed to list directory during validation: ${directory.absolutePath}")
            for (child in children) {
                currentCoroutineContext().ensureActive()
                val path = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                result[path] = child.isDirectory to if (child.isDirectory) 0L else child.length()
                if (child.isDirectory) queue.add(child to path)
            }
        }
        return result
    }

    suspend fun copyDirectoryRecursively(
        sourceDir: File,
        destDir: File,
        totalBytes: Long,
        onProgress: suspend (Long, String) -> Unit
    ) {
        if (!destDir.exists()) {
            destDir.mkdirs()
        }
        val destDirCanonical = destDir.canonicalPath
        val files = directoryEntries(sourceDir)
            ?: throw IOException("Failed to list source directory: ${sourceDir.absolutePath}")
        for (file in files) {
            if (!currentCoroutineContext().isActive) {
                throw CancellationException("Copy cancelled")
            }
            val destFile = File(destDir, file.name)
            val destFileCanonical = destFile.canonicalPath
            if (!destFileCanonical.startsWith(destDirCanonical + File.separator) && destFileCanonical != destDirCanonical) {
                throw SecurityException("Path traversal attempt detected during copy: ${file.name}")
            }
            if (file.isDirectory) {
                copyDirectoryRecursively(file, destFile, totalBytes, onProgress)
            } else {
                streamTransferHelper.copySingleFile(file, destFile, totalBytes, onProgress)
            }
        }
    }

    fun calculateTotalSize(dir: File): Long {
        var size = 0L
        val queue = ArrayDeque<File>()
        queue.add(dir)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val files = directoryEntries(current)
                ?: throw IOException("Failed to list source directory while calculating size: ${current.absolutePath}")
            for (file in files) {
                if (file.isDirectory) {
                    queue.add(file)
                } else {
                    size += file.length()
                }
            }
        }
        return size
    }

    fun isSymbolicLink(file: File): Boolean {
        return try {
            java.nio.file.Files.isSymbolicLink(file.toPath())
        } catch (_: Throwable) {
            try {
                val parent = file.parentFile ?: return false
                val canonicalParent = parent.canonicalFile
                val fileInCanonicalParent = File(canonicalParent, file.name)
                fileInCanonicalParent.canonicalPath != fileInCanonicalParent.absolutePath
            } catch (_: Throwable) {
                false
            }
        }
    }
}
