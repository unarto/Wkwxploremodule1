// [Modul: :file-system] [Jalur Class]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/filesystem/local/LocalDirectoryOperationHelper.kt
// [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API
package com.wakwau.xplore.core.storage.filesystem.local

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import com.wakwau.xplore.core.storage.permission.StoragePermissionChecker

class LocalDirectoryOperationHelper(
    private val streamTransferHelper: LocalStreamTransferHelper = LocalStreamTransferHelper(),
    private val storagePermissionChecker: StoragePermissionChecker? = null
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
        val files = sourceDir.listFiles() ?: return
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
            val files = current.listFiles() ?: continue
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
