package com.wakwau.xplore.core.worker.service

import com.wakwau.xplore.core.storage.operation.BackgroundOperationType
import com.wakwau.xplore.core.storage.operation.BackgroundOperationEvent
import com.wakwau.xplore.core.storage.operation.FileOperationError
import com.wakwau.xplore.core.storage.operation.FileOperationProgress
import com.wakwau.xplore.core.storage.operation.FileOperationProgressDispatcher
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

object FileOperationTerminalEmitter {
    suspend fun execute(
        operationId: String,
        operationType: BackgroundOperationType,
        dispatcher: FileOperationProgressDispatcher,
        operation: suspend () -> FileOperationResult<FileOperationProgress>?
    ) {
        var cancellation: CancellationException? = null
        val terminalResult = try {
            operation() ?: FileOperationResult.Completed(operationType)
        } catch (error: CancellationException) {
            cancellation = error
            FileOperationResult.Cancelled
        } catch (_: Exception) {
            FileOperationResult.Failure(FileOperationError.UNKNOWN)
        }

        withContext(NonCancellable) {
            dispatcher.emitProgress(BackgroundOperationEvent(operationId, terminalResult))
        }
        cancellation?.let { throw it }
    }
}
