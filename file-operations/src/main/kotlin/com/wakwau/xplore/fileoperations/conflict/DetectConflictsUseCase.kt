// [Jalur Class/Modul]: file-operations/src/main/kotlin/com/wakwau/xplore/fileoperations/conflict/DetectConflictsUseCase.kt
// [Penjelasan]: UseCase pendeteksian benturan nama/direktori pada lokasi target di modul file-operations dalam package khusus conflict sesuai ownership.md.
package com.wakwau.xplore.fileoperations.conflict

import com.wakwau.xplore.fileoperations.conflict.ConflictDetector
import com.wakwau.xplore.fileoperations.conflict.FileConflict
import com.wakwau.xplore.core.storage.model.StorageLocation

class DetectConflictsUseCase(
    private val conflictDetector: ConflictDetector
) {
    suspend operator fun invoke(
        sources: List<StorageLocation>,
        destinationDir: StorageLocation
    ): List<FileConflict> {
        return conflictDetector.detectConflicts(sources, destinationDir)
    }
}
