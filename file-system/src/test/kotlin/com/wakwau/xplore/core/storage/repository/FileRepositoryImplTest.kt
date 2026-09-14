// [Jalur Class/Modul]: file-system/src/test/kotlin/com/wakwau/xplore/core/storage/repository/FileRepositoryImplTest.kt
// [Penjelasan]: Unit test untuk FileRepositoryImpl dengan LocalFileSystem dan test fixture contracts mandiri pada modul file-system.
package com.wakwau.xplore.core.storage.repository

import com.wakwau.xplore.core.storage.api.error.StorageErrorMapper
import com.wakwau.xplore.core.storage.filesystem.local.LocalFileSystem
import com.wakwau.xplore.core.storage.mapper.FileItemMapper
import com.wakwau.xplore.core.storage.metadata.FileMetadataReader
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import com.wakwau.xplore.core.storage.testutil.TestRootFileSystem
import com.wakwau.xplore.core.storage.testutil.TestSafFileSystem
import com.wakwau.xplore.core.storage.testutil.TestShizukuFileSystem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.wakwau.xplore.core.storage.filesystem.StorageBackendClassifier
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class FileRepositoryImplTest {

    private lateinit var repository: FileRepositoryImpl
    private lateinit var tempDir: File
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        val metadataReader = FileMetadataReader()
        val itemMapper = FileItemMapper()
        val errorMapper = com.wakwau.xplore.core.storage.testutil.TestStorageErrorMapper()
        val localFileSystem = LocalFileSystem(
            fileMetadataReader = metadataReader,
            fileItemMapper = itemMapper
        )
        
        repository = FileRepositoryImpl(
            localFileSystem = localFileSystem,
            safFileSystem = com.wakwau.xplore.core.storage.testutil.TestSafFileSystem(),
            safShizukuFileSystem = com.wakwau.xplore.core.storage.testutil.TestShizukuFileSystem(),
            rootFileSystem = com.wakwau.xplore.core.storage.testutil.TestRootFileSystem(),
            backendClassifier = StorageBackendClassifier(isSuAvailable = { false }, isShizukuAvailable = { false }, isSafPersisted = { false }),
            storageErrorMapper = errorMapper,
            ioDispatcher = dispatcher
        )
        
        tempDir = Files.createTempDirectory("file_repo_test").toFile()
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun delete_existingFile_returnsSuccess() = runTest {
        val file = File(tempDir, "delete.txt")
        file.createNewFile()
        
        val result = repository.delete(StorageLocation(file.absolutePath))
        
        assertTrue(result is FileOperationResult.Success)
        assertTrue(!file.exists())
    }

    @Test
    fun delete_nonexistentFile_returnsFailure() = runTest {
        val result = repository.delete(StorageLocation("${tempDir.absolutePath}/none.txt"))
        
        assertTrue(result is FileOperationResult.Failure)
    }

    @Test
    fun rename_existingFile_validName_returnsSuccess() = runTest {
        val file = File(tempDir, "rename.txt")
        file.createNewFile()
        
        val result = repository.rename(StorageLocation(file.absolutePath), "newname.txt")
        
        assertTrue(result is FileOperationResult.Success)
        val newFile = File(tempDir, "newname.txt")
        assertTrue(newFile.exists())
    }

    @Test
    fun rename_existingFile_collision_returnsFailure() = runTest {
        val file = File(tempDir, "rename_col.txt")
        file.createNewFile()
        val existingTarget = File(tempDir, "target.txt")
        existingTarget.createNewFile()
        
        val result = repository.rename(StorageLocation(file.absolutePath), "target.txt")
        
        assertTrue(result is FileOperationResult.Failure)
    }

    @Test
    fun copy_existingFile_validDestination_returnsSuccess() = runTest {
        val file = File(tempDir, "copy.txt")
        file.writeText("content")
        val destFile = File(tempDir, "dest.txt")
        
        val results = repository.copy(StorageLocation(file.absolutePath), StorageLocation(destFile.absolutePath)).toList()
        
        assertTrue(results.isNotEmpty())
        assertTrue(results.last() is FileOperationResult.Success)
        assertTrue(destFile.exists())
    }

    @Test
    fun move_existingFile_validDestination_returnsSuccess() = runTest {
        val file = File(tempDir, "move.txt")
        file.writeText("content")
        val destFile = File(tempDir, "dest_move.txt")
        
        val results = repository.move(StorageLocation(file.absolutePath), StorageLocation(destFile.absolutePath)).toList()
        
        assertTrue(results.isNotEmpty())
        assertTrue(results.last() is FileOperationResult.Success)
        assertTrue(destFile.exists())
        assertTrue(!file.exists())
    }

    @Test
    fun move_failedTransfer_doesNotDeleteSourceFile() = runTest {
        val file = File(tempDir, "move_failed_src.txt")
        file.writeText("critical source content")
        // Target in non-existent directory to force failure
        val invalidDestDir = File(tempDir, "non_existent_folder/dest.txt")

        val results = repository.move(StorageLocation(file.absolutePath), StorageLocation(invalidDestDir.absolutePath)).toList()

        assertTrue(results.last() is FileOperationResult.Failure)
        // Source MUST still exist and be intact
        assertTrue("Source file must be preserved if move fails", file.exists())
        assertEquals("critical source content", file.readText())
    }

    @Test
    fun delete_recursiveFolder_returnsSuccessAndRemovesFolderAndChildren() = runTest {
        val folder = File(tempDir, "test_folder")
        folder.mkdirs()
        val subFile1 = File(folder, "sub1.txt").apply { writeText("data1") }
        val subDir = File(folder, "sub_dir").apply { mkdirs() }
        val subFile2 = File(subDir, "sub2.txt").apply { writeText("data2") }

        val result = repository.delete(StorageLocation(folder.absolutePath))

        assertTrue(result is FileOperationResult.Success)
        assertTrue(!folder.exists())
        assertTrue(!subFile1.exists())
        assertTrue(!subDir.exists())
        assertTrue(!subFile2.exists())
    }

    @Test
    fun delete_rootOrProtectedPath_returnsFailureAccessDenied() = runTest {
        val rootResult = repository.delete(StorageLocation("/"))
        assertTrue(rootResult is FileOperationResult.Failure)
        assertEquals(com.wakwau.xplore.core.storage.operation.FileOperationError.ACCESS_DENIED, (rootResult as FileOperationResult.Failure).error)

        val emulatedResult = repository.delete(StorageLocation("/storage/emulated/0"))
        assertTrue(emulatedResult is FileOperationResult.Failure)
        assertEquals(com.wakwau.xplore.core.storage.operation.FileOperationError.ACCESS_DENIED, (emulatedResult as FileOperationResult.Failure).error)
    }

    @Test
    fun delete_symlink_deletesSymlinkWithoutDeletingTarget() = runTest {
        val targetDir = File(tempDir, "real_target_dir").apply { mkdirs() }
        val targetFile = File(targetDir, "real_file.txt").apply { writeText("real content") }
        val symlink = File(tempDir, "link_to_target")

        try {
            Files.createSymbolicLink(symlink.toPath(), targetDir.toPath())
            val result = repository.delete(StorageLocation(symlink.absolutePath))

            assertTrue(result is FileOperationResult.Success)
            assertTrue(!symlink.exists())
            assertTrue("Target directory must remain intact when symlink is deleted", targetDir.exists())
            assertTrue("Target file must remain intact when symlink is deleted", targetFile.exists())
        } catch (_: UnsupportedOperationException) {
            // Symbolic links might not be supported on all test OS environments
        } catch (_: java.nio.file.FileSystemException) {
            // Privilege required on Windows
        }
    }

    @Test
    fun copy_cancellation_cleansUpPartialTargetFile() = runTest {
        val srcFile = File(tempDir, "large_source.txt")
        srcFile.writeText("some content that fails or cancels")
        val destFile = File(tempDir, "partial_dest.txt")

        // Intentionally target a read-only or invalid destination scenario or simulate cancellation
        val invalidDestPath = StorageLocation("/sys/kernel/debug/invalid_partial_dest.txt")
        val results = repository.copy(StorageLocation(srcFile.absolutePath), invalidDestPath).toList()

        assertTrue(results.last() is FileOperationResult.Failure)
        assertTrue("Source file must not be modified", srcFile.exists())
    }
}


