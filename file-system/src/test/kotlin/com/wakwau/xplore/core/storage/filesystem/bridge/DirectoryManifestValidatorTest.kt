package com.wakwau.xplore.core.storage.filesystem.bridge

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryManifestValidatorTest {
    private val validator = DirectoryManifestValidator()

    @Test
    fun completeSubtree_allowsSourceDeletion() = runTest {
        var sourceDeleted = false

        validator.validate(completeManifest, completeManifest)
        sourceDeleted = true

        assertTrue(sourceDeleted)
    }

    @Test
    fun missingChild_rejectsValidationAndKeepsSource() = runTest {
        assertValidationFailureKeepsSource(completeManifest.dropLast(1))
    }

    @Test
    fun wrongFileSize_rejectsValidationAndKeepsSource() = runTest {
        val destination = completeManifest.map {
            if (it.relativePath == "root.txt") it.copy(size = it.size + 1) else it
        }

        assertValidationFailureKeepsSource(destination)
    }

    @Test
    fun missingNestedChild_rejectsValidationAndKeepsSource() = runTest {
        val destination = completeManifest.filterNot { it.relativePath == "nested/child.txt" }

        assertValidationFailureKeepsSource(destination)
    }

    @Test
    fun emptyDirectory_allowsSourceDeletion() = runTest {
        var sourceDeleted = false

        validator.validate(emptyList(), emptyList())
        sourceDeleted = true

        assertTrue(sourceDeleted)
    }

    @Test
    fun cancellationDuringValidation_keepsSource() = runTest {
        var sourceDeleted = false
        val cancelledJob = Job().apply { cancel() }

        val result = runCatching {
            withContext(cancelledJob) {
                validator.validate(completeManifest, completeManifest)
                sourceDeleted = true
            }
        }

        assertTrue(result.exceptionOrNull() is CancellationException)
        assertFalse(sourceDeleted)
    }

    private suspend fun assertValidationFailureKeepsSource(destination: List<DirectoryManifestEntry>) {
        var sourceDeleted = false

        val result = runCatching {
            validator.validate(completeManifest, destination)
            sourceDeleted = true
        }

        assertTrue(result.exceptionOrNull() is IOException)
        assertFalse(sourceDeleted)
    }

    private companion object {
        val completeManifest = listOf(
            DirectoryManifestEntry("nested", isDirectory = true, size = 0),
            DirectoryManifestEntry("nested/child.txt", isDirectory = false, size = 12),
            DirectoryManifestEntry("root.txt", isDirectory = false, size = 7)
        )
    }
}
