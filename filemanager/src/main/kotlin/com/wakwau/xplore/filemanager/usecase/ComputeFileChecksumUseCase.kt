// [Jalur Class/Modul]: filemanager/src/main/kotlin/com/wakwau/xplore/filemanager/usecase/ComputeFileChecksumUseCase.kt
// [Penjelasan]: UseCase operasi kalkulasi checksum berkas read-only dipindahkan ke modul filemanager sesuai ownership.md.
package com.wakwau.xplore.filemanager.usecase

import com.wakwau.xplore.core.storage.checksum.FileChecksumReader
import com.wakwau.xplore.core.storage.model.FileChecksum
import com.wakwau.xplore.core.storage.model.StorageLocation

class ComputeFileChecksumUseCase(
    private val checksumReader: FileChecksumReader
) {
    suspend operator fun invoke(location: StorageLocation): FileChecksum {
        return checksumReader.calculateChecksum(location)
    }
}
