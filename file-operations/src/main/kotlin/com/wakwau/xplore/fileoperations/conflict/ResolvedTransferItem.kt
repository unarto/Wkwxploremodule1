// [Jalur Class/Modul]: file-operations/src/main/kotlin/com/wakwau/xplore/fileoperations/conflict/ResolvedTransferItem.kt
// [Penjelasan]: Model entitas transfer berkas/direktori setelah evaluasi benturan yang membawa nama dan lokasi target definitif.
package com.wakwau.xplore.fileoperations.conflict

import com.wakwau.xplore.core.storage.model.StorageLocation

data class ResolvedTransferItem(
    val source: StorageLocation,
    val destinationDir: StorageLocation,
    val targetLocation: StorageLocation,
    val originalName: String,
    val targetName: String,
    val isDirectory: Boolean,
    val choice: ConflictChoice
)
