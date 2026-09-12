// [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/metadata/LocalDetailedMetadataReader.kt
// [Penjelasan]: Membaca rincian metadata lengkap berkas dari sistem berkas lokal maupun SAF Content URI seperti path lengkap, ukuran byte, timestamp, izin POSIX, dan MIME type secara aktual dengan mendelegasikan ekstraksi atribut dasar java.io.File ke FileMetadataReader.
package com.wakwau.xplore.core.storage.metadata

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.wakwau.xplore.core.storage.model.FileDetailedMetadata
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.permission.FilePermissionFormatter
import com.wakwau.xplore.core.utils.mime.MimeTypeDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
class LocalDetailedMetadataReader(
    private val permissionFormatter: FilePermissionFormatter = FilePermissionFormatter(),
    private val fileMetadataReader: FileMetadataReader = FileMetadataReader(),
    private val context: Context? = null
) : DetailedMetadataReader {

    override suspend fun readDetailedMetadata(location: StorageLocation): FileDetailedMetadata = withContext(Dispatchers.IO) {
        if (location.path.startsWith("content://") && context != null) {
            val uri = Uri.parse(location.path)
            val doc = DocumentFile.fromSingleUri(context, uri) ?: DocumentFile.fromTreeUri(context, uri)
            val isDirectory = doc?.isDirectory ?: false
            val name = doc?.name ?: location.path
            val mimeType = if (isDirectory) "inode/directory" else (doc?.type ?: MimeTypeDetector.getMimeType(name))

            FileDetailedMetadata(
                fileName = name,
                fullPath = location.path,
                parentPath = uri.path ?: "",
                sizeBytes = if (doc?.isFile == true) doc.length() else 0L,
                isDirectory = isDirectory,
                lastModifiedTimestamp = doc?.lastModified() ?: 0L,
                isReadable = doc?.canRead() ?: false,
                isWritable = doc?.canWrite() ?: false,
                isExecutable = false,
                isHidden = name.startsWith("."),
                posixPermissions = if (doc?.canWrite() == true) "rw-" else "r--",
                mimeType = mimeType
            )
        } else {
            val file = File(location.path)
            val isDirectory = file.isDirectory
            val mimeType = if (isDirectory) "inode/directory" else MimeTypeDetector.getMimeType(file.name)
            val name = file.name.ifEmpty { location.path }
            val basicMetadata = fileMetadataReader.readMetadata(file)

            FileDetailedMetadata(
                fileName = name,
                fullPath = file.absolutePath,
                parentPath = file.parent ?: "",
                sizeBytes = basicMetadata.size,
                isDirectory = isDirectory,
                lastModifiedTimestamp = basicMetadata.modifiedTime,
                isReadable = basicMetadata.isReadable,
                isWritable = basicMetadata.isWritable,
                isExecutable = basicMetadata.isExecutable,
                isHidden = basicMetadata.isHidden,
                posixPermissions = permissionFormatter.formatPosixPermissions(file),
                mimeType = mimeType
            )
        }
    }
}
