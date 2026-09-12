// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/model/StorageVolumeItem.kt
// [Penjelasan]: Model data immutable representasi volume penyimpanan fisik atau virtual (internal, SD card, OTG, root).
package com.wakwau.xplore.core.storage.model

data class StorageVolumeItem(
    val id: String,
    val name: String,
    val rootPath: String,
    val type: StorageVolumeType,
    val isReadOnly: Boolean,
    val spaceInfo: StorageSpaceInfo?,
    val createdAt: Long = 0L
)
