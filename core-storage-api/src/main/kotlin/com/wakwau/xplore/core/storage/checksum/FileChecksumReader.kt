// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/checksum/FileChecksumReader.kt
// [Penjelasan]: Kontrak interface domain untuk membaca atau menghitung checksum hash kriptografis berkas.
package com.wakwau.xplore.core.storage.checksum

import com.wakwau.xplore.core.storage.model.FileChecksum
import com.wakwau.xplore.core.storage.model.StorageLocation

interface FileChecksumReader {
    suspend fun calculateChecksum(location: StorageLocation): FileChecksum
}
