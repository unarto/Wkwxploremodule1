// [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/filesystem/saf/SafFileSystem.kt
// [Penjelasan]: Implementasi fasad sistem berkas Storage Access Framework (SAF) yang mendelegasikan resolusi DocumentFile ke SafUriResolver dan transfer I/O ke SafStreamTransferHelper (< 250 LOC).
package com.wakwau.xplore.core.storage.filesystem.saf

import android.content.Context
import android.net.Uri
import com.wakwau.xplore.core.storage.constant.StorageConstants
import com.wakwau.xplore.core.storage.filesystem.SafFileSystemContract
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.FileMetadata
import com.wakwau.xplore.core.storage.model.FileType
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.FileOperationProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.FileNotFoundException
import java.io.IOException

class SafFileSystem(
    private val context: Context,
    private val uriResolver: SafUriResolver = SafUriResolver(context),
    private val streamTransferHelper: SafStreamTransferHelper = SafStreamTransferHelper(context)
) : SafFileSystemContract {
    private val moveTargetValidator = SafMoveTargetValidator()

    override suspend fun listFiles(location: StorageLocation, showHidden: Boolean): List<FileItem> {
        val uri = Uri.parse(location.path)
        val documentFile = uriResolver.resolveTreeDocumentFile(uri)
            ?: uriResolver.resolveDocumentFile(uri)
            ?: throw FileNotFoundException("Invalid SAF URI or not a tree URI: ${location.path}")

        if (!documentFile.exists() || !documentFile.isDirectory) {
            throw FileNotFoundException("Directory not found or is not a directory: ${location.path}")
        }

        val files = try {
            documentFile.listFiles()
        } catch (error: SecurityException) {
            throw error
        } catch (error: Exception) {
            throw IOException("Failed to list SAF directory: ${location.path}", error)
        }

        return files.filter { file ->
            if (!showHidden) file.name?.startsWith(".") != true else true
        }.map { file ->
            val type = if (file.isDirectory) FileType.DIRECTORY else FileType.FILE
            val itemUri = file.uri.toString()
            val metadata = FileMetadata(
                size = if (file.isFile) file.length() else 0L,
                modifiedTime = file.lastModified(),
                createdTime = null,
                isReadable = file.canRead(),
                isWritable = file.canWrite(),
                isExecutable = false,
                isHidden = file.name?.startsWith(".") == true
            )
            FileItem(
                id = itemUri,
                name = file.name ?: StorageConstants.DEFAULT_UNKNOWN_FILE_NAME,
                location = StorageLocation(itemUri, location.rootId),
                type = type,
                metadata = metadata
            )
        }.sortedWith(compareBy({ it.type != FileType.DIRECTORY }, { it.name.lowercase() }))
    }

    override suspend fun createDirectory(location: StorageLocation, name: String): FileItem {
        val trimmedName = name.trim()
        val actualParentDoc = uriResolver.resolveParentDirectory(Uri.parse(location.path))
            ?: throw FileNotFoundException("Invalid SAF parent URI: ${location.path}")

        if (!actualParentDoc.exists() || !actualParentDoc.isDirectory) {
            throw FileNotFoundException("Parent directory not found in SAF: ${location.path}")
        }

        val createdDir = try {
            actualParentDoc.createDirectory(trimmedName)
        } catch (e: IllegalArgumentException) {
            throw IOException("Invalid argument when creating directory via SAF: $trimmedName", e)
        } ?: throw IOException("Failed to create directory via SAF: $trimmedName")

        val createdUri = createdDir.uri.toString()
        val metadata = FileMetadata(
            size = 0L,
            modifiedTime = createdDir.lastModified(),
            createdTime = null,
            isReadable = createdDir.canRead(),
            isWritable = createdDir.canWrite(),
            isExecutable = false,
            isHidden = trimmedName.startsWith(".")
        )

        return FileItem(
            id = createdUri,
            name = createdDir.name ?: trimmedName,
            location = StorageLocation(path = createdUri, rootId = location.rootId),
            type = FileType.DIRECTORY,
            metadata = metadata
        )
    }

    override suspend fun delete(location: StorageLocation) {
        val pathClean = location.path.trim().trimEnd('/')
        if (pathClean.isEmpty() || pathClean == "/" || pathClean.equals("/storage", ignoreCase = true) || pathClean.equals("/storage/emulated", ignoreCase = true)) {
            throw SecurityException("Cannot delete root or protected storage path: ${location.path}")
        }
        val documentFile = uriResolver.resolveDocumentFile(Uri.parse(location.path))
            ?: throw FileNotFoundException("Invalid SAF URI: ${location.path}")
        if (!documentFile.exists()) {
            throw FileNotFoundException("SAF file not found: ${location.path}")
        }
        val isDeleted = try {
            documentFile.delete()
        } catch (e: IllegalArgumentException) {
            throw IOException("Invalid argument when deleting SAF file: ${location.path}", e)
        }
        if (!isDeleted) throw IOException("Failed to delete SAF file: ${location.path}")
    }

    override suspend fun rename(location: StorageLocation, newName: String): FileItem {
        val documentFile = uriResolver.resolveDocumentFile(Uri.parse(location.path))
            ?: throw FileNotFoundException("Invalid SAF URI: ${location.path}")
        if (!documentFile.exists()) {
            throw FileNotFoundException("SAF file not found: ${location.path}")
        }
        val isRenamed = try {
            documentFile.renameTo(newName)
        } catch (e: IllegalArgumentException) {
            throw IOException("Invalid argument when renaming SAF file to: $newName", e)
        }
        if (!isRenamed) throw IOException("Failed to rename SAF file to: $newName")

        val updatedUri = documentFile.uri.toString()
        val isDir = documentFile.isDirectory
        val metadata = FileMetadata(
            size = if (isDir) 0L else documentFile.length(),
            modifiedTime = documentFile.lastModified(),
            createdTime = null,
            isReadable = documentFile.canRead(),
            isWritable = documentFile.canWrite(),
            isExecutable = false,
            isHidden = newName.startsWith(".")
        )

        return FileItem(
            id = updatedUri,
            name = documentFile.name ?: newName,
            location = StorageLocation(path = updatedUri, rootId = location.rootId),
            type = if (isDir) FileType.DIRECTORY else FileType.FILE,
            metadata = metadata
        )
    }

    override fun copy(source: StorageLocation, destination: StorageLocation): Flow<FileOperationProgress> = flow {
        val destUri = Uri.parse(destination.path)
        val targetName = destUri.fragment
        val cleanDestPath = destination.path.substringBefore("#")
        val cleanDestUri = Uri.parse(cleanDestPath)

        val sourceDoc = uriResolver.resolveDocumentFile(Uri.parse(source.path))
            ?: throw FileNotFoundException("Source SAF file not found: ${source.path}")
        val destDoc = uriResolver.resolveTreeDocumentFile(cleanDestUri)
            ?: uriResolver.resolveDocumentFile(cleanDestUri)
            ?: throw FileNotFoundException("Destination SAF folder not found: $cleanDestPath")

        if (source.path == cleanDestPath || sourceDoc.uri == destDoc.uri) {
            throw IllegalArgumentException("Source and destination are the same")
        }

        val isSourceDir = sourceDoc.isDirectory
        if (isSourceDir && cleanDestPath.startsWith(source.path)) {
            throw IllegalArgumentException("Cannot copy a directory into itself")
        }

        var totalCopied = 0L
        val totalBytes = if (isSourceDir) streamTransferHelper.calculateTotalSize(sourceDoc) else sourceDoc.length()

        if (totalBytes == 0L) {
            emit(FileOperationProgress(0L, 0L, targetName ?: sourceDoc.name ?: StorageConstants.DEFAULT_UNKNOWN_FILE_NAME))
        }

        if (isSourceDir) {
            streamTransferHelper.copyDirectoryTransactionally(sourceDoc, destDoc, targetName, totalBytes) { incrementalBytes, fileName ->
                totalCopied += incrementalBytes
                emit(FileOperationProgress(totalCopied, totalBytes, fileName))
            }
        } else {
            streamTransferHelper.copySingleFile(sourceDoc, destDoc, targetName, totalBytes) { incrementalBytes, fileName ->
                totalCopied += incrementalBytes
                emit(FileOperationProgress(totalCopied, totalBytes, fileName))
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun move(source: StorageLocation, destination: StorageLocation): Flow<FileOperationProgress> = flow {
        val sourceDoc = uriResolver.resolveDocumentFile(Uri.parse(source.path))
            ?: throw FileNotFoundException("Source not found: ${source.path}")
        val sourceSize = if (sourceDoc.isFile) sourceDoc.length() else 0L
        val isSourceDir = sourceDoc.isDirectory
        val targetName = Uri.parse(destination.path).fragment
            ?: sourceDoc.name
            ?: StorageConstants.DEFAULT_UNKNOWN_FILE_NAME
        val cleanDestPath = destination.path.substringBefore("#")

        if (isSourceDir) {
            val destinationParent = uriResolver.resolveTreeDocumentFile(Uri.parse(cleanDestPath))
                ?: uriResolver.resolveDocumentFile(Uri.parse(cleanDestPath))
                ?: throw FileNotFoundException("Move failed: SAF destination cannot be resolved ($cleanDestPath)")
            val totalBytes = streamTransferHelper.calculateTotalSize(sourceDoc)
            var copied = 0L
            streamTransferHelper.copyDirectoryTransactionally(sourceDoc, destinationParent, targetName, totalBytes, afterPublish = { published ->
                currentCoroutineContext().ensureActive()
                streamTransferHelper.validateDirectoryTree(sourceDoc, published)
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

        currentCoroutineContext().ensureActive()
        val destinationDocument = uriResolver.resolveTreeDocumentFile(Uri.parse(cleanDestPath))
            ?: uriResolver.resolveDocumentFile(Uri.parse(cleanDestPath))
            ?: throw FileNotFoundException("Move failed: SAF destination cannot be resolved ($cleanDestPath)")
        val targetDocument = if (destinationDocument.isDirectory) {
            destinationDocument.findFile(targetName)
        } else {
            destinationDocument
        }
        val targetState = targetDocument?.let {
            SafMoveTargetState(
                exists = it.exists(),
                isFile = it.isFile,
                isDirectory = it.isDirectory,
                size = if (it.isFile) it.length() else 0L
            )
        }
        moveTargetValidator.validate(isSourceDir, sourceSize, targetState)
        currentCoroutineContext().ensureActive()
        delete(source)
    }.flowOn(Dispatchers.IO)

    override suspend fun getFileItem(location: StorageLocation): FileItem? {
        val documentFile = uriResolver.resolveDocumentFile(Uri.parse(location.path)) ?: return null
        if (!documentFile.exists()) return null
        val isDir = documentFile.isDirectory
        val itemUri = documentFile.uri.toString()
        val metadata = FileMetadata(
            size = if (isDir) 0L else documentFile.length(),
            modifiedTime = documentFile.lastModified(),
            createdTime = null,
            isReadable = documentFile.canRead(),
            isWritable = documentFile.canWrite(),
            isExecutable = false,
            isHidden = documentFile.name?.startsWith(".") == true
        )
        return FileItem(
            id = itemUri,
            name = documentFile.name ?: StorageConstants.DEFAULT_UNKNOWN_FILE_NAME,
            location = StorageLocation(itemUri, location.rootId),
            type = if (isDir) FileType.DIRECTORY else FileType.FILE,
            metadata = metadata
        )
    }

    override suspend fun exists(location: StorageLocation): Boolean =
        uriResolver.resolveDocumentFile(Uri.parse(location.path))?.exists() == true
}
