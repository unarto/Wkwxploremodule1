package com.wakwau.xplore.core.worker.service

import com.wakwau.xplore.core.storage.operation.BackgroundOperationEvent
import com.wakwau.xplore.core.storage.operation.BackgroundOperationType
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

class ForegroundOperationExecutionGateTest {
    @Test
    fun foregroundSuccess_executesOnceAndEmitsOneCompleted() = runTest {
        val dispatcher = RecordingDispatcher()
        var executions = 0
        var cleanups = 0

        ForegroundOperationExecutionGate.execute(
            operationId = OPERATION_ID,
            operationType = BackgroundOperationType.COPY,
            dispatcher = dispatcher,
            startForeground = {},
            operation = { executions++; null },
            onStartupFailure = {},
            cleanup = { cleanups++ }
        )

        assertEquals(1, executions)
        assertEquals(1, cleanups)
        assertEquals(
            BackgroundOperationEvent(OPERATION_ID, FileOperationResult.Completed(BackgroundOperationType.COPY)),
            dispatcher.events.single()
        )
    }

    @Test
    fun foregroundFailure_neverExecutesAndEmitsOneCorrelatedFailure() = runTest {
        val dispatcher = RecordingDispatcher()
        val startupError = IllegalStateException("foreground rejected")
        var executions = 0
        var reportedError: Exception? = null
        var cleanups = 0

        ForegroundOperationExecutionGate.execute(
            operationId = OPERATION_ID,
            operationType = BackgroundOperationType.MOVE,
            dispatcher = dispatcher,
            startForeground = { throw startupError },
            operation = { executions++; null },
            onStartupFailure = { reportedError = it },
            cleanup = { cleanups++ }
        )

        assertEquals(0, executions)
        assertEquals(1, cleanups)
        assertEquals(startupError, reportedError)
        assertEquals(OPERATION_ID, dispatcher.events.single().operationId)
        assertEquals(FileOperationResult.Failure(FileOperationError.UNKNOWN), dispatcher.events.single().result)
    }

    @Test
    fun operationCancellation_isPropagatedWithOneCancelledAndCleanup() = runTest {
        val dispatcher = RecordingDispatcher()
        var cleanups = 0

        val result = runCatching {
            ForegroundOperationExecutionGate.execute(
                operationId = OPERATION_ID,
                operationType = BackgroundOperationType.DELETE,
                dispatcher = dispatcher,
                startForeground = {},
                operation = { throw CancellationException("cancelled") },
                onStartupFailure = {},
                cleanup = { cleanups++ }
            )
        }

        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(1, cleanups)
        assertEquals(OPERATION_ID, dispatcher.events.single().operationId)
        assertEquals(FileOperationResult.Cancelled, dispatcher.events.single().result)
    }

    private class RecordingDispatcher : FileOperationProgressDispatcher {
        val events = mutableListOf<BackgroundOperationEvent>()
        override val progressFlow: Flow<BackgroundOperationEvent> = emptyFlow()

        override suspend fun emitProgress(event: BackgroundOperationEvent) {
            events += event
        }
    }

    private companion object {
        const val OPERATION_ID = "operation-123"
    }
}
