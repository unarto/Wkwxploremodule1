package com.wakwau.xplore.core.worker.service

import com.wakwau.xplore.core.storage.operation.BackgroundOperationType
import com.wakwau.xplore.core.storage.operation.FileOperationError
import com.wakwau.xplore.core.storage.operation.FileOperationProgress
import com.wakwau.xplore.core.storage.operation.FileOperationProgressDispatcher
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

object ForegroundOperationExecutionGate {
    suspend fun execute(
        operationId: String,
        operationType: BackgroundOperationType,
        dispatcher: FileOperationProgressDispatcher,
        startForeground: () -> Unit,
        operation: suspend () -> FileOperationResult<FileOperationProgress>?,
        onStartupFailure: (Exception) -> Unit,
        cleanup: () -> Unit
    ) {
        try {
            try {
                startForeground()
            } catch (error: CancellationException) {
                FileOperationTerminalEmitter.execute(operationId, operationType, dispatcher) {
                    throw error
                }
                return
            } catch (error: Exception) {
                onStartupFailure(error)
                FileOperationTerminalEmitter.execute(operationId, operationType, dispatcher) {
                    FileOperationResult.Failure(FileOperationError.UNKNOWN)
                }
                return
            }

            FileOperationTerminalEmitter.execute(operationId, operationType, dispatcher, operation)
        } finally {
            withContext(NonCancellable) { cleanup() }
        }
    }
}
