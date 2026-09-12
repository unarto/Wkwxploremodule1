// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/model/StorageSpaceInfo.kt
// [Penjelasan]: Model data immutable penyimpan informasi kapasitas penyimpanan (total, free, used, percentage).
package com.wakwau.xplore.core.storage.model

data class StorageSpaceInfo(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val percentageUsed: Int
)
