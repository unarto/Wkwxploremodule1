// [Jalur Class/Modul]: file-operations/src/main/kotlin/com/wakwau/xplore/fileoperations/conflict/ResolveTransferUseCase.kt
// [Penjelasan]: UseCase penyelesaian benturan berkas untuk mentransformasi daftar berkas sumber dan keputusan pengguna menjadi ResolvedTransferItem siap eksekusi menggunakan DetailedMetadataReader untuk deteksi tipe direktori secara akurat.
package com.wakwau.xplore.fileoperations.conflict

import com.wakwau.xplore.fileoperations.conflict.ConflictChoice
import com.wakwau.xplore.fileoperations.conflict.ConflictDetector
import com.wakwau.xplore.fileoperations.conflict.ConflictResolver
import com.wakwau.xplore.fileoperations.conflict.ResolvedTransferItem
import com.wakwau.xplore.core.storage.metadata.DetailedMetadataReader
import com.wakwau.xplore.core.storage.model.StorageLocation

class ResolveTransferUseCase(
    private val conflictDetector: ConflictDetector,
    private val conflictResolver: ConflictResolver,
    private val detailedMetadataReader: DetailedMetadataReader
) {
    suspend operator fun invoke(
        sources: List<StorageLocation>,
        destinationDir: StorageLocation,
        conflictDecisions: Map<StorageLocation, ConflictChoice>
    ): List<ResolvedTransferItem> {
        val existingNames = conflictDetector.getExistingNames(destinationDir).toMutableSet()
        val conflicts = conflictDetector.detectConflicts(sources, destinationDir)
        val conflictMap = conflicts.associateBy { it.source }

        val resolved = mutableListOf<ResolvedTransferItem>()

        for (source in sources) {
            val conflict = conflictMap[source]
            if (conflict != null) {
                val choice = conflictDecisions[source] ?: ConflictChoice.SKIP
                val item = conflictResolver.resolveConflict(conflict, choice, existingNames)
                if (item != null) {
                    resolved.add(item)
                }
            } else {
                val isDir = runCatching {
                    detailedMetadataReader.readDetailedMetadata(source).isDirectory
                }.getOrDefault(false)

                val item = conflictResolver.resolveNonConflictingItem(
                    source = source,
                    destinationDir = destinationDir,
                    isDirectory = isDir,
                    existingNames = existingNames
                )
                resolved.add(item)
            }
        }

        return resolved
    }
}

