// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/model/FileIndexItem.kt
// [Penjelasan]: Model domain representasi entitas indeks berkas untuk query pencarian cepat dan kategorisasi berkas tanpa ketergantungan Room framework.
package com.wakwau.xplore.core.storage.model

data class FileIndexItem(
    val filePath: String,
    val fileName: String,
    val size: Long,
    val extension: String,
    val category: String,
    val dateModified: Long,
    val isDirectory: Boolean = false
)
