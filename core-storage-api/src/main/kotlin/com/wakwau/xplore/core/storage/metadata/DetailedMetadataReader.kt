// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/metadata/DetailedMetadataReader.kt
// [Penjelasan]: Kontrak interface domain untuk mengekstrak informasi metadata berkas secara lengkap dan mendalam.
package com.wakwau.xplore.core.storage.metadata

import com.wakwau.xplore.core.storage.model.FileDetailedMetadata
import com.wakwau.xplore.core.storage.model.StorageLocation

interface DetailedMetadataReader {
    suspend fun readDetailedMetadata(location: StorageLocation): FileDetailedMetadata
}
