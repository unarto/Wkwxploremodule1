// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/model/StorageLocation.kt
// [Penjelasan]: Model data immutable representasi lokasi penyimpanan berkas atau direktori.
package com.wakwau.xplore.core.storage.model

data class StorageLocation(
    val path: String,
    val rootId: String = ""
)
