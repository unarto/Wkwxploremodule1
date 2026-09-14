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

class CrossFilesystemDirectoryTransferHelper(
    private val context: Context,
    private val localFileSystem: LocalFileSystemContract,
    private val safFileSystem: SafFileSystemContract,
    private val safShizukuFileSystem: ShizukuFileSystemContract,
    private val rootFileSystem: RootFileSystemContract
) {

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
        StorageBackendType.SAF -> resolveSafDocument(Uri.parse(source.path))?.let { try { it.isDirectory } catch (_: Exception) { false } } ?: false
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
        StorageBackendType.LOCAL -> (File(source.path).listFiles() ?: emptyArray()).map { StorageLocation(it.absolutePath, source.rootId) }
        StorageBackendType.SAF -> {
            val doc = resolveSafDocument(Uri.parse(source.path))
            val children = if (doc != null) (try { doc.listFiles() } catch (_: Exception) { emptyArray() }) else emptyArray()
            children.map { StorageLocation(it.uri.toString(), source.rootId) }
        }
        StorageBackendType.SHIZUKU -> getShizukuService().listDirectory(source.path).mapNotNull { bundle ->
            val path = bundle.getString(ShizukuIpcConstants.KEY_PATH)
            val name = bundle.getString(ShizukuIpcConstants.KEY_NAME)
            if (path != null && name != "." && name != "..") StorageLocation(path, source.rootId) else null
        }
        StorageBackendType.ROOT -> (SuFile(source.path).listFiles() ?: emptyArray()).map { StorageLocation(it.absolutePath, source.rootId) }
    }

    suspend fun validateTransferComplete(
        source: StorageLocation,
        destination: StorageLocation,
        sourceType: StorageBackendType,
        destType: StorageBackendType,
        isSourceDir: Boolean,
        expectedSize: Long
    ) {
        val sourceName = getSourceName(source, sourceType)
        val destSize = getDestFileSize(sourceName, destination, destType)
        if (!isSourceDir) {
            if (destSize < 0 || (expectedSize > 0 && destSize != expectedSize)) {
                rollbackDestination(source, destination, sourceType, destType)
                throw IOException("Cross-filesystem move validation failed: destination file incomplete or size mismatch")
            }
        } else if (destSize < 0) {
            rollbackDestination(source, destination, sourceType, destType)
            throw IOException("Cross-filesystem move validation failed: destination directory not found")
        }
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

    private suspend fun getDestFileSize(sourceFileName: String, destination: StorageLocation, destType: StorageBackendType): Long = when (destType) {
        StorageBackendType.LOCAL -> {
            val file = resolveLocalDestFile(sourceFileName, destination.path)
            if (file.exists()) (if (file.isDirectory) 0L else file.length()) else -1L
        }
        StorageBackendType.SAF -> {
            val doc = resolveSafDocument(Uri.parse(destination.path))
            if (doc != null && doc.exists()) {
                val target = if (doc.isDirectory) doc.findFile(sourceFileName) else doc
                if (target != null && target.exists()) (if (target.isDirectory) 0L else target.length()) else -1L
            } else -1L
        }
        StorageBackendType.SHIZUKU -> {
            val service = getShizukuService()
            val path = resolveShizukuDestFilePath(sourceFileName, destination.path)
            if (service.exists(path)) (if (service.isDirectory(path)) 0L else service.length(path)) else -1L
        }
        StorageBackendType.ROOT -> {
            val file = SuFile(resolveRootDestFilePath(sourceFileName, destination.path))
            if (file.exists()) (if (file.isDirectory) 0L else file.length()) else -1L
        }
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
