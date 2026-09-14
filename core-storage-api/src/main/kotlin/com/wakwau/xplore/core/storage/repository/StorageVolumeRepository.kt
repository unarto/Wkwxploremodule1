// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/repository/StorageVolumeRepository.kt
// [Penjelasan]: Antarmuka kontrak repository untuk pemantauan dan penyegaran daftar volume penyimpanan sistem.
package com.wakwau.xplore.core.storage.repository

import com.wakwau.xplore.core.storage.model.StorageVolumeItem
import kotlinx.coroutines.flow.Flow

interface StorageVolumeRepository {
    fun getVolumes(): Flow<List<StorageVolumeItem>>
    suspend fun refreshVolumes()
    fun close()
}
