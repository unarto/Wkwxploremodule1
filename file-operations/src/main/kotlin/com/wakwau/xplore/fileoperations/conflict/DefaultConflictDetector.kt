// [Jalur Class/Modul]: file-operations/src/main/kotlin/com/wakwau/xplore/fileoperations/conflict/DefaultConflictDetector.kt
// [Penjelasan]: Implementasi ConflictDetector untuk mendeteksi potensi konflik nama berkas dan folder di direktori tujuan, berkomunikasi murni melalui abstraksi DirectoryRepository dan DetailedMetadataReader dari core-storage-api tanpa menyentuh implementasi filesystem konkret.
package com.wakwau.xplore.fileoperations.conflict

import com.wakwau.xplore.fileoperations.conflict.ConflictDetector
import com.wakwau.xplore.fileoperations.conflict.FileConflict
import com.wakwau.xplore.core.storage.constant.StorageConstants
import com.wakwau.xplore.core.storage.metadata.DetailedMetadataReader
import com.wakwau.xplore.core.storage.model.FileDetailedMetadata
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.model.isSameOrDescendantOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import com.wakwau.xplore.core.storage.repository.DirectoryRepository

class DefaultConflictDetector(
    private val directoryRepository: DirectoryRepository,
    private val detailedMetadataReader: DetailedMetadataReader
) : ConflictDetector {

    override suspend fun getExistingNames(destinationDir: StorageLocation): Set<String> {
        val result = directoryRepository.list(destinationDir, showHidden = true)
        return when (result) {
            is FileOperationResult.Success -> result.data.map { it.name }.toSet()
            is FileOperationResult.Failure -> error("Cannot read destination: ${result.error}")
            FileOperationResult.Cancelled -> throw CancellationException("Destination listing cancelled")
            is FileOperationResult.Completed -> error("Unexpected listing completion")
        }
    }

    override suspend fun detectConflicts(
        sources: List<StorageLocation>,
        destinationDir: StorageLocation
    ): List<FileConflict> {
        currentCoroutineContext().ensureActive()
        require(detailedMetadataReader.readDetailedMetadata(destinationDir).isDirectory) { "Destination must be a directory" }
        val existingNames = getExistingNames(destinationDir)
        val plannedNames = mutableSetOf<String>()
        val conflicts = mutableListOf<FileConflict>()

        for (source in sources) {
            currentCoroutineContext().ensureActive()
            val metadata = detailedMetadataReader.readDetailedMetadata(source)

            val sourceName = extractItemName(source, metadata)
            val isDir = metadata.isDirectory

            // Proteksi: jangan memproses kontainer ke dalam dirinya sendiri atau sub-direktorinya
            require(!isDir || !destinationDir.isSameOrDescendantOf(source)) { "Cannot transfer a directory into itself or its descendants" }

            val batchCollision = !plannedNames.add(sourceName.lowercase(java.util.Locale.ROOT))
            val hasConflict = batchCollision || existingNames.any { it.equals(sourceName, ignoreCase = true) }
            if (hasConflict) {
                conflicts.add(
                    FileConflict(
                        source = source,
                        sourceName = sourceName,
                        targetName = sourceName,
                        isDirectory = isDir,
                        destinationDir = destinationDir,
                        isBatchCollision = batchCollision
                    )
                )
            }
        }

        return conflicts
    }

    private fun extractItemName(location: StorageLocation, metadata: FileDetailedMetadata?): String {
        if (location.path.startsWith(StorageConstants.CONTENT_SCHEME_PREFIX)) {
            val metaName = metadata?.fileName
            if (!metaName.isNullOrBlank()) {
                return metaName
            }
        }
        return location.path.trimEnd('/').substringAfterLast('/')
    }
}
