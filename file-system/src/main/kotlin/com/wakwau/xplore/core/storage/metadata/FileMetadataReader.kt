// [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/metadata/FileMetadataReader.kt
// [Penjelasan]: Single source of truth untuk ekstraksi atribut dasar metadata berkas fisik (java.io.File) seperti ukuran byte, timestamp modifikasi, permission flag, dan hidden status.
package com.wakwau.xplore.core.storage.metadata

import com.wakwau.xplore.core.storage.model.FileMetadata
import java.io.File

class FileMetadataReader {
    fun readMetadata(file: File): FileMetadata {
        return FileMetadata(
            size = if (file.isFile) file.length() else 0L,
            modifiedTime = file.lastModified(),
            createdTime = null, // not easily available via java.io.File without NIO
            isReadable = file.canRead(),
            isWritable = file.canWrite(),
            isExecutable = file.canExecute(),
            isHidden = file.isHidden
        )
    }
}
