package com.wakwau.xplore.core.storage.filesystem.local

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.toList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class LocalDirectoryOperationHelperTest {
    @Test
    fun emptyDirectory_isCopiedSuccessfully() = runTest {
        withTempDirectory { root ->
            val source = File(root, "source").apply { mkdir() }
            val destination = File(root, "destination")

            LocalDirectoryOperationHelper().copyDirectoryRecursively(source, destination, 0L) { _, _ -> }

            assertTrue(destination.isDirectory)
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun nullListing_throwsIoFailure() = runTest {
        withTempDirectory { root ->
            val source = File(root, "source").apply { mkdir() }
            val destination = File(root, "destination")
            val helper = LocalDirectoryOperationHelper(directoryEntries = { null })

            val result = runCatching {
                helper.copyDirectoryRecursively(source, destination, 0L) { _, _ -> }
            }

            assertTrue(result.exceptionOrNull() is IOException)
        }
    }

    @Test
    fun permissionError_isPropagated() = runTest {
        withTempDirectory { root ->
            val source = File(root, "source").apply { mkdir() }
            val helper = LocalDirectoryOperationHelper(directoryEntries = { throw SecurityException("denied") })

            val result = runCatching {
                helper.copyDirectoryRecursively(source, File(root, "destination"), 0L) { _, _ -> }
            }

            assertTrue(result.exceptionOrNull() is SecurityException)
        }
    }

    @Test
    fun moveListingFailure_preservesSource() = runTest {
        withTempDirectory { root ->
            val source = File(root, "source").apply {
                mkdir()
                File(this, "important.txt").writeText("keep me")
            }
            val helper = LocalDirectoryOperationHelper(directoryEntries = { null })
            val fileSystem = LocalFileSystem(directoryOperationHelper = helper)
            val destination = File(root, "missing-parent/destination")

            val result = runCatching {
                fileSystem.move(source.absolutePath, destination.absolutePath).toList()
            }

            assertTrue(result.exceptionOrNull() is IOException)
            assertTrue(source.isDirectory)
            assertEquals("keep me", File(source, "important.txt").readText())
        }
    }

    @Test
    fun cancellation_isNotConvertedToSuccess() = runTest {
        withTempDirectory { root ->
            val source = File(root, "source").apply {
                mkdir()
                File(this, "item.txt").writeText("content")
            }
            val result = runCatching {
                LocalDirectoryOperationHelper().copyDirectoryRecursively(
                    source,
                    File(root, "destination"),
                    7L
                ) { _, _ -> throw CancellationException("cancelled") }
            }

            assertTrue(result.exceptionOrNull() is CancellationException)
        }
    }

    @Test
    fun transactionalCopy_failureRollsBackOperationOwnedSubtree() = runTest {
        withTempDirectory { root ->
            val source = File(root, "source").apply {
                mkdir()
                File(this, "first.txt").writeText("first")
                File(this, "second.txt").writeText("second")
            }
            val destination = File(root, "destination")
            var copiedFiles = 0

            val result = runCatching {
                LocalDirectoryOperationHelper().copyDirectoryTransactionally(source, destination, 11L) { _, _ ->
                    copiedFiles++
                    if (copiedFiles == 1) throw IOException("injected child failure")
                }
            }

            assertTrue(result.exceptionOrNull() is IOException)
            assertTrue(!destination.exists())
            assertTrue(root.listFiles().orEmpty().none { it.name.contains(".wkw-") })
        }
    }

    @Test
    fun transactionalCopy_failurePreservesExistingDestination() = runTest {
        withTempDirectory { root ->
            val source = File(root, "source").apply {
                mkdir()
                File(this, "replacement.txt").writeText("replacement")
            }
            val destination = File(root, "destination").apply {
                mkdir()
                File(this, "original.txt").writeText("original")
            }

            val result = runCatching {
                LocalDirectoryOperationHelper().copyDirectoryTransactionally(source, destination, 11L) { _, _ ->
                    throw IOException("injected child failure")
                }
            }

            assertTrue(result.exceptionOrNull() is IOException)
            assertEquals("original", File(destination, "original.txt").readText())
            assertTrue(!File(destination, "replacement.txt").exists())
        }
    }

    @Test
    fun transactionalCopy_cancellationRollsBackAndPropagates() = runTest {
        withTempDirectory { root ->
            val source = File(root, "source").apply {
                mkdir()
                File(this, "item.txt").writeText("content")
            }
            val destination = File(root, "destination")

            val result = runCatching {
                LocalDirectoryOperationHelper().copyDirectoryTransactionally(source, destination, 7L) { _, _ ->
                    throw CancellationException("cancelled")
                }
            }

            assertTrue(result.exceptionOrNull() is CancellationException)
            assertTrue(!destination.exists())
            assertTrue(source.exists())
        }
    }

    @Test
    fun transactionalCopy_successReplacesExistingDestinationAfterCompleteCopy() = runTest {
        withTempDirectory { root ->
            val source = File(root, "source").apply {
                mkdir()
                File(this, "nested").mkdir()
                File(this, "nested/item.txt").writeText("complete")
            }
            val destination = File(root, "destination").apply {
                mkdir()
                File(this, "old.txt").writeText("old")
            }

            LocalDirectoryOperationHelper().copyDirectoryTransactionally(source, destination, 8L) { _, _ -> }

            assertTrue(!File(destination, "old.txt").exists())
            assertEquals("complete", File(destination, "nested/item.txt").readText())
            assertTrue(root.listFiles().orEmpty().none { it.name.contains(".wkw-") })
        }
    }

    @Test
    fun moveValidationFailure_restoresExistingDestination() = runTest {
        withTempDirectory { root ->
            val source = File(root, "source").apply { mkdir(); File(this, "new.txt").writeText("new") }
            val destination = File(root, "destination").apply { mkdir(); File(this, "old.txt").writeText("old") }

            val result = runCatching {
                LocalDirectoryOperationHelper().copyDirectoryTransactionally(source, destination, 3L, afterPublish = {
                    throw IOException("validation failed")
                }) { _, _ -> }
            }

            assertTrue(result.isFailure)
            assertEquals("old", File(destination, "old.txt").readText())
            assertTrue(!File(destination, "new.txt").exists())
            assertTrue(source.exists())
        }
    }

    @Test
    fun cancellationAfterPublish_restoresExistingDestination() = runTest {
        withTempDirectory { root ->
            val source = File(root, "source").apply { mkdir(); File(this, "new.txt").writeText("new") }
            val destination = File(root, "destination").apply { mkdir(); File(this, "old.txt").writeText("old") }

            val result = runCatching {
                LocalDirectoryOperationHelper().copyDirectoryTransactionally(source, destination, 3L, afterPublish = {
                    throw CancellationException("cancelled after publish")
                }) { _, _ -> }
            }

            assertTrue(result.exceptionOrNull() is CancellationException)
            assertEquals("old", File(destination, "old.txt").readText())
            assertTrue(source.exists())
        }
    }

    @Test
    fun successfulValidation_deletesSourceThenCleansBackup() = runTest {
        withTempDirectory { root ->
            val source = File(root, "source").apply { mkdir(); File(this, "new.txt").writeText("new") }
            val destination = File(root, "destination").apply { mkdir(); File(this, "old.txt").writeText("old") }
            val helper = LocalDirectoryOperationHelper()

            helper.copyDirectoryTransactionally(source, destination, 3L, afterPublish = {
                helper.validateDirectoryTree(source, destination)
                assertTrue(source.deleteRecursively())
            }) { _, _ -> }

            assertTrue(!source.exists())
            assertEquals("new", File(destination, "new.txt").readText())
            assertTrue(root.listFiles().orEmpty().none { it.name.contains(".wkw-") })
        }
    }

    @Test
    fun nestedMismatch_doesNotLoseExistingDestination() = runTest {
        withTempDirectory { root ->
            val source = File(root, "source").apply {
                mkdir(); File(this, "nested").mkdir(); File(this, "nested/item.txt").writeText("new")
            }
            val destination = File(root, "destination").apply { mkdir(); File(this, "old.txt").writeText("old") }
            val helper = LocalDirectoryOperationHelper()

            val result = runCatching {
                helper.copyDirectoryTransactionally(source, destination, 3L, afterPublish = {
                    File(destination, "nested/item.txt").delete()
                    helper.validateDirectoryTree(source, destination)
                }) { _, _ -> }
            }

            assertTrue(result.isFailure)
            assertEquals("old", File(destination, "old.txt").readText())
            assertTrue(source.exists())
        }
    }

    private suspend fun withTempDirectory(block: suspend (File) -> Unit) {
        val directory = Files.createTempDirectory("local-directory-helper").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
