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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

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
        destType: StorageBackendType
    ): Flow<FileOperationProgress> = flow {
        val totalBytes = 0L // [CopyFix]: Penghitungan ukuran silang dihapus sesuai audit codemati.md
        var totalCopied = 0L

        if (totalBytes == 0L) {
            val sourceName = directoryHelper.getSourceName(source, sourceType)
            emit(FileOperationProgress(0L, 0L, sourceName))
        }

        val isSourceDir = directoryHelper.isSourceDirectory(source, sourceType)
        if (isSourceDir) {
            copyDirectoryCrossRecursively(
                source = source,
                destination = destination,
                sourceType = sourceType,
                destType = destType,
                totalBytes = totalBytes
            ) { bytes, fileName ->
                totalCopied += bytes
                emit(FileOperationProgress(totalCopied, totalBytes, fileName))
            }
        } else {
            copySingleFileCross(
                source = source,
                destination = destination,
                sourceType = sourceType,
                destType = destType,
                totalBytes = totalBytes
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
        val sourceSize = 0L // [CopyFix]: Penghitungan ukuran silang dihapus sesuai audit codemati.md

        try {
            copyCross(source, destination, sourceType, destType).collect { progress ->
                emit(progress)
            }
            if (currentCoroutineContext().isActive) {
                directoryHelper.validateTransferComplete(source, destination, sourceType, destType, isSourceDir, sourceSize)
                directoryHelper.deleteSource(source, sourceType)
            }
        } catch (e: Throwable) {
            directoryHelper.rollbackDestination(source, destination, sourceType, destType)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun copySingleFileCross(
        source: StorageLocation,
        destination: StorageLocation,
        sourceType: StorageBackendType,
        destType: StorageBackendType,
        totalBytes: Long,
        onProgress: suspend (Long, String) -> Unit
    ) {
        val sourceName = directoryHelper.getSourceName(source, sourceType)
        val inStream = openSourceInputStream(source, sourceType)
        var outHandle: OutputHandle? = null

        val buffer = ByteArray(StorageConstants.Buffer.DEFAULT_I_O_BUFFER_SIZE_BYTES)
        try {
            outHandle = openDestOutputStream(sourceName, destination, destType)
            inStream.use { input ->
                outHandle.outputStream.use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } >= 0) {
                        if (!currentCoroutineContext().isActive) {
                            outHandle.cleanup()
                            throw CancellationException("Cross-filesystem copy cancelled")
                        }
                        output.write(buffer, 0, bytesRead)
                        onProgress(bytesRead.toLong(), sourceName)
                    }
                }
            }
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

    private suspend fun copyDirectoryCrossRecursively(
        source: StorageLocation,
        destination: StorageLocation,
        sourceType: StorageBackendType,
        destType: StorageBackendType,
        totalBytes: Long,
        onProgress: suspend (Long, String) -> Unit
    ) {
        val sourceDirName = directoryHelper.getSourceName(source, sourceType)
        val targetDestLocation = directoryHelper.createDestDirectory(sourceDirName, destination, destType)
        val children = directoryHelper.listSourceChildren(source, sourceType)

        for (child in children) {
            if (!currentCoroutineContext().isActive) {
                throw CancellationException("Cross-filesystem directory copy cancelled")
            }
            val isChildDir = directoryHelper.isSourceDirectory(child, sourceType)
            if (isChildDir) {
                copyDirectoryCrossRecursively(
                    source = child,
                    destination = targetDestLocation,
                    sourceType = sourceType,
                    destType = destType,
                    totalBytes = totalBytes,
                    onProgress = onProgress
                )
            } else {
                copySingleFileCross(
                    source = child,
                    destination = targetDestLocation,
                    sourceType = sourceType,
                    destType = destType,
                    totalBytes = totalBytes,
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

    private data class OutputHandle(val outputStream: OutputStream, val cleanup: () -> Unit)

    private suspend fun openDestOutputStream(
        sourceFileName: String,
        destination: StorageLocation,
        destType: StorageBackendType
    ): OutputHandle = when (destType) {
        StorageBackendType.LOCAL -> {
            val destFile = directoryHelper.resolveLocalDestFile(sourceFileName, destination.path)
            destFile.parentFile?.mkdirs()
            OutputHandle(FileOutputStream(destFile)) { try { destFile.delete() } catch (e: Exception) { android.util.Log.w("FileSystem", "Failed to clean partial file", e) } }
        }
        StorageBackendType.SAF -> {
            val destDoc = directoryHelper.resolveSafDocument(Uri.parse(destination.path))
                ?: throw FileNotFoundException("Destination SAF folder not found: ${destination.path}")
            val targetFileDoc = if (destDoc.isDirectory) {
                destDoc.createFile(StorageConstants.DEFAULT_MIME_TYPE_ALL, sourceFileName)
                    ?: throw IOException("Failed to create SAF destination file: $sourceFileName")
            } else destDoc
            val outStream = context.contentResolver.openOutputStream(targetFileDoc.uri)
                ?: throw IOException("Cannot open SAF output stream: ${targetFileDoc.uri}")
            OutputHandle(outStream) { try { targetFileDoc.delete() } catch (e: Exception) { android.util.Log.w("FileSystem", "Failed to clean partial file", e) } }
        }
        StorageBackendType.SHIZUKU -> {
            val service = directoryHelper.getShizukuService()
            val destFilePath = directoryHelper.resolveShizukuDestFilePath(sourceFileName, destination.path)
            val pfd = service.openFileForWrite(destFilePath)
                ?: throw IOException("Cannot open Shizuku output stream: $destFilePath")
            OutputHandle(android.os.ParcelFileDescriptor.AutoCloseOutputStream(pfd)) {
                try { service.delete(destFilePath) } catch (e: Exception) { android.util.Log.w("FileSystem", "Failed to clean partial file", e) }
            }
        }
        StorageBackendType.ROOT -> {
            val destFilePath = directoryHelper.resolveRootDestFilePath(sourceFileName, destination.path)
            val destFile = SuFile(destFilePath)
            destFile.parentFile?.let { SuFile(it.absolutePath).mkdirs() }
            OutputHandle(SuFileOutputStream.open(destFile)) { try { destFile.delete() } catch (e: Exception) { android.util.Log.w("FileSystem", "Failed to clean partial file", e) } }
        }
    }
}
