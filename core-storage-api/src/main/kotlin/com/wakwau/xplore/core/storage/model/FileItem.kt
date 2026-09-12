// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/model/FileItem.kt
// [Penjelasan]: Model data immutable representasi entitas berkas atau folder dalam sistem penyimpanan.
package com.wakwau.xplore.core.storage.model

data class FileItem(
    val id: String,
    val name: String,
    val location: StorageLocation,
    val type: FileType,
    val metadata: FileMetadata
)
