// [Jalur Class/Modul]: file-operations/src/main/kotlin/com/wakwau/xplore/fileoperations/conflict/DefaultConflictDetector.kt
// [Penjelasan]: Implementasi ConflictDetector untuk mendeteksi potensi konflik nama berkas dan folder di direktori tujuan, berkomunikasi murni melalui abstraksi DirectoryRepository dan DetailedMetadataReader dari core-storage-api tanpa menyentuh implementasi filesystem konkret.
package com.wakwau.xplore.fileoperations.conflict

import com.wakwau.xplore.fileoperations.conflict.ConflictDetector
import com.wakwau.xplore.fileoperations.conflict.FileConflict
import com.wakwau.xplore.core.storage.constant.StorageConstants
import com.wakwau.xplore.core.storage.metadata.DetailedMetadataReader
import com.wakwau.xplore.core.storage.model.FileDetailedMetadata
import com.wakwau.xplore.core.storage.model.StorageLocation
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
            else -> emptySet()
        }
    }

    override suspend fun detectConflicts(
        sources: List<StorageLocation>,
        destinationDir: StorageLocation
    ): List<FileConflict> {
        val existingNames = getExistingNames(destinationDir)
        val conflicts = mutableListOf<FileConflict>()

        for (source in sources) {
            val metadata = runCatching {
                detailedMetadataReader.readDetailedMetadata(source)
            }.getOrNull()

            val sourceName = extractItemName(source, metadata)
            val isDir = metadata?.isDirectory ?: false

            // Proteksi: jangan memproses kontainer ke dalam dirinya sendiri atau sub-direktorinya
            if (isDir && isSelfOrInside(source, destinationDir)) {
                continue
            }

            val hasConflict = existingNames.any { it.equals(sourceName, ignoreCase = true) }
            if (hasConflict) {
                conflicts.add(
                    FileConflict(
                        source = source,
                        sourceName = sourceName,
                        targetName = sourceName,
                        isDirectory = isDir,
                        destinationDir = destinationDir
                    )
                )
            }
        }

        return conflicts
    }

    private fun isSelfOrInside(source: StorageLocation, destination: StorageLocation): Boolean {
        if (source.rootId.isNotEmpty() && destination.rootId.isNotEmpty() && source.rootId != destination.rootId) {
            return false
        }
        if (source.path.startsWith(StorageConstants.CONTENT_SCHEME_PREFIX) || destination.path.startsWith(StorageConstants.CONTENT_SCHEME_PREFIX)) {
            return source.path.trimEnd('/') == destination.path.trimEnd('/')
        }
        val sourceNorm = normalizePath(source.path)
        val destNorm = normalizePath(destination.path)
        if (sourceNorm.isEmpty() || destNorm.isEmpty()) {
            return false
        }
        return destNorm == sourceNorm || (sourceNorm == "/" && destNorm.startsWith("/")) || destNorm.startsWith("$sourceNorm/")
    }

    private fun normalizePath(rawPath: String): String {
        val path = rawPath.replace('\\', '/')
        if (path.isEmpty()) return ""
        val isAbsolute = path.startsWith('/')
        val segments = mutableListOf<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "", "." -> continue
                ".." -> if (segments.isNotEmpty() && segments.last() != "..") {
                    segments.removeAt(segments.size - 1)
                } else if (!isAbsolute) {
                    segments.add("..")
                }
                else -> segments.add(segment)
            }
        }
        val joined = segments.joinToString("/")
        return if (isAbsolute) "/$joined" else joined
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
