// [Jalur Class/Modul]: file-operations/src/main/kotlin/com/wakwau/xplore/fileoperations/conflict/FileConflict.kt
// [Penjelasan]: Model representasi benturan nama antara entitas berkas/direktori sumber dan entitas yang sudah ada di lokasi tujuan.
package com.wakwau.xplore.fileoperations.conflict

import com.wakwau.xplore.core.storage.model.StorageLocation

data class FileConflict(
    val source: StorageLocation,
    val sourceName: String,
    val targetName: String,
    val isDirectory: Boolean,
    val destinationDir: StorageLocation
)
