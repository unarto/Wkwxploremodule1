package com.wakwau.xplore.core.storage.filesystem.bridge

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException

internal data class DirectoryManifestEntry(
    val relativePath: String,
    val isDirectory: Boolean,
    val size: Long
)

internal class DirectoryManifestValidator {
    suspend fun validate(source: List<DirectoryManifestEntry>, destination: List<DirectoryManifestEntry>) {
        currentCoroutineContext().ensureActive()
        val expected = source.associateBy { it.relativePath }
        val actual = destination.associateBy { it.relativePath }
        if (expected.size != source.size || actual.size != destination.size) {
            throw IOException("Move validation failed: duplicate relative paths in directory manifest")
        }
        if (expected.keys != actual.keys) {
            throw IOException("Move validation failed: destination subtree entries differ from source")
        }

        for ((path, sourceEntry) in expected) {
            currentCoroutineContext().ensureActive()
            val destinationEntry = actual.getValue(path)
            if (sourceEntry.isDirectory != destinationEntry.isDirectory) {
                throw IOException("Move validation failed: entry type differs at $path")
            }
            if (!sourceEntry.isDirectory && sourceEntry.size != destinationEntry.size) {
                throw IOException("Move validation failed: file size differs at $path")
            }
        }

        val sourceFiles = source.count { !it.isDirectory }
        val destinationFiles = destination.count { !it.isDirectory }
        val sourceBytes = source.filterNot { it.isDirectory }.sumOf { it.size }
        val destinationBytes = destination.filterNot { it.isDirectory }.sumOf { it.size }
        if (sourceFiles != destinationFiles || sourceBytes != destinationBytes) {
            throw IOException("Move validation failed: destination file count or total bytes differ from source")
        }
    }
}
