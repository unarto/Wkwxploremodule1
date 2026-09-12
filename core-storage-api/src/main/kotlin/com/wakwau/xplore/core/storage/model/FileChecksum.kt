// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/model/FileChecksum.kt
// [Penjelasan]: Model data immutable untuk menyimpan hash kriptografis berkas (MD5, SHA-1, SHA-256).
package com.wakwau.xplore.core.storage.model

data class FileChecksum(
    val md5: String,
    val sha1: String,
    val sha256: String
)
