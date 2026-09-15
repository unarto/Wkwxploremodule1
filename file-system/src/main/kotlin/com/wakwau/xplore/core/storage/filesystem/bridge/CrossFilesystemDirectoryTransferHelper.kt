// [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/filesystem/bridge/CrossFilesystemDirectoryTransferHelper.kt
// [Penjelasan]: Helper terisolasi untuk menangani resolusi pembuatan direktori tujuan, listing anak direktori sumber, validasi integritas transfer move, dan rollback jika terjadi kegagalan/pembatalan.
package com.wakwau.xplore.core.storage.filesystem.bridge

import kotlinx.coroutines.CancellationException
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.topjohnwu.superuser.io.SuFile
import com.wakwau.xplore.core.storage.constant.StorageConstants
import com.wakwau.xplore.core.storage.filesystem.LocalFileSystemContract
import com.wakwau.xplore.core.storage.filesystem.RootFileSystemContract
import com.wakwau.xplore.core.storage.filesystem.SafFileSystemContract
import com.wakwau.xplore.core.storage.filesystem.ShizukuFileSystemContract
import com.wakwau.xplore.core.storage.filesystem.StorageBackendType
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.shizuku.IPrivilegedFileService
import com.wakwau.xplore.core.storage.shizuku.ShizukuHelper
import com.wakwau.xplore.core.storage.shizuku.ShizukuIpcConstants
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class CrossFilesystemDirectoryTransferHelper(
    private val context: Context,
    private val localFileSystem: LocalFileSystemContract,
    private val safFileSystem: SafFileSystemContract,
    private val safShizukuFileSystem: ShizukuFileSystemContract,
    private val rootFileSystem: RootFileSystemContract
) {
    private val manifestValidator = DirectoryManifestValidator()

    data class DirectoryStaging(
        val staging: StorageLocation,
        val target: StorageLocation,
        val type: StorageBackendType
    )

    suspend fun createDirectoryStaging(
        sourceName: String,
        destination: StorageLocation,
        destType: StorageBackendType
    ): DirectoryStaging {
        val suffix = java.util.UUID.randomUUID().toString()
        return when (destType) {
            StorageBackendType.LOCAL -> {
                val target = File(destination.path)
                val staging = File(target.parentFile, ".${target.name}.wkw-$suffix.tmp")
                if (!staging.mkdir()) throw IOException("Failed to create local directory staging: ${staging.absolutePath}")
                DirectoryStaging(StorageLocation(staging.absolutePath, destination.rootId), StorageLocation(target.absolutePath, destination.rootId), destType)
            }
            StorageBackendType.SAF -> {
                val cleanDestination = destination.path.substringBefore('#')
                val finalName = Uri.parse(destination.path).fragment ?: sourceName
                val parent = resolveSafDocument(Uri.parse(cleanDestination))
                    ?: throw FileNotFoundException("Destination SAF folder not found: ${destination.path}")
                if (!parent.isDirectory) throw IOException("SAF directory copy requires a destination directory: ${destination.path}")
                val stagingName = ".$finalName.wkw-$suffix.tmp"
                val staging = parent.createDirectory(stagingName)
                    ?: throw IOException("Failed to create SAF directory staging: $stagingName")
                val target = parent.findFile(finalName)
                DirectoryStaging(
                    StorageLocation(staging.uri.toString(), destination.rootId),
                    StorageLocation(target?.uri?.toString() ?: "${parent.uri}#$finalName", destination.rootId),
                    destType
                )
            }
            StorageBackendType.SHIZUKU -> {
                val service = getShizukuService()
                val target = destination.path
                val staging = "$target.wkw-$suffix.tmp"
                if (!service.createDirectory(staging)) throw IOException("Failed to create Shizuku directory staging: $staging")
                DirectoryStaging(StorageLocation(staging, destination.rootId), StorageLocation(target, destination.rootId), destType)
            }
            StorageBackendType.ROOT -> {
                val target = destination.path
                val staging = SuFile("$target.wkw-$suffix.tmp")
                if (!staging.mkdir()) throw IOException("Failed to create root directory staging: ${staging.absolutePath}")
                DirectoryStaging(StorageLocation(staging.absolutePath, destination.rootId), StorageLocation(target, destination.rootId), destType)
            }
        }
    }

    suspend fun publishDirectoryStaging(
        staging: DirectoryStaging,
        sourceName: String,
        destination: StorageLocation,
        afterPublish: suspend (StorageLocation) -> Unit = {}
    ) {
        when (staging.type) {
            StorageBackendType.LOCAL -> publishLocalDirectory(File(staging.staging.path), File(staging.target.path)) {
                afterPublish(staging.target)
            }
            StorageBackendType.SAF -> {
                val finalName = Uri.parse(destination.path).fragment ?: sourceName
                val parent = resolveSafDocument(Uri.parse(destination.path.substringBefore('#')))
                    ?: throw FileNotFoundException("Destination SAF folder not found: ${destination.path}")
                val staged = resolveSafDocument(Uri.parse(staging.staging.path))
                    ?: throw FileNotFoundException("SAF staging directory disappeared: ${staging.staging.path}")
                publishSafDirectory(parent, staged, finalName) { published ->
                    afterPublish(StorageLocation(published.uri.toString(), staging.target.rootId))
                }
            }
            StorageBackendType.SHIZUKU -> publishShizukuDirectory(getShizukuService(), staging.staging.path, staging.target.path) {
                afterPublish(staging.target)
            }
            StorageBackendType.ROOT -> publishRootDirectory(SuFile(staging.staging.path), SuFile(staging.target.path), staging.target.rootId) {
                afterPublish(staging.target)
            }
        }
    }

    suspend fun cleanupDirectoryStaging(staging: DirectoryStaging) {
        when (staging.type) {
            StorageBackendType.LOCAL -> File(staging.staging.path).let { if (it.exists() && !it.deleteRecursively()) throw IOException("Failed to clean local directory staging: ${it.absolutePath}") }
            StorageBackendType.SAF -> resolveSafDocument(Uri.parse(staging.staging.path))?.let { if (it.exists() && !it.delete()) throw IOException("Failed to clean SAF directory staging: ${it.uri}") }
            StorageBackendType.SHIZUKU -> getShizukuService().let { if (it.exists(staging.staging.path) && !it.delete(staging.staging.path)) throw IOException("Failed to clean Shizuku directory staging: ${staging.staging.path}") }
            StorageBackendType.ROOT -> if (SuFile(staging.staging.path).exists()) rootFileSystem.delete(staging.staging)
        }
    }


    suspend fun createDestDirectory(
        dirName: String,
        destination: StorageLocation,
        destType: StorageBackendType
    ): StorageLocation {
        return when (destType) {
            StorageBackendType.LOCAL -> {
                val destParent = File(destination.path)
                val targetDir = if (destParent.isDirectory) File(destParent, dirName) else destParent
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
                StorageLocation(targetDir.absolutePath, destination.rootId)
            }
            StorageBackendType.SAF -> {
                val parentDoc = resolveSafDocument(Uri.parse(destination.path))
                    ?: throw FileNotFoundException("Destination SAF parent folder not found: ${destination.path}")
                val targetDoc = if (parentDoc.isDirectory) {
                    parentDoc.findFile(dirName)?.takeIf { it.isDirectory }
                        ?: parentDoc.createDirectory(dirName)
                        ?: throw IOException("Failed to create SAF subfolder: $dirName")
                } else {
                    parentDoc
                }
                StorageLocation(targetDoc.uri.toString(), destination.rootId)
            }
            StorageBackendType.SHIZUKU -> {
                val service = getShizukuService()
                val targetPath = resolveShizukuDestFilePath(dirName, destination.path)
                if (!service.exists(targetPath)) {
                    service.createDirectory(targetPath)
                }
                StorageLocation(targetPath, destination.rootId)
            }
            StorageBackendType.ROOT -> {
                val targetPath = resolveRootDestFilePath(dirName, destination.path)
                val dir = SuFile(targetPath)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                StorageLocation(targetPath, destination.rootId)
            }
        }
    }

    suspend fun isSourceDirectory(source: StorageLocation, sourceType: StorageBackendType): Boolean = when (sourceType) {
        StorageBackendType.LOCAL -> File(source.path).isDirectory
        StorageBackendType.SAF -> {
            val document = resolveSafDocument(Uri.parse(source.path))
                ?: throw FileNotFoundException("Source SAF document not found: ${source.path}")
            try {
                document.isDirectory
            } catch (error: SecurityException) {
                throw error
            } catch (error: Exception) {
                throw IOException("Failed to inspect SAF source: ${source.path}", error)
            }
        }
        StorageBackendType.SHIZUKU -> getShizukuService().isDirectory(source.path)
        StorageBackendType.ROOT -> SuFile(source.path).isDirectory
    }

    suspend fun calculateTotalSize(source: StorageLocation, sourceType: StorageBackendType): Long {
        if (!isSourceDirectory(source, sourceType)) {
            return when (sourceType) {
                StorageBackendType.LOCAL -> File(source.path).length()
                StorageBackendType.SAF -> resolveSafDocument(Uri.parse(source.path))?.length() ?: 0L
                StorageBackendType.SHIZUKU -> getShizukuService().length(source.path)
                StorageBackendType.ROOT -> SuFile(source.path).length()
            }
        }

        return listSourceChildren(source, sourceType).sumOf { child ->
            calculateTotalSize(child, sourceType)
        }
    }

    fun getSourceName(source: StorageLocation, sourceType: StorageBackendType): String = when (sourceType) {
        StorageBackendType.LOCAL -> File(source.path).name
        StorageBackendType.SAF -> resolveSafDocument(Uri.parse(source.path))?.name ?: StorageConstants.DEFAULT_UNKNOWN_FILE_NAME
        StorageBackendType.SHIZUKU -> source.path.trimEnd('/').substringAfterLast('/')
        StorageBackendType.ROOT -> SuFile(source.path).name.ifEmpty { source.path.trimEnd('/').substringAfterLast('/') }
    }

    suspend fun listSourceChildren(source: StorageLocation, sourceType: StorageBackendType): List<StorageLocation> = when (sourceType) {
        StorageBackendType.LOCAL -> {
            val children = File(source.path).listFiles()
                ?: throw IOException("Failed to list local source directory: ${source.path}")
            children.map { StorageLocation(it.absolutePath, source.rootId) }
        }
        StorageBackendType.SAF -> {
            val doc = resolveSafDocument(Uri.parse(source.path))
                ?: throw FileNotFoundException("Source SAF directory not found: ${source.path}")
            val children = try {
                doc.listFiles()
            } catch (error: SecurityException) {
                throw error
            } catch (error: Exception) {
                throw IOException("Failed to list SAF source directory: ${source.path}", error)
            }
            children.map { StorageLocation(it.uri.toString(), source.rootId) }
        }
        StorageBackendType.SHIZUKU -> getShizukuService().listDirectory(source.path).map { bundle ->
            val path = bundle.getString(ShizukuIpcConstants.KEY_PATH)
                ?: throw IOException("Invalid Shizuku directory entry without a path: ${source.path}")
            val name = bundle.getString(ShizukuIpcConstants.KEY_NAME)
                ?: throw IOException("Invalid Shizuku directory entry without a name: ${source.path}")
            if (name == "." || name == "..") null else StorageLocation(path, source.rootId)
        }.filterNotNull()
        StorageBackendType.ROOT -> {
            val children = SuFile(source.path).listFiles()
                ?: throw IOException("Failed to list root source directory: ${source.path}")
            children.map { StorageLocation(it.absolutePath, source.rootId) }
        }
    }

    suspend fun validateTransferComplete(
        source: StorageLocation,
        destination: StorageLocation,
        sourceType: StorageBackendType,
        destType: StorageBackendType,
        isSourceDir: Boolean
    ) {
        val sourceName = getSourceName(source, sourceType)
        val destinationTarget = resolveDestinationTarget(sourceName, destination, destType)
        if (!isSourceDir) {
            val sourceSize = getFileSize(source, sourceType)
            val destSize = getFileSize(destinationTarget, destType)
            if (destSize != sourceSize) {
                rollbackDestination(source, destination, sourceType, destType)
                throw IOException("Cross-filesystem move validation failed: destination file incomplete or size mismatch")
            }
        } else {
            val sourceManifest = collectDirectoryManifest(source, sourceType)
            val destinationManifest = collectDirectoryManifest(destinationTarget, destType)
            manifestValidator.validate(sourceManifest, destinationManifest)
        }
    }

    suspend fun isDestinationDirectory(destination: StorageLocation, destType: StorageBackendType): Boolean = when (destType) {
        StorageBackendType.LOCAL -> File(destination.path).isDirectory
        StorageBackendType.SAF -> resolveSafDocument(Uri.parse(destination.path))?.isDirectory == true
        StorageBackendType.SHIZUKU -> getShizukuService().let { it.exists(destination.path) && it.isDirectory(destination.path) }
        StorageBackendType.ROOT -> SuFile(destination.path).isDirectory
    }

    private suspend fun collectDirectoryManifest(
        root: StorageLocation,
        type: StorageBackendType
    ): List<DirectoryManifestEntry> {
        val entries = mutableListOf<DirectoryManifestEntry>()
        val queue = ArrayDeque<Pair<StorageLocation, String>>()
        queue.add(root to "")
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (location, relativePath) = queue.removeFirst()
            if (!isSourceDirectory(location, type)) {
                throw IOException("Move validation failed: expected directory at ${location.path}")
            }
            val children = listSourceChildren(location, type)
            for (child in children) {
                currentCoroutineContext().ensureActive()
                val name = getSourceName(child, type)
                val childRelativePath = if (relativePath.isEmpty()) name else "$relativePath/$name"
                val isDirectory = isSourceDirectory(child, type)
                entries += DirectoryManifestEntry(
                    relativePath = childRelativePath,
                    isDirectory = isDirectory,
                    size = if (isDirectory) 0L else getFileSize(child, type)
                )
                if (isDirectory) queue.add(child to childRelativePath)
            }
        }
        return entries
    }

    private suspend fun resolveDestinationTarget(
        sourceName: String,
        destination: StorageLocation,
        destType: StorageBackendType
    ): StorageLocation = when (destType) {
        StorageBackendType.LOCAL -> StorageLocation(destination.path, destination.rootId)
        StorageBackendType.SAF -> {
            val finalName = Uri.parse(destination.path).fragment ?: sourceName
            val destinationDocument = resolveSafDocument(Uri.parse(destination.path.substringBefore('#')))
                ?: throw FileNotFoundException("Destination SAF document not found: ${destination.path}")
            val target = if (destinationDocument.isDirectory) destinationDocument.findFile(finalName) else destinationDocument
            target?.let { StorageLocation(it.uri.toString(), destination.rootId) }
                ?: throw FileNotFoundException("Destination SAF target not found: $finalName")
        }
        StorageBackendType.SHIZUKU -> StorageLocation(destination.path, destination.rootId)
        StorageBackendType.ROOT -> StorageLocation(destination.path, destination.rootId)
    }

    private suspend fun publishLocalDirectory(staged: File, target: File, afterPublish: suspend () -> Unit) {
        val backup = target.takeIf { it.exists() }?.let {
            File(target.parentFile, ".${target.name}.wkw-${java.util.UUID.randomUUID()}.bak").also { backup ->
                if (!target.renameTo(backup)) throw IOException("Failed to preserve local destination: ${target.absolutePath}")
            }
        }
        try {
            if (!staged.renameTo(target)) throw IOException("Failed to publish local directory: ${target.absolutePath}")
            afterPublish()
        } catch (error: Throwable) {
            if (target.exists()) target.deleteRecursively()
            if (backup != null && !backup.renameTo(target)) error.addSuppressed(IOException("Failed to restore local destination: ${target.absolutePath}"))
            throw error
        }
        if (backup != null && !backup.deleteRecursively() && backup.exists()) throw IOException("Move committed but failed to remove local directory backup: ${backup.absolutePath}")
    }

    private suspend fun publishSafDirectory(parent: DocumentFile, staged: DocumentFile, name: String, afterPublish: suspend (DocumentFile) -> Unit) {
        val existing = parent.findFile(name)
        val backupName = existing?.let { ".$name.wkw-${java.util.UUID.randomUUID()}.bak".also { backup ->
            if (!existing.renameTo(backup)) throw IOException("Failed to preserve SAF destination: $name")
        } }
        try {
            if (!staged.renameTo(name)) throw IOException("Failed to publish SAF directory: $name")
            afterPublish(parent.findFile(name) ?: throw IOException("Published SAF directory cannot be resolved: $name"))
        } catch (error: Throwable) {
            parent.findFile(name)?.let { if (!it.delete()) error.addSuppressed(IOException("Failed to remove failed SAF publish: $name")) }
            if (existing != null && !existing.renameTo(name)) error.addSuppressed(IOException("Failed to restore SAF destination: $name"))
            throw error
        }
        if (existing != null && !existing.delete() && existing.exists()) throw IOException("Move committed but failed to remove SAF directory backup: $backupName")
    }

    private suspend fun publishShizukuDirectory(service: IPrivilegedFileService, staged: String, target: String, afterPublish: suspend () -> Unit) {
        val backup = if (service.exists(target)) "$target.wkw-${java.util.UUID.randomUUID()}.bak".also {
            if (!service.rename(target, it)) throw IOException("Failed to preserve Shizuku destination: $target")
        } else null
        try {
            if (!service.rename(staged, target)) throw IOException("Failed to publish Shizuku directory: $target")
            afterPublish()
        } catch (error: Throwable) {
            if (service.exists(target) && !service.delete(target)) error.addSuppressed(IOException("Failed to remove failed Shizuku publish: $target"))
            if (backup != null && !service.rename(backup, target)) error.addSuppressed(IOException("Failed to restore Shizuku destination: $target"))
            throw error
        }
        if (backup != null && !service.delete(backup) && service.exists(backup)) throw IOException("Move committed but failed to remove Shizuku directory backup: $backup")
    }

    private suspend fun publishRootDirectory(staged: SuFile, target: SuFile, rootId: String, afterPublish: suspend () -> Unit) {
        val backup = target.takeIf { it.exists() }?.let {
            SuFile("${target.absolutePath}.wkw-${java.util.UUID.randomUUID()}.bak").also { backup ->
                if (!target.renameTo(backup)) throw IOException("Failed to preserve root destination: ${target.absolutePath}")
            }
        }
        try {
            if (!staged.renameTo(target)) throw IOException("Failed to publish root directory: ${target.absolutePath}")
            afterPublish()
        } catch (error: Throwable) {
            try { if (target.exists()) rootFileSystem.delete(StorageLocation(target.absolutePath, rootId)) } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
            if (backup != null && !backup.renameTo(target)) error.addSuppressed(IOException("Failed to restore root destination: ${target.absolutePath}"))
            throw error
        }
        if (backup != null) withContext(NonCancellable) {
            rootFileSystem.delete(StorageLocation(backup.absolutePath, rootId))
        }
    }


    private suspend fun getFileSize(location: StorageLocation, type: StorageBackendType): Long = when (type) {
        StorageBackendType.LOCAL -> File(location.path).takeIf { it.exists() && it.isFile }?.length()
            ?: throw FileNotFoundException("File not found during validation: ${location.path}")
        StorageBackendType.SAF -> resolveSafDocument(Uri.parse(location.path))?.takeIf { it.exists() && it.isFile }?.length()
            ?: throw FileNotFoundException("SAF file not found during validation: ${location.path}")
        StorageBackendType.SHIZUKU -> getShizukuService().let { service ->
            if (!service.exists(location.path) || service.isDirectory(location.path)) throw FileNotFoundException("Shizuku file not found during validation: ${location.path}")
            service.length(location.path)
        }
        StorageBackendType.ROOT -> SuFile(location.path).takeIf { it.exists() && it.isFile }?.length()
            ?: throw FileNotFoundException("Root file not found during validation: ${location.path}")
    }

    suspend fun rollbackDestination(
        source: StorageLocation,
        destination: StorageLocation,
        sourceType: StorageBackendType,
        destType: StorageBackendType
    ) {
        try {
            val sourceName = getSourceName(source, sourceType)
            val targetLocation = when (destType) {
                StorageBackendType.LOCAL -> StorageLocation(resolveLocalDestFile(sourceName, destination.path).absolutePath, destination.rootId)
                StorageBackendType.SAF -> {
                    val doc = resolveSafDocument(Uri.parse(destination.path))
                    val target = if (doc?.isDirectory == true) doc.findFile(sourceName) else doc
                    target?.let { StorageLocation(it.uri.toString(), destination.rootId) }
                }
                StorageBackendType.SHIZUKU -> StorageLocation(resolveShizukuDestFilePath(sourceName, destination.path), destination.rootId)
                StorageBackendType.ROOT -> StorageLocation(resolveRootDestFilePath(sourceName, destination.path), destination.rootId)
            }
            if (targetLocation != null) {
                deleteSource(targetLocation, destType)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.w("FileSystem", "Failed to clean partial file", e)
        }
    }

    suspend fun deleteSource(source: StorageLocation, sourceType: StorageBackendType) = when (sourceType) {
        StorageBackendType.LOCAL -> localFileSystem.delete(source)
        StorageBackendType.SAF -> safFileSystem.delete(source)
        StorageBackendType.SHIZUKU -> safShizukuFileSystem.delete(source)
        StorageBackendType.ROOT -> rootFileSystem.delete(source)
    }

    fun resolveLocalDestFile(sourceName: String, destPath: String): File =
        File(destPath).let { if (it.isDirectory) File(it, sourceName) else it }

    fun resolveShizukuDestFilePath(sourceName: String, destPath: String): String =
        if (destPath.endsWith("/")) "$destPath$sourceName" else "$destPath/$sourceName"

    fun resolveRootDestFilePath(sourceName: String, destPath: String): String =
        if (destPath.endsWith("/")) "$destPath$sourceName" else "$destPath/$sourceName"

    fun resolveSafDocument(uri: Uri): DocumentFile? = try {
        DocumentFile.fromTreeUri(context, uri) ?: DocumentFile.fromSingleUri(context, uri)
    } catch (_: Exception) { null }

    suspend fun getShizukuService(): IPrivilegedFileService =
        ShizukuHelper.getPrivilegedService(context.packageName) ?: throw FileNotFoundException("Root/Shizuku service not available")
}
