// [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/filesystem/bridge/CrossFilesystemTransferBridge.kt
// [Penjelasan]: Bridge transfer streaming I/O lintas sistem berkas (Local <-> SAF <-> Shizuku <-> Root) yang mendelegasikan manajemen direktori ke CrossFilesystemDirectoryTransferHelper (< 250 LOC).
package com.wakwau.xplore.core.storage.filesystem.bridge

import android.content.Context
import android.net.Uri
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
import com.wakwau.xplore.core.storage.constant.StorageConstants
import com.wakwau.xplore.core.storage.filesystem.LocalFileSystemContract
import com.wakwau.xplore.core.storage.filesystem.RootFileSystemContract
import com.wakwau.xplore.core.storage.filesystem.SafFileSystemContract
import com.wakwau.xplore.core.storage.filesystem.ShizukuFileSystemContract
import com.wakwau.xplore.core.storage.filesystem.StorageBackendType
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.FileOperationProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

open class CrossFilesystemTransferBridge(
    private val context: Context,
    private val localFileSystem: LocalFileSystemContract,
    private val safFileSystem: SafFileSystemContract,
    private val safShizukuFileSystem: ShizukuFileSystemContract,
    private val rootFileSystem: RootFileSystemContract,
    private val directoryHelper: CrossFilesystemDirectoryTransferHelper = CrossFilesystemDirectoryTransferHelper(
        context = context,
        localFileSystem = localFileSystem,
        safFileSystem = safFileSystem,
        safShizukuFileSystem = safShizukuFileSystem,
        rootFileSystem = rootFileSystem
    )
) {

    open fun copyCross(
        source: StorageLocation,
        destination: StorageLocation,
        sourceType: StorageBackendType,
        destType: StorageBackendType,
        afterDirectoryPublish: suspend (StorageLocation) -> Unit = {}
    ): Flow<FileOperationProgress> = flow {
        val totalBytes = directoryHelper.calculateTotalSize(source, sourceType)
        var totalCopied = 0L

        if (totalBytes == 0L) {
            val sourceName = directoryHelper.getSourceName(source, sourceType)
            emit(FileOperationProgress(0L, 0L, sourceName))
        }

        val isSourceDir = directoryHelper.isSourceDirectory(source, sourceType)
        if (isSourceDir) {
            val sourceName = directoryHelper.getSourceName(source, sourceType)
            val staging = directoryHelper.createDirectoryStaging(sourceName, destination, destType)
            try {
                copyDirectoryContentsCrossRecursively(
                    source = source,
                    destination = staging.staging,
                    sourceType = sourceType,
                    destType = destType
                ) { bytes, fileName ->
                    totalCopied += bytes
                    emit(FileOperationProgress(totalCopied, totalBytes, fileName))
                }
                currentCoroutineContext().ensureActive()
                directoryHelper.publishDirectoryStaging(staging, sourceName, destination, afterDirectoryPublish)
            } catch (error: Throwable) {
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        directoryHelper.cleanupDirectoryStaging(staging)
                    }
                } catch (cleanup: Throwable) {
                    error.addSuppressed(cleanup)
                }
                throw error
            }
        } else {
            copySingleFileCross(
                source = source,
                destination = destination,
                sourceType = sourceType,
                destType = destType
            ) { bytes, fileName ->
                totalCopied += bytes
                emit(FileOperationProgress(totalCopied, totalBytes, fileName))
            }
        }
    }.flowOn(Dispatchers.IO)

    open fun moveCross(
        source: StorageLocation,
        destination: StorageLocation,
        sourceType: StorageBackendType,
        destType: StorageBackendType
    ): Flow<FileOperationProgress> = flow {
        val isSourceDir = directoryHelper.isSourceDirectory(source, sourceType)
        if (isSourceDir) {
            copyCross(source, destination, sourceType, destType) {
                currentCoroutineContext().ensureActive()
                directoryHelper.validateTransferComplete(source, destination, sourceType, destType, true)
                currentCoroutineContext().ensureActive()
            }.collect { emit(it) }
        } else {
            val totalBytes = directoryHelper.calculateTotalSize(source, sourceType)
            var copiedBytes = 0L
            copySingleFileCross(source, destination, sourceType, destType) { bytes, name ->
                copiedBytes += bytes
                emit(FileOperationProgress(copiedBytes, totalBytes, name))
            }
        }
        // Publish and validation have committed. Even partial source deletion must
        // leave the valid destination intact.
        com.wakwau.xplore.core.storage.filesystem.deleteAfterTransferCommit {
            directoryHelper.deleteSource(source, sourceType)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun copySingleFileCross(
        source: StorageLocation,
        destination: StorageLocation,
        sourceType: StorageBackendType,
        destType: StorageBackendType,
        onProgress: suspend (Long, String) -> Unit
    ) {
        val sourceName = directoryHelper.getSourceName(source, sourceType)
        val expectedBytes = directoryHelper.calculateTotalSize(source, sourceType)
        var writtenBytes = 0L
        val inStream = openSourceInputStream(source, sourceType)
        var outHandle: OutputHandle? = null

        val buffer = ByteArray(StorageConstants.Buffer.DEFAULT_I_O_BUFFER_SIZE_BYTES)
        try {
            outHandle = openDestOutputStream(sourceName, destination, destType)
            inStream.use { input ->
                outHandle.outputStream.use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } >= 0) {
                        currentCoroutineContext().ensureActive()
                        output.write(buffer, 0, bytesRead)
                        writtenBytes += bytesRead
                        onProgress(bytesRead.toLong(), sourceName)
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            if (writtenBytes != expectedBytes) throw IOException("Source changed during copy: ${source.path}")
            outHandle.validate(writtenBytes)
            outHandle.commit()
        } catch (e: CancellationException) {
            outHandle?.cleanup()
            inStream.close()
            throw e
        } catch (e: Exception) {
            outHandle?.cleanup()
            inStream.close()
            throw e
        }
    }

    private suspend fun copyDirectoryContentsCrossRecursively(
        source: StorageLocation,
        destination: StorageLocation,
        sourceType: StorageBackendType,
        destType: StorageBackendType,
        onProgress: suspend (Long, String) -> Unit
    ) {
        val targetDestLocation = destination
        val children = directoryHelper.listSourceChildren(source, sourceType)

        for (child in children) {
            currentCoroutineContext().ensureActive()
            val isChildDir = directoryHelper.isSourceDirectory(child, sourceType)
            if (isChildDir) {
                val childName = directoryHelper.getSourceName(child, sourceType)
                val childDestination = directoryHelper.createDestDirectory(childName, targetDestLocation, destType)
                copyDirectoryContentsCrossRecursively(
                    source = child,
                    destination = childDestination,
                    sourceType = sourceType,
                    destType = destType,
                    onProgress = onProgress
                )
            } else {
                copySingleFileCross(
                    source = child,
                    destination = directoryHelper.childTarget(targetDestLocation, directoryHelper.getSourceName(child, sourceType)),
                    sourceType = sourceType,
                    destType = destType,
                    onProgress = onProgress
                )
            }
        }
    }

    private suspend fun openSourceInputStream(source: StorageLocation, sourceType: StorageBackendType): InputStream = when (sourceType) {
        StorageBackendType.LOCAL -> {
            val file = File(source.path)
            if (!file.exists()) throw FileNotFoundException("Source local file not found: ${source.path}")
            FileInputStream(file)
        }
        StorageBackendType.SAF -> {
            val doc = directoryHelper.resolveSafDocument(Uri.parse(source.path)) ?: throw FileNotFoundException("Source SAF file not found: ${source.path}")
            context.contentResolver.openInputStream(doc.uri) ?: throw FileNotFoundException("Cannot open SAF input stream: ${source.path}")
        }
        StorageBackendType.SHIZUKU -> {
            val pfd = directoryHelper.getShizukuService().openFileForRead(source.path) ?: throw IOException("Cannot open Shizuku input stream: ${source.path}")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd)
        }
        StorageBackendType.ROOT -> SuFileInputStream.open(SuFile(source.path))
    }

    private data class OutputHandle(
        val outputStream: OutputStream,
        val commit: () -> Unit,
        val validate: (Long) -> Unit,
        val cleanup: () -> Unit
    )

    private suspend fun openDestOutputStream(
        sourceFileName: String,
        destination: StorageLocation,
        destType: StorageBackendType
    ): OutputHandle = when (destType) {
        StorageBackendType.LOCAL -> {
            val destFile = directoryHelper.resolveLocalDestFile(sourceFileName, destination.path)
            destFile.parentFile?.mkdirs()
            val temporary = File(destFile.parentFile, ".${destFile.name}.wkw-${UUID.randomUUID()}.tmp")
            OutputHandle(
                FileOutputStream(temporary),
                commit = { replaceLocalFile(temporary, destFile) },
                validate = { size -> if (temporary.length() != size) throw IOException("Incomplete staged file: $temporary") },
                cleanup = { try { temporary.delete() } catch (e: Exception) { android.util.Log.w("FileSystem", "Failed to clean temporary file", e) } }
            )
        }
        StorageBackendType.SAF -> {
            val finalName = Uri.parse(destination.path).fragment ?: sourceFileName
            val destDoc = directoryHelper.resolveSafDocument(Uri.parse(destination.path.substringBefore('#')))
                ?: throw FileNotFoundException("Destination SAF folder not found: ${destination.path}")
            val parent = if (destDoc.isDirectory) destDoc else destDoc.parentFile
                ?: throw IOException("Cannot safely replace SAF destination without a parent: ${destDoc.uri}")
            val existing = if (destDoc.isDirectory) parent.findFile(finalName) else destDoc
            val temporaryName = ".$sourceFileName.wkw-${UUID.randomUUID()}.tmp"
            val temporary = parent.createFile(StorageConstants.DEFAULT_MIME_TYPE_ALL, temporaryName)
                ?: throw IOException("Failed to create temporary SAF destination file: $sourceFileName")
            val outStream = context.contentResolver.openOutputStream(temporary.uri)
                ?: run {
                    temporary.delete()
                    throw IOException("Cannot open SAF output stream: ${temporary.uri}")
                }
            OutputHandle(
                outStream,
                commit = { replaceSafDocument(parent, temporary, existing, finalName) },
                validate = { size -> if (temporary.length() != size) throw IOException("Incomplete staged SAF file: ${temporary.uri}") },
                cleanup = { try { parent.findFile(temporaryName)?.delete() } catch (e: Exception) { android.util.Log.w("FileSystem", "Failed to clean temporary file", e) } }
            )
        }
        StorageBackendType.SHIZUKU -> {
            val service = directoryHelper.getShizukuService()
            val destFilePath = directoryHelper.resolveShizukuDestFilePath(sourceFileName, destination.path)
            val temporaryPath = "$destFilePath.wkw-${UUID.randomUUID()}.tmp"
            val pfd = service.openFileForWrite(temporaryPath)
                ?: throw IOException("Cannot open Shizuku temporary output stream: $temporaryPath")
            OutputHandle(
                android.os.ParcelFileDescriptor.AutoCloseOutputStream(pfd),
                commit = { replaceShizukuFile(service, temporaryPath, destFilePath) },
                validate = { size -> if (service.length(temporaryPath) != size) throw IOException("Incomplete staged file: $temporaryPath") },
                cleanup = { try { service.delete(temporaryPath) } catch (e: Exception) { android.util.Log.w("FileSystem", "Failed to clean temporary file", e) } }
            )
        }
        StorageBackendType.ROOT -> {
            val destFilePath = directoryHelper.resolveRootDestFilePath(sourceFileName, destination.path)
            val destFile = SuFile(destFilePath)
            destFile.parentFile?.let { SuFile(it.absolutePath).mkdirs() }
            val temporary = SuFile("$destFilePath.wkw-${UUID.randomUUID()}.tmp")
            OutputHandle(
                SuFileOutputStream.open(temporary),
                commit = { replaceRootFile(temporary, destFile) },
                validate = { size -> if (temporary.length() != size) throw IOException("Incomplete staged file: $temporary") },
                cleanup = { try { temporary.delete() } catch (e: Exception) { android.util.Log.w("FileSystem", "Failed to clean temporary file", e) } }
            )
        }
    }

    private fun replaceLocalFile(temporary: File, destination: File) {
        try {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            if (!destination.exists()) {
                Files.move(temporary.toPath(), destination.toPath())
                return
            }
            val backup = File(destination.parentFile, ".${destination.name}.wkw-${UUID.randomUUID()}.bak")
            Files.move(destination.toPath(), backup.toPath())
            try {
                Files.move(temporary.toPath(), destination.toPath())
            } catch (error: Throwable) {
                if (!destination.exists()) Files.move(backup.toPath(), destination.toPath())
                throw error
            }
            try {
                Files.delete(backup.toPath())
            } catch (error: Exception) {
                android.util.Log.w("FileSystem", "Published destination retained; backup cleanup failed", error)
            }
        }
    }

    private fun replaceSafDocument(parent: androidx.documentfile.provider.DocumentFile, temporary: androidx.documentfile.provider.DocumentFile, existing: androidx.documentfile.provider.DocumentFile?, finalName: String) {
        if (existing == null) {
            if (!temporary.renameTo(finalName)) throw IOException("Failed to publish SAF destination: $finalName")
            return
        }
        val backupName = ".$finalName.wkw-${UUID.randomUUID()}.bak"
        if (!existing.renameTo(backupName)) throw IOException("Failed to preserve SAF destination: $finalName")
        try {
            if (!temporary.renameTo(finalName)) throw IOException("Failed to publish SAF destination: $finalName")
        } catch (error: Throwable) {
            if (parent.findFile(finalName) == null) existing.renameTo(finalName)
            throw error
        }
        try {
            if (!existing.delete() && existing.exists()) throw IOException("Failed to remove SAF destination backup: $backupName")
        } catch (error: Exception) {
            android.util.Log.w("FileSystem", "Published destination retained; backup cleanup failed", error)
        }
    }

    private fun replaceShizukuFile(service: com.wakwau.xplore.core.storage.shizuku.IPrivilegedFileService, temporaryPath: String, destinationPath: String) {
        if (!service.exists(destinationPath)) {
            if (!service.rename(temporaryPath, destinationPath)) throw IOException("Failed to publish Shizuku destination: $destinationPath")
            return
        }
        val backupPath = "$destinationPath.wkw-${UUID.randomUUID()}.bak"
        if (!service.rename(destinationPath, backupPath)) throw IOException("Failed to preserve Shizuku destination: $destinationPath")
        try {
            if (!service.rename(temporaryPath, destinationPath)) throw IOException("Failed to publish Shizuku destination: $destinationPath")
        } catch (error: Throwable) {
            if (!service.exists(destinationPath)) service.rename(backupPath, destinationPath)
            throw error
        }
        try {
            if (!service.delete(backupPath) && service.exists(backupPath)) throw IOException("Failed to remove Shizuku destination backup: $backupPath")
        } catch (error: Exception) {
            android.util.Log.w("FileSystem", "Published destination retained; backup cleanup failed", error)
        }
    }

    private fun replaceRootFile(temporary: SuFile, destination: SuFile) {
        if (!destination.exists()) {
            if (!temporary.renameTo(destination)) throw IOException("Failed to publish root destination: ${destination.absolutePath}")
            return
        }
        val backup = SuFile("${destination.absolutePath}.wkw-${UUID.randomUUID()}.bak")
        if (!destination.renameTo(backup)) throw IOException("Failed to preserve root destination: ${destination.absolutePath}")
        try {
            if (!temporary.renameTo(destination)) throw IOException("Failed to publish root destination: ${destination.absolutePath}")
        } catch (error: Throwable) {
            if (!destination.exists()) backup.renameTo(destination)
            throw error
        }
        try {
            if (!backup.delete() && backup.exists()) throw IOException("Failed to remove root destination backup: ${backup.absolutePath}")
        } catch (error: Exception) {
            android.util.Log.w("FileSystem", "Published destination retained; backup cleanup failed", error)
        }
    }
}
