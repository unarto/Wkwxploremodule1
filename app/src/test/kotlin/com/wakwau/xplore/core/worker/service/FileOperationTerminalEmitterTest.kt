package com.wakwau.xplore.core.worker.service

import com.wakwau.xplore.core.storage.operation.BackgroundOperationType
import com.wakwau.xplore.core.storage.operation.BackgroundOperationEvent
import com.wakwau.xplore.core.storage.operation.FileOperationError
import com.wakwau.xplore.core.storage.operation.FileOperationProgressDispatcher
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOperationTerminalEmitterTest {
    private val operationId = "operation-1"

    @Test
    fun success_emitsCompletedExactlyOnce() = runTest {
        val dispatcher = RecordingDispatcher()

        FileOperationTerminalEmitter.execute(operationId, BackgroundOperationType.COPY, dispatcher) { null }

        assertEquals(listOf(BackgroundOperationEvent(operationId, FileOperationResult.Completed(BackgroundOperationType.COPY))), dispatcher.events)
    }

    @Test
    fun failure_emitsFailureWithoutCompleted() = runTest {
        val dispatcher = RecordingDispatcher()

        FileOperationTerminalEmitter.execute(operationId, BackgroundOperationType.MOVE, dispatcher) {
            FileOperationResult.Failure(FileOperationError.IO_ERROR)
        }

        assertEquals(FileOperationResult.Failure(FileOperationError.IO_ERROR), dispatcher.events.single().result)
        assertTrue(dispatcher.events.none { it.result is FileOperationResult.Completed })
    }

    @Test
    fun exception_emitsFailureWithoutCompleted() = runTest {
        val dispatcher = RecordingDispatcher()

        FileOperationTerminalEmitter.execute(operationId, BackgroundOperationType.DELETE, dispatcher) {
            error("unexpected executor failure")
        }

        assertEquals(FileOperationResult.Failure(FileOperationError.UNKNOWN), dispatcher.events.single().result)
        assertTrue(dispatcher.events.none { it.result is FileOperationResult.Completed })
    }

    @Test
    fun cancellation_emitsCancelledOnceAndPropagatesCancellation() = runTest {
        val dispatcher = RecordingDispatcher()

        val result = runCatching {
            FileOperationTerminalEmitter.execute(operationId, BackgroundOperationType.COPY, dispatcher) {
                throw CancellationException("cancelled")
            }
        }

        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(FileOperationResult.Cancelled, dispatcher.events.single().result)
        assertEquals(operationId, dispatcher.events.single().operationId)
        assertTrue(dispatcher.events.none { it.result is FileOperationResult.Completed })
    }

    private class RecordingDispatcher : FileOperationProgressDispatcher {
        val events = mutableListOf<BackgroundOperationEvent>()
        override val progressFlow: Flow<BackgroundOperationEvent> = emptyFlow()

        override suspend fun emitProgress(event: BackgroundOperationEvent) {
            events += event
        }
    }
}
