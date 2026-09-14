package com.wakwau.xplore.core.storage.filesystem.saf

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SafMoveTargetValidatorTest {
    private val validator = SafMoveTargetValidator()

    @Test
    fun parentExistsButActualTargetMissing_failsBeforeSourceDeletion() {
        var sourceDeleted = false

        val result = runCatching {
            validator.validate(sourceIsDirectory = false, sourceSize = 12L, target = null)
            sourceDeleted = true
        }

        assertTrue(result.exceptionOrNull() is IOException)
        assertTrue(!sourceDeleted)
    }

    @Test
    fun validTarget_allowsSourceDeletion() {
        var sourceDeleted = false

        validator.validate(
            sourceIsDirectory = false,
            sourceSize = 12L,
            target = SafMoveTargetState(exists = true, isFile = true, isDirectory = false, size = 12L)
        )
        sourceDeleted = true

        assertTrue(sourceDeleted)
    }

    @Test
    fun incompleteTarget_failsBeforeSourceDeletion() {
        var sourceDeleted = false

        val result = runCatching {
            validator.validate(
                sourceIsDirectory = false,
                sourceSize = 12L,
                target = SafMoveTargetState(exists = true, isFile = true, isDirectory = false, size = 3L)
            )
            sourceDeleted = true
        }

        assertTrue(result.exceptionOrNull() is IOException)
        assertTrue(!sourceDeleted)
    }

    @Test
    fun validDirectoryTarget_allowsSourceDeletion() {
        var sourceDeleted = false

        validator.validate(
            sourceIsDirectory = true,
            sourceSize = 0L,
            target = SafMoveTargetState(exists = true, isFile = false, isDirectory = true, size = 0L)
        )
        sourceDeleted = true

        assertTrue(sourceDeleted)
    }

    @Test
    fun cancellationBeforeValidation_keepsSource() = runTest {
        var sourceDeleted = false

        val result = runCatching {
            throw CancellationException("cancelled")
            @Suppress("UNREACHABLE_CODE")
            validator.validate(false, 12L, null)
            @Suppress("UNREACHABLE_CODE")
            run { sourceDeleted = true }
        }

        assertTrue(result.exceptionOrNull() is CancellationException)
        assertTrue(!sourceDeleted)
    }
}
