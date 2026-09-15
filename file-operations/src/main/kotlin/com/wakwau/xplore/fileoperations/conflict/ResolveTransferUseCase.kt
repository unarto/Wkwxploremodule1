// [Jalur Class/Modul]: file-operations/src/main/kotlin/com/wakwau/xplore/fileoperations/conflict/ResolveTransferUseCase.kt
// [Penjelasan]: UseCase penyelesaian benturan berkas untuk mentransformasi daftar berkas sumber dan keputusan pengguna menjadi ResolvedTransferItem siap eksekusi menggunakan DetailedMetadataReader untuk deteksi tipe direktori secara akurat.
package com.wakwau.xplore.fileoperations.conflict

import com.wakwau.xplore.fileoperations.conflict.ConflictChoice
import com.wakwau.xplore.fileoperations.conflict.ConflictDetector
import com.wakwau.xplore.fileoperations.conflict.ConflictResolver
import com.wakwau.xplore.fileoperations.conflict.ResolvedTransferItem
import com.wakwau.xplore.core.storage.metadata.DetailedMetadataReader
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.model.isSameOrDescendantOf
import kotlinx.coroutines.ensureActive

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
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        require(detailedMetadataReader.readDetailedMetadata(destinationDir).isDirectory) { "Destination must be a directory" }
        val uniqueSources = sources.distinct()
        val existingNames = conflictDetector.getExistingNames(destinationDir).toMutableSet()
        val conflicts = conflictDetector.detectConflicts(uniqueSources, destinationDir)
        val unresolved = conflicts.filter {
            conflictDecisions[it.source] == null ||
                (it.isBatchCollision && conflictDecisions[it.source] == ConflictChoice.OVERWRITE)
        }
        if (unresolved.isNotEmpty()) throw UnresolvedTransferConflicts(unresolved)
        val conflictMap = conflicts.associateBy { it.source }
        val plannedTargets = mutableSetOf<String>()
        val resolved = mutableListOf<ResolvedTransferItem>()
        for (source in uniqueSources) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val metadata = detailedMetadataReader.readDetailedMetadata(source)
            require(!metadata.isDirectory || !destinationDir.isSameOrDescendantOf(source)) {
                "Cannot transfer a directory into itself or its descendants"
            }
            val name = metadata.fileName
            require(name.isNotBlank() && name != "." && name != ".." && '/' !in name) { "Invalid source name" }
            val conflict = conflictMap[source] ?: FileConflict(source, name, name, metadata.isDirectory, destinationDir)
            val choice = conflictDecisions[source] ?: ConflictChoice.OVERWRITE
            if (choice == ConflictChoice.SKIP) {
                val skipped = requireNotNull(conflictResolver.resolveConflict(conflict, ConflictChoice.OVERWRITE, existingNames))
                resolved.add(skipped.copy(choice = ConflictChoice.SKIP))
                continue
            }
            val item = requireNotNull(conflictResolver.resolveConflict(conflict, choice, existingNames))
            require(!(item.targetLocation.isSameOrDescendantOf(source) && source.isSameOrDescendantOf(item.targetLocation))) {
                "Source and final target must differ"
            }
            val key = item.targetLocation.path.lowercase(java.util.Locale.ROOT)
            if (!plannedTargets.add(key)) {
                throw UnresolvedTransferConflicts(listOf(conflict.copy(isBatchCollision = true)))
            }
            resolved.add(item)
        }
        return resolved
    }
}

