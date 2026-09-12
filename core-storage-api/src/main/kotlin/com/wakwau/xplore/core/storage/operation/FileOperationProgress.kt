// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/operation/FileOperationProgress.kt
// [Penjelasan]: Model progress untuk operasi file secara real-time yang mencakup jumlah byte tertulis, total byte, dan nama file yang sedang diproses.
package com.wakwau.xplore.core.storage.operation

data class FileOperationProgress(
    val bytesWritten: Long,
    val totalBytes: Long,
    val fileName: String
) {
    val percentage: Float
        get() = if (totalBytes > 0) bytesWritten.toFloat() / totalBytes else 0f
}
