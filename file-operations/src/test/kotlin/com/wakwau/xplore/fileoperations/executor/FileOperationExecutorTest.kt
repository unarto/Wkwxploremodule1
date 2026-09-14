package com.wakwau.xplore.fileoperations.executor

import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.BackgroundOperationType
import com.wakwau.xplore.core.storage.operation.FileOperationError
import com.wakwau.xplore.core.storage.operation.FileOperationProgress
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import com.wakwau.xplore.core.storage.repository.FileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOperationExecutorTest {
    @Test
    fun copy_dispatchesOnlyToCopyDomainOperation() = runTest {
        val repository = RecordingFileRepository()
        val executor = FileOperationExecutor(repository)

        val terminal = executor.execute(request(BackgroundOperationType.COPY)) { }

        assertNull(terminal)
        assertEquals(1, repository.copyCalls)
        assertEquals(0, repository.moveCalls)
        assertEquals(0, repository.deleteCalls)
    }

    @Test
    fun move_dispatchesOnlyToMoveDomainOperation() = runTest {
        val repository = RecordingFileRepository()
        val executor = FileOperationExecutor(repository)

        val terminal = executor.execute(request(BackgroundOperationType.MOVE)) { }

        assertNull(terminal)
        assertEquals(0, repository.copyCalls)
        assertEquals(1, repository.moveCalls)
        assertEquals(0, repository.deleteCalls)
    }

    @Test
    fun delete_dispatchesOnlyToDeleteDomainOperation() = runTest {
        val repository = RecordingFileRepository()
        val executor = FileOperationExecutor(repository)

        val terminal = executor.execute(
            FileOperationRequest(BackgroundOperationType.DELETE, sources = listOf(source))
        ) { }

        assertNull(terminal)
        assertEquals(0, repository.copyCalls)
        assertEquals(0, repository.moveCalls)
        assertEquals(1, repository.deleteCalls)
    }

    @Test
    fun multiSourceFailure_stopsBeforeNextSource() = runTest {
        val repository = RecordingFileRepository().apply {
            copyResult = flowOf(FileOperationResult.Failure(FileOperationError.IO_ERROR))
        }
        val executor = FileOperationExecutor(repository)
        val second = StorageLocation("/source/second.txt")

        val terminal = executor.execute(
            FileOperationRequest(
                BackgroundOperationType.COPY,
                sources = listOf(source, second),
                destination = destination
            )
        ) { }

        assertEquals(FileOperationResult.Failure(FileOperationError.IO_ERROR), terminal)
        assertEquals(1, repository.copyCalls)
    }

    @Test
    fun cancellation_isPropagatedAndDoesNotExecuteAgain() = runTest {
        val repository = RecordingFileRepository().apply {
            copyResult = flow { throw CancellationException("cancelled") }
        }
        val executor = FileOperationExecutor(repository)

        val result = runCatching {
            executor.execute(request(BackgroundOperationType.COPY)) { }
        }

        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(1, repository.copyCalls)
    }

    @Test
    fun progressIsForwardedWithoutDuplicateExecution() = runTest {
        val progress = FileOperationResult.Success(FileOperationProgress(4, 4, "file.txt"))
        val repository = RecordingFileRepository().apply { copyResult = flowOf(progress) }
        val forwarded = mutableListOf<FileOperationResult<FileOperationProgress>>()

        val terminal = FileOperationExecutor(repository).execute(request(BackgroundOperationType.COPY)) {
            forwarded += it
        }

        assertNull(terminal)
        assertEquals(listOf(progress), forwarded)
        assertEquals(1, repository.copyCalls)
    }

    private fun request(type: BackgroundOperationType) = FileOperationRequest(
        type = type,
        sources = listOf(source),
        destination = destination
    )

    private class RecordingFileRepository : FileRepository {
        var copyCalls = 0
        var moveCalls = 0
        var deleteCalls = 0
        var copyResult: Flow<FileOperationResult<FileOperationProgress>> = emptyTransfer()
        var moveResult: Flow<FileOperationResult<FileOperationProgress>> = emptyTransfer()

        override fun copy(
            source: StorageLocation,
            destination: StorageLocation
        ): Flow<FileOperationResult<FileOperationProgress>> {
            copyCalls++
            return copyResult
        }

        override fun move(
            source: StorageLocation,
            destination: StorageLocation
        ): Flow<FileOperationResult<FileOperationProgress>> {
            moveCalls++
            return moveResult
        }

        override suspend fun delete(location: StorageLocation): FileOperationResult<Unit> {
            deleteCalls++
            return FileOperationResult.Success(Unit)
        }

        override suspend fun createDirectory(
            location: StorageLocation,
            name: String
        ): FileOperationResult<FileItem> = error("Not used")

        override suspend fun rename(
            location: StorageLocation,
            newName: String
        ): FileOperationResult<FileItem> = error("Not used")

        private companion object {
            fun emptyTransfer(): Flow<FileOperationResult<FileOperationProgress>> = flowOf()
        }
    }

    private companion object {
        val source = StorageLocation("/source/file.txt")
        val destination = StorageLocation("/destination")
    }
}
