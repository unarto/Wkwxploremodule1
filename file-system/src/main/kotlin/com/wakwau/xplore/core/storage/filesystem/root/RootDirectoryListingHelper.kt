// [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/filesystem/root/RootDirectoryListingHelper.kt
// [Penjelasan]: Helper terisolasi untuk menangani pembacaan listing, metadata berkas root, pembuatan direktori, dan sanitasi path direktori root.
package com.wakwau.xplore.core.storage.filesystem.root

import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.wakwau.xplore.core.storage.constant.StorageConstants
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.FileMetadata
import com.wakwau.xplore.core.storage.model.FileType
import com.wakwau.xplore.core.storage.model.StorageLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.io.FileNotFoundException
import java.io.IOException

class RootDirectoryListingHelper(
    private val streamTransferHelper: RootStreamTransferHelper = RootStreamTransferHelper()
) {

    private val protectedPaths = setOf(
        "/storage",
        "/storage/emulated",
        "/system",
        "/vendor",
        "/apex",
        "/proc",
        "/sys",
        "/dev",
        "/etc",
        "/bin",
        "/sbin"
    )

    fun listFiles(
        location: StorageLocation,
        showHidden: Boolean
    ): List<FileItem> {
        val suDirectory = SuFile(location.path)
        if (!suDirectory.exists() || !suDirectory.isDirectory) {
            throw FileNotFoundException("Directory not found or is not a directory in root: ${location.path}")
        }

        val rawFiles = suDirectory.listFiles()
            ?: throw IOException("Failed to list root directory: ${location.path}")
        val filtered = if (!showHidden) {
            rawFiles.filter { !it.isHidden && !it.name.startsWith(".") }
        } else {
            rawFiles.toList()
        }

        return filtered.map { file ->
            val isDir = file.isDirectory
            val metadata = FileMetadata(
                size = if (isDir) 0L else file.length(),
                modifiedTime = file.lastModified(),
                createdTime = null,
                isReadable = file.canRead(),
                isWritable = file.canWrite(),
                isExecutable = file.canExecute(),
                isHidden = file.isHidden || file.name.startsWith(".")
            )
            val type = if (isDir) FileType.DIRECTORY else FileType.FILE
            FileItem(
                id = file.absolutePath,
                name = file.name.ifEmpty { file.absolutePath },
                location = StorageLocation(path = file.absolutePath, rootId = location.rootId),
                type = type,
                metadata = metadata
            )
        }.sortedWith(compareBy({ it.type != FileType.DIRECTORY }, { it.name.lowercase() }))
    }

    fun createDirectory(
        location: StorageLocation,
        name: String
    ): FileItem {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || trimmedName.contains("/") || trimmedName.contains("\\") || trimmedName == ".." || trimmedName == ".") {
            throw IllegalArgumentException("Invalid directory name: $name")
        }

        val parentPath = location.path.trimEnd('/')
        val targetPath = if (parentPath.isEmpty()) "/$trimmedName" else "$parentPath/$trimmedName"
        val targetFile = SuFile(targetPath)

        if (targetFile.exists()) {
            throw IOException("Directory already exists in root: $targetPath")
        }

        targetFile.mkdirs() || targetFile.mkdir() || Shell.cmd("mkdir -p ${escapeShellArg(targetPath)}").exec().isSuccess
        if (!targetFile.exists()) {
            throw IOException("Failed to create root directory: $targetPath")
        }

        val metadata = FileMetadata(
            size = 0L,
            modifiedTime = System.currentTimeMillis(),
            createdTime = null,
            isReadable = true,
            isWritable = true,
            isExecutable = true,
            isHidden = trimmedName.startsWith(".")
        )

        return FileItem(
            id = targetPath,
            name = trimmedName,
            location = StorageLocation(path = targetPath, rootId = location.rootId),
            type = FileType.DIRECTORY,
            metadata = metadata
        )
    }

    suspend fun deleteDirectoryRecursively(dir: SuFile) {
        val stack = ArrayDeque<SuFile>()
        stack.addLast(dir)
        val filesToDelete = ArrayDeque<SuFile>()

        while (stack.isNotEmpty()) {
            if (!currentCoroutineContext().isActive) throw CancellationException()
            val current = stack.removeLast()
            filesToDelete.addFirst(current)

            if (current.isDirectory) {
                val children = current.listFiles() ?: continue
                for (child in children) {
                    stack.addLast(child)
                }
            }
        }

        for (file in filesToDelete) {
            if (!currentCoroutineContext().isActive) throw CancellationException()
            if (!file.delete() && file.exists()) {
                throw IOException("Failed to delete root file: ${file.absolutePath}")
            }
        }
    }

    suspend fun copyDirectoryTransactionally(
        sourceDir: SuFile,
        destDir: SuFile,
        totalBytes: Long,
        afterPublish: suspend () -> Unit = {},
        onProgress: suspend (Long, String) -> Unit
    ) {
        val parent = destDir.parentFile ?: throw IOException("Destination has no parent: ${destDir.absolutePath}")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Failed to create root destination parent: ${parent.absolutePath}")
        val staging = SuFile(parent, ".${destDir.name}.wkw-${java.util.UUID.randomUUID()}.tmp")
        try {
            if (!staging.mkdir()) throw IOException("Failed to create root staging directory: ${staging.absolutePath}")
            copyDirectoryRecursively(sourceDir, staging, totalBytes, onProgress)
            currentCoroutineContext().ensureActive()
            publishDirectory(staging, destDir, afterPublish)
        } catch (error: Throwable) {
            try { withContext(NonCancellable) { if (staging.exists()) deleteDirectoryRecursively(staging) } } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
            throw error
        }
    }

    private suspend fun publishDirectory(staging: SuFile, destination: SuFile, afterPublish: suspend () -> Unit) {
        val backup = destination.takeIf { it.exists() }?.let {
            SuFile(destination.parentFile, ".${destination.name}.wkw-${java.util.UUID.randomUUID()}.bak").also { backup ->
                if (!destination.renameTo(backup)) throw IOException("Failed to preserve root destination: ${destination.absolutePath}")
            }
        }
        try {
            if (!staging.renameTo(destination)) throw IOException("Failed to publish root directory: ${destination.absolutePath}")
            afterPublish()
        } catch (error: Throwable) {
            if (destination.exists()) try { deleteDirectoryRecursively(destination) } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
            if (backup != null && !backup.renameTo(destination)) error.addSuppressed(IOException("Failed to restore root destination: ${destination.absolutePath}"))
            throw error
        }
        if (backup != null) withContext(NonCancellable) { deleteDirectoryRecursively(backup) }
    }

    suspend fun validateDirectoryTree(source: SuFile, destination: SuFile) {
        if (directoryManifest(source) != directoryManifest(destination)) {
            throw IOException("Root directory move validation failed: destination subtree differs from source")
        }
    }

    private suspend fun directoryManifest(root: SuFile): Map<String, Pair<Boolean, Long>> {
        val result = linkedMapOf<String, Pair<Boolean, Long>>()
        val queue = ArrayDeque<Pair<SuFile, String>>()
        queue.add(root to "")
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (directory, prefix) = queue.removeFirst()
            if (!directory.isDirectory) throw IOException("Expected root directory during validation: ${directory.absolutePath}")
            val children = directory.listFiles()
                ?: throw IOException("Failed to list root directory during validation: ${directory.absolutePath}")
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
        sourceDir: SuFile,
        destDir: SuFile,
        totalBytes: Long,
        onProgress: suspend (Long, String) -> Unit
    ) {
        if (!destDir.exists()) {
            destDir.mkdirs()
        }
        val children = sourceDir.listFiles()
            ?: throw IOException("Failed to list root source directory: ${sourceDir.absolutePath}")
        for (child in children) {
            if (!currentCoroutineContext().isActive) {
                throw CancellationException("Root copy operation cancelled")
            }
            val targetChild = SuFile(destDir, child.name)
            if (child.isDirectory) {
                copyDirectoryRecursively(child, targetChild, totalBytes, onProgress)
            } else {
                streamTransferHelper.copySingleFile(child, targetChild, totalBytes, onProgress)
            }
        }
    }

    fun calculateTotalSize(dir: SuFile): Long {
        var size = 0L
        val queue = ArrayDeque<SuFile>()
        queue.add(dir)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val children = current.listFiles()
                ?: throw IOException("Failed to list root source directory while calculating size: ${current.absolutePath}")
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

    fun isProtectedRootPath(path: String): Boolean {
        val clean = path.trim().trimEnd('/')
        if (clean.isEmpty() || clean == "/" || clean == StorageConstants.ROOT_PATH) return true
        val primaryStorage = StorageConstants.DEFAULT_PRIMARY_STORAGE_PATH.trimEnd('/')
        if (clean.equals(primaryStorage, ignoreCase = true)) return true
        return protectedPaths.contains(clean.lowercase())
    }

    fun escapeShellArg(arg: String): String {
        return "'" + arg.replace("'", "'\\''") + "'"
    }
}
