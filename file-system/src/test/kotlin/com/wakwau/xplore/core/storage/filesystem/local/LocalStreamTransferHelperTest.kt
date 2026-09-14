package com.wakwau.xplore.core.storage.filesystem.local

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class LocalStreamTransferHelperTest {
    @Test
    fun overwriteSuccess_replacesExistingTarget() = runTest {
        withFiles { source, destination ->
            LocalStreamTransferHelper().copySingleFile(source, destination, source.length()) { _, _ -> }

            assertEquals("new content", destination.readText())
            assertNoTemporaryFiles(destination)
        }
    }

    @Test
    fun overwriteFailure_preservesExistingTargetAndCleansTemporaryFile() = runTest {
        withFiles { source, destination ->
            val result = runCatching {
                LocalStreamTransferHelper().copySingleFile(source, destination, source.length()) { _, _ ->
                    throw IOException("forced failure")
                }
            }

            assertTrue(result.exceptionOrNull() is IOException)
            assertEquals("old content", destination.readText())
            assertNoTemporaryFiles(destination)
        }
    }

    @Test
    fun overwriteCancellation_preservesExistingTargetAndCleansTemporaryFile() = runTest {
        withFiles { source, destination ->
            val result = runCatching {
                LocalStreamTransferHelper().copySingleFile(source, destination, source.length()) { _, _ ->
                    throw CancellationException("cancelled")
                }
            }

            assertTrue(result.exceptionOrNull() is CancellationException)
            assertEquals("old content", destination.readText())
            assertNoTemporaryFiles(destination)
        }
    }

    @Test
    fun moveOverwriteFailure_preservesSourceAndExistingTarget() = runTest {
        withFiles { source, destination ->
            val result = runCatching {
                LocalStreamTransferHelper().copySingleFile(source, destination, source.length()) { _, _ ->
                    throw IOException("forced move-copy failure")
                }
                source.delete()
            }

            assertTrue(result.exceptionOrNull() is IOException)
            assertEquals("new content", source.readText())
            assertEquals("old content", destination.readText())
            assertNoTemporaryFiles(destination)
        }
    }

    private suspend fun withFiles(block: suspend (File, File) -> Unit) {
        val directory = Files.createTempDirectory("safe-local-replacement").toFile()
        val source = File(directory, "source.txt").apply { writeText("new content") }
        val destination = File(directory, "destination.txt").apply { writeText("old content") }
        try {
            block(source, destination)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun assertNoTemporaryFiles(destination: File) {
        val temporaryFiles = destination.parentFile.listFiles().orEmpty().filter {
            it.name.startsWith(".${destination.name}.wkw-") && it.name.endsWith(".tmp")
        }
        assertTrue("Partial temporary files must be cleaned", temporaryFiles.isEmpty())
    }
}
