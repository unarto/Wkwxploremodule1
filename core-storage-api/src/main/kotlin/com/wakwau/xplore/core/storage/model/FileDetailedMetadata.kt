// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/model/FileDetailedMetadata.kt
// [Penjelasan]: Model data immutable untuk rincian metadata berkas seperti path absolut, izin POSIX, timestamp modifikasi, ukuran, dan tipe MIME.
package com.wakwau.xplore.core.storage.model

data class FileDetailedMetadata(
    val fileName: String,
    val fullPath: String,
    val parentPath: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val lastModifiedTimestamp: Long,
    val isReadable: Boolean,
    val isWritable: Boolean,
    val isExecutable: Boolean,
    val isHidden: Boolean,
    val posixPermissions: String,
    val mimeType: String
)
