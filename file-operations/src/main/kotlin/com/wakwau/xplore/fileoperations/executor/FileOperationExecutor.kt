package com.wakwau.xplore.fileoperations.executor

import com.wakwau.xplore.core.storage.constant.StorageConstants
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.BackgroundOperationType
import com.wakwau.xplore.core.storage.operation.FileOperationError
import com.wakwau.xplore.core.storage.operation.FileOperationProgress
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import com.wakwau.xplore.core.storage.repository.FileRepository
import com.wakwau.xplore.fileoperations.conflict.ConflictChoice
import com.wakwau.xplore.fileoperations.conflict.ResolvedTransferItem
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class FileOperationExecutor(
    private val fileRepository: FileRepository
) {
    suspend fun execute(
        request: FileOperationRequest,
        onProgress: suspend (FileOperationResult<FileOperationProgress>) -> Unit
    ): FileOperationResult<FileOperationProgress>? {
        val resolvedItems = request.resolvedItems
        return if (resolvedItems != null) {
            executeResolved(request.type, resolvedItems, onProgress)
        } else {
            executeSources(request.type, request.sources, request.destination, onProgress)
        }
    }

    private suspend fun executeSources(
        type: BackgroundOperationType,
        sources: List<StorageLocation>,
        destination: StorageLocation?,
        onProgress: suspend (FileOperationResult<FileOperationProgress>) -> Unit
    ): FileOperationResult<FileOperationProgress>? {
        if (type != BackgroundOperationType.DELETE && destination == null) {
            return FileOperationResult.Failure(FileOperationError.INVALID_LOCATION)
        }

        var deletedCount = 0L
        for (source in sources) {
            currentCoroutineContext().ensureActive()
            if (type == BackgroundOperationType.DELETE) {
                when (val result = fileRepository.delete(source)) {
                    is FileOperationResult.Success -> {
                        deletedCount++
                        onProgress(
                            FileOperationResult.Success(
                                FileOperationProgress(
                                    deletedCount,
                                    sources.size.toLong(),
                                    source.path.trimEnd('/').substringAfterLast('/')
                                )
                            )
                        )
                    }
                    is FileOperationResult.Failure -> return result
                    FileOperationResult.Cancelled -> return FileOperationResult.Cancelled
                    is FileOperationResult.Completed -> return FileOperationResult.Failure(FileOperationError.UNKNOWN)
                }
                continue
            }

            val target = createTargetLocation(source, requireNotNull(destination))
            val terminal = executeTransfer(type, source, target, onProgress)
            if (terminal != null) return terminal
        }
        return null
    }

    private suspend fun executeResolved(
        type: BackgroundOperationType,
        resolvedItems: List<ResolvedTransferItem>,
        onProgress: suspend (FileOperationResult<FileOperationProgress>) -> Unit
    ): FileOperationResult<FileOperationProgress>? {
        if (type == BackgroundOperationType.DELETE) {
            return FileOperationResult.Failure(FileOperationError.INVALID_LOCATION)
        }
        for (item in resolvedItems) {
            currentCoroutineContext().ensureActive()
            if (item.choice == ConflictChoice.SKIP) continue
            val terminal = executeTransfer(type, item.source, item.targetLocation, onProgress)
            if (terminal != null) return terminal
        }
        return null
    }

    private suspend fun executeTransfer(
        type: BackgroundOperationType,
        source: StorageLocation,
        target: StorageLocation,
        onProgress: suspend (FileOperationResult<FileOperationProgress>) -> Unit
    ): FileOperationResult<FileOperationProgress>? {
        val flow = when (type) {
            BackgroundOperationType.COPY -> fileRepository.copy(source, target)
            BackgroundOperationType.MOVE -> fileRepository.move(source, target)
            BackgroundOperationType.DELETE -> return FileOperationResult.Failure(FileOperationError.INVALID_LOCATION)
        }
        var terminal: FileOperationResult<FileOperationProgress>? = null
        flow.collect { result ->
            when (result) {
                is FileOperationResult.Success -> onProgress(result)
                is FileOperationResult.Failure -> terminal = result
                FileOperationResult.Cancelled -> terminal = FileOperationResult.Cancelled
                is FileOperationResult.Completed -> Unit
            }
        }
        return terminal
    }

    private fun createTargetLocation(source: StorageLocation, destination: StorageLocation): StorageLocation {
        val sourceName = source.path.trimEnd('/').substringAfterLast('/')
        return if (destination.path.startsWith(StorageConstants.CONTENT_SCHEME_PREFIX)) {
            StorageLocation(
                path = "${destination.path.substringBefore('#')}#$sourceName",
                rootId = destination.rootId
            )
        } else {
            StorageLocation(
                path = "${destination.path.trimEnd('/')}/$sourceName",
                rootId = destination.rootId
            )
        }
    }
}
