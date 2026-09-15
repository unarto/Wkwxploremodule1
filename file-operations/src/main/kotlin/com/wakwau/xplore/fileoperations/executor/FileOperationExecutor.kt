package com.wakwau.xplore.fileoperations.executor

import com.wakwau.xplore.core.storage.constant.StorageConstants
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.model.isSameOrDescendantOf
import com.wakwau.xplore.core.storage.operation.*
import com.wakwau.xplore.core.storage.repository.FileRepository
import com.wakwau.xplore.fileoperations.conflict.ConflictChoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class FileOperationExecutor(private val fileRepository: FileRepository) {
    suspend fun execute(
        request: FileOperationRequest,
        onItemOutcome: suspend (FileOperationItemOutcome) -> Unit = {},
        onProgress: suspend (FileOperationResult<FileOperationProgress>) -> Unit
    ): FileOperationResult<FileOperationProgress>? {
        val resolved = request.resolvedItems
        if (request.type != BackgroundOperationType.DELETE && resolved == null && request.destination == null) {
            return FileOperationResult.Failure(FileOperationError.INVALID_LOCATION)
        }
        val entries = if (resolved != null) {
            require(request.type != BackgroundOperationType.DELETE)
            val transfers = resolved.filter { it.choice != ConflictChoice.SKIP }
            require(transfers.map { it.source }.distinct().size == transfers.size) { "Duplicate source" }
            require(transfers.map { it.targetLocation.path.lowercase(java.util.Locale.ROOT) }.distinct().size == transfers.size) {
                "Batch targets require conflict resolution"
            }
            transfers.forEach {
                require(!it.targetLocation.isSameOrDescendantOf(it.source) || (!it.isDirectory && !it.source.isSameOrDescendantOf(it.targetLocation))) { "Invalid transfer target" }
            }
            resolved.map { Entry(it.source, it.targetLocation, it.choice == ConflictChoice.SKIP) }
        } else request.sources.distinct().map { source ->
            Entry(source, request.destination?.let { createTargetLocation(source, it) }, false)
        }
        if (request.type != BackgroundOperationType.DELETE) {
            val targets = entries.filterNot { it.skip }.map { requireNotNull(it.target) }
            require(targets.map { it.path.lowercase(java.util.Locale.ROOT) }.distinct().size == targets.size) {
                "Batch targets require conflict resolution before execution"
            }
            entries.filterNot { it.skip }.forEach {
                val target = requireNotNull(it.target)
                require(!(target.isSameOrDescendantOf(it.source) && it.source.isSameOrDescendantOf(target))) {
                    "Source and final target must differ"
                }
            }
        }
        var nextIndex = 0
        var deletedCount = 0L
        suspend fun record(entry: Entry, status: FileOperationItemStatus, committed: Boolean = false) =
            withContext(NonCancellable) {
                onItemOutcome(FileOperationItemOutcome(entry.source, entry.target, status, committed))
            }
        try {
            for ((index, entry) in entries.withIndex()) {
                nextIndex = index
                if (entry.skip) {
                    record(entry, FileOperationItemStatus.SKIPPED)
                    nextIndex = index + 1
                    continue
                }
                try {
                    currentCoroutineContext().ensureActive()
                    val failure = if (request.type == BackgroundOperationType.DELETE) {
                        when (val result = fileRepository.delete(entry.source)) {
                            is FileOperationResult.Success -> null
                            is FileOperationResult.Failure -> result
                            FileOperationResult.Cancelled -> FileOperationResult.Cancelled
                            is FileOperationResult.Completed -> FileOperationResult.Failure(FileOperationError.UNKNOWN)
                        }
                    } else {
                        executeTransfer(request.type, entry.source, requireNotNull(entry.target), onProgress)
                    }
                    if (failure != null) {
                        record(entry, if (failure == FileOperationResult.Cancelled) FileOperationItemStatus.CANCELLED else FileOperationItemStatus.FAILED)
                        nextIndex = index + 1
                        return failure
                    }
                    record(entry, FileOperationItemStatus.COMPLETED, request.type != BackgroundOperationType.DELETE)
                    nextIndex = index + 1
                    if (request.type == BackgroundOperationType.DELETE) {
                        deletedCount++
                        onProgress(FileOperationResult.Success(FileOperationProgress(
                            deletedCount, entries.size.toLong(), entry.source.path.trimEnd('/').substringAfterLast('/')
                        )))
                    }
                } catch (error: CancellationException) {
                    if (nextIndex == index) record(entry, FileOperationItemStatus.CANCELLED, error is CommittedDestination)
                    nextIndex = index + 1
                    throw error
                } catch (error: Exception) {
                    if (nextIndex == index) record(entry, FileOperationItemStatus.FAILED, error is CommittedDestination)
                    nextIndex = index + 1
                    return FileOperationResult.Failure(FileOperationError.UNKNOWN)
                }
            }
            return null
        } finally {
            for (entry in entries.drop(nextIndex)) {
                record(entry, if (entry.skip) FileOperationItemStatus.SKIPPED else FileOperationItemStatus.NOT_STARTED)
            }
        }
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
            BackgroundOperationType.DELETE -> error("Delete is not a transfer")
        }
        var terminal: FileOperationResult<FileOperationProgress>? = null
        flow.collect { result ->
            when (result) {
                is FileOperationResult.Success -> if (terminal == null) onProgress(result)
                is FileOperationResult.Failure -> if (terminal == null) terminal = result
                FileOperationResult.Cancelled -> if (terminal == null) terminal = FileOperationResult.Cancelled
                is FileOperationResult.Completed -> Unit
            }
        }
        return terminal
    }

    private data class Entry(val source: StorageLocation, val target: StorageLocation?, val skip: Boolean)

    private fun createTargetLocation(source: StorageLocation, destination: StorageLocation): StorageLocation {
        val name = source.path.trimEnd('/').substringAfterLast('/')
        return StorageLocation(
            if (destination.path.startsWith(StorageConstants.CONTENT_SCHEME_PREFIX)) "${destination.path.substringBefore('#')}#$name"
            else "${destination.path.trimEnd('/')}/$name",
            destination.rootId
        )
    }
}
