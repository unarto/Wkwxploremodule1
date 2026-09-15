// [Modul: :app] [Jalur Class]: app/src/main/kotlin/com/wakwau/xplore/orchestrator/AppOrchestratorViewModel.kt
// [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API

package com.wakwau.xplore.orchestrator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakwau.xplore.core.storage.api.error.StorageErrorMapper
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.search.FileSearchQuery
import com.wakwau.xplore.di.FileManagerUseCaseModule
import com.wakwau.xplore.filemanager.constant.FileOperationConstants
import com.wakwau.xplore.filemanager.event.DualPaneEvent
import com.wakwau.xplore.filemanager.state.DualPaneState
import com.wakwau.xplore.filemanager.ui.action.FileOperationActionDelegate
import com.wakwau.xplore.fileoperations.conflict.ConflictChoice
import com.wakwau.xplore.fileoperations.conflict.FileConflict
import com.wakwau.xplore.fileoperations.conflict.ResolvedTransferItem
import com.wakwau.xplore.search.sync.FileIndexSynchronizer
import com.wakwau.xplore.fileoperations.ui.state.OperationUiState
import com.wakwau.xplore.orchestrator.fileops.CopyOperationOrchestrator
import com.wakwau.xplore.orchestrator.fileops.DeleteOperationOrchestrator
import com.wakwau.xplore.orchestrator.fileops.MoveOperationOrchestrator
import com.wakwau.xplore.orchestrator.fileops.RenameOperationOrchestrator
import com.wakwau.xplore.orchestrator.search.SearchOperationOrchestrator
import com.wakwau.xplore.search.ui.SearchUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import com.wakwau.xplore.core.storage.operation.BackgroundOperationEvent
import com.wakwau.xplore.core.storage.operation.FileOperationItemStatus
import com.wakwau.xplore.core.storage.model.isSameOrDescendantOf
import com.wakwau.xplore.filemanager.state.PanelId

class AppOrchestratorViewModel(
    private val useCaseModule: FileManagerUseCaseModule,
    private val storageErrorMapper: StorageErrorMapper,
    private val backgroundOperationClient: com.wakwau.xplore.fileoperations.client.BackgroundOperationClient,
    private val fileIndexSynchronizer: FileIndexSynchronizer
) : ViewModel(), FileOperationActionDelegate {
    private val pendingIndexMutations = PendingIndexMutations()
    private var activeOperationId: String? = null
    private var planningJob: Job? = null
    private var operationPanelId: PanelId = PanelId.LEFT
    private var operationMarkedIds: Set<String> = emptySet()

    init {
        // [CopyFix]: Connect observeProgress flow to UI events berdasarkan copy.md
        viewModelScope.launch {
            backgroundOperationClient.observeProgress().collect { event ->
                val result = event.result
                if (result is com.wakwau.xplore.core.storage.operation.FileOperationResult.Success) {
                    if (event.operationId == activeOperationId) internalDispatch(DualPaneEvent.OperationProgress(result.data))
                    return@collect
                }
                var syncFailure: Exception? = null
                try {
                    reconcileOperation(event)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    syncFailure = error
                } finally {
                    internalDispatch(DualPaneEvent.Refresh(PanelId.LEFT))
                    internalDispatch(DualPaneEvent.Refresh(PanelId.RIGHT))
                }
                if (event.operationId != activeOperationId) return@collect
                activeOperationId = null
                when {
                    syncFailure != null -> internalDispatch(DualPaneEvent.OperationFailed(syncFailure.message ?: "Index synchronization failed"))
                    result is com.wakwau.xplore.core.storage.operation.FileOperationResult.Completed -> {
                        val message = when (result.operationType) {
                            com.wakwau.xplore.core.storage.operation.BackgroundOperationType.COPY -> FileOperationConstants.SUCCESS_COPY
                            com.wakwau.xplore.core.storage.operation.BackgroundOperationType.MOVE -> FileOperationConstants.SUCCESS_MOVE
                            com.wakwau.xplore.core.storage.operation.BackgroundOperationType.DELETE -> FileOperationConstants.SUCCESS_DELETE
                        }
                        internalDispatch(DualPaneEvent.OperationSuccess(message))
                    }
                    result is com.wakwau.xplore.core.storage.operation.FileOperationResult.Failure ->
                        internalDispatch(DualPaneEvent.OperationFailed(result.error.name))
                    else -> internalDispatch(DualPaneEvent.OperationCancelled)
                }
            }
        }
    }

    private val _operationState = MutableStateFlow<OperationUiState>(OperationUiState.Idle)
    val operationState: StateFlow<OperationUiState> = _operationState

    private val _searchUiState = MutableStateFlow(SearchUiState())
    val searchUiState: StateFlow<SearchUiState> = _searchUiState

    private var externalDispatch: ((DualPaneEvent) -> Unit)? = null

    fun setExternalDispatch(dispatch: (DualPaneEvent) -> Unit) {
        externalDispatch = dispatch
    }

    private val internalDispatch: (DualPaneEvent) -> Unit = { event ->
        dispatchEvent(event)
        externalDispatch?.invoke(event)
    }

    private val copyOrchestrator = CopyOperationOrchestrator(
        copyFilesUseCase = useCaseModule.copyFilesUseCase,
        detectConflictsUseCase = useCaseModule.detectConflictsUseCase,
        resolveTransferUseCase = useCaseModule.resolveTransferUseCase,
        storageErrorMapper = storageErrorMapper,
        dispatch = internalDispatch,
        onEnqueued = { operationId, items ->
            activeOperationId = operationId
            pendingIndexMutations.put(
                operationId,
                PendingIndexMutation.Transfer(
                    com.wakwau.xplore.core.storage.operation.BackgroundOperationType.COPY,
                    items,
                    operationPanelId,
                    operationMarkedIds
                )
            )
        },
        onShowConflict = { isMove, conflicts, dest, sources ->
            showConflict(isMove, conflicts, dest, sources)
        }
    )

    private val moveOrchestrator = MoveOperationOrchestrator(
        moveFilesUseCase = useCaseModule.moveFilesUseCase,
        detectConflictsUseCase = useCaseModule.detectConflictsUseCase,
        resolveTransferUseCase = useCaseModule.resolveTransferUseCase,
        storageErrorMapper = storageErrorMapper,
        dispatch = internalDispatch,
        onEnqueued = { operationId, items ->
            activeOperationId = operationId
            pendingIndexMutations.put(
                operationId,
                PendingIndexMutation.Transfer(
                    com.wakwau.xplore.core.storage.operation.BackgroundOperationType.MOVE,
                    items,
                    operationPanelId,
                    operationMarkedIds
                )
            )
        },
        onShowConflict = { isMove, conflicts, dest, sources ->
            showConflict(isMove, conflicts, dest, sources)
        }
    )

    private val deleteOrchestrator = DeleteOperationOrchestrator(
        deleteFilesUseCase = useCaseModule.deleteFilesUseCase,
        storageErrorMapper = storageErrorMapper,
        dispatch = internalDispatch,
        onEnqueued = { operationId, sources ->
            activeOperationId = operationId
            pendingIndexMutations.put(operationId, PendingIndexMutation.Delete(sources))
        }
    )

    private val renameOrchestrator = RenameOperationOrchestrator(
        renameFileUseCase = useCaseModule.renameFileUseCase,
        dispatch = internalDispatch,
        onRenamed = { oldItem, newItem ->
            fileIndexSynchronizer.syncRenamed(oldItem.location, newItem)
        }
    )

    private val searchOrchestrator = SearchOperationOrchestrator(
        searchFilesUseCase = useCaseModule.searchFilesUseCase,
        dispatch = internalDispatch
    )

    suspend fun syncCreatedItem(item: FileItem) {
        fileIndexSynchronizer.syncCreated(item)
    }

    private suspend fun reconcileOperation(event: BackgroundOperationEvent) {
        val mutation = pendingIndexMutations.take(event.operationId) ?: return
        var firstFailure: Exception? = null
        for (outcome in event.outcomes) {
            val completed = outcome.status == FileOperationItemStatus.COMPLETED
            if (mutation is PendingIndexMutation.Transfer) {
                val item = mutation.items.firstOrNull { it.source == outcome.source } ?: continue
                if (completed && mutation.sourcePanelId != null) {
                    val cleared = mutation.markedIds.filterTo(mutableSetOf()) { path ->
                        StorageLocation(path, item.source.rootId).isSameOrDescendantOf(item.source)
                    }
                    internalDispatch(DualPaneEvent.RemoveSelectedItems(mutation.sourcePanelId, cleared))
                }
                if (!completed && !outcome.destinationCommitted) continue
                try {
                    if (completed && mutation.type == com.wakwau.xplore.core.storage.operation.BackgroundOperationType.MOVE) {
                        fileIndexSynchronizer.syncMoved(item.source, item.destinationDir, item.targetLocation, item.targetName)
                    } else {
                        fileIndexSynchronizer.syncCopied(item.destinationDir, item.targetLocation, item.targetName)
                        if (mutation.type == com.wakwau.xplore.core.storage.operation.BackgroundOperationType.MOVE) {
                            val parent = requireNotNull(useCaseModule.getParentLocationUseCase(item.source)) {
                                "Cannot reconcile remaining source without its parent"
                            }
                            fileIndexSynchronizer.syncRemainingSource(item.source, parent, item.originalName)
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (firstFailure == null) firstFailure = error
                }
            } else if (mutation is PendingIndexMutation.Delete && completed) {
                try {
                    fileIndexSynchronizer.removeByPrefix(outcome.source.path)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (firstFailure == null) firstFailure = error
                }
            }
        }
        firstFailure?.let { throw it }
    }

    override fun normalizeMarkedItems(ids: Set<String>, candidates: List<FileItem>) =
        com.wakwau.xplore.fileoperations.selection.NormalizeMarkedItems(ids, candidates)

    // [Jalur Class/Modul]: app/src/main/kotlin/com/wakwau/xplore/orchestrator/AppOrchestratorViewModel.kt
    // [Penjelasan]: Mengimplementasikan antarmuka FileOperationActionDelegate.dispatchEvent untuk menangani event operasi I/O dan memetakan resource ID tanpa hardcode.
    override fun dispatchEvent(event: DualPaneEvent) {
        when (event) {
            is DualPaneEvent.OperationStarted -> {
                val nameRes = when (event.operationNameRes) {
                    FileOperationConstants.OPERATION_COPY -> com.wakwau.xplore.filemanager.ui.R.string.op_copy_started
                    FileOperationConstants.OPERATION_MOVE -> com.wakwau.xplore.filemanager.ui.R.string.op_move_started
                    FileOperationConstants.OPERATION_DELETE -> com.wakwau.xplore.filemanager.ui.R.string.op_delete_started
                    FileOperationConstants.OPERATION_RENAME -> com.wakwau.xplore.filemanager.ui.R.string.op_rename_started
                    FileOperationConstants.OPERATION_CREATE_DIR -> com.wakwau.xplore.filemanager.ui.R.string.op_create_dir_started
                    else -> event.operationNameRes
                }
                _operationState.value = OperationUiState.Running(nameRes)
            }
            is DualPaneEvent.OperationProgress -> {
                val currentState = _operationState.value
                if (currentState is OperationUiState.Running) {
                    _operationState.value = currentState.copy(progress = event.progress)
                }
            }
            is DualPaneEvent.OperationSuccess -> {
                val successRes = when (event.messageRes) {
                    FileOperationConstants.SUCCESS_COPY -> com.wakwau.xplore.filemanager.ui.R.string.op_copy_completed
                    FileOperationConstants.SUCCESS_MOVE -> com.wakwau.xplore.filemanager.ui.R.string.op_move_completed
                    FileOperationConstants.SUCCESS_DELETE -> com.wakwau.xplore.filemanager.ui.R.string.op_delete_completed
                    FileOperationConstants.SUCCESS_RENAME -> com.wakwau.xplore.filemanager.ui.R.string.op_rename_completed
                    FileOperationConstants.SUCCESS_CREATE_DIR -> com.wakwau.xplore.filemanager.ui.R.string.op_create_dir_completed
                    else -> event.messageRes
                }
                _operationState.value = OperationUiState.Success(successRes)
            }
            is DualPaneEvent.OperationFailed -> {
                _operationState.value = OperationUiState.Failure(event.error)
            }
            is DualPaneEvent.OperationCancelled -> {
                _operationState.value = OperationUiState.Cancelled
            }
            is DualPaneEvent.CancelOperationRequested -> {
                planningJob?.cancel()
                activeOperationId?.let { backgroundOperationClient.cancelOperation(it) }
                    ?: run { _operationState.value = OperationUiState.Cancelled }
            }
            is DualPaneEvent.ClearOperationState -> {
                planningJob?.cancel()
                _operationState.value = OperationUiState.Idle
            }
            is DualPaneEvent.ShowOperationConfirmation -> {
                planningJob?.cancel()
                operationPanelId = event.sourcePanelId
                operationMarkedIds = event.markedIds
                _operationState.value = OperationUiState.Confirming(
                    isMove = event.isMove,
                    items = event.items,
                    destination = event.destination
                )
            }
            is DualPaneEvent.SearchIconClicked -> {
                _searchUiState.update {
                    it.copy(
                        isSearchDialogOpen = true,
                        results = emptyList(),
                        isSearching = false,
                        searchError = null,
                        hasSearched = false
                    )
                }
            }
            is DualPaneEvent.DismissSearchDialog -> {
                _searchUiState.update { it.copy(isSearchDialogOpen = false) }
                searchOrchestrator.cancelSearch()
            }
            is DualPaneEvent.SearchStarted -> {
                _searchUiState.update { it.copy(isSearching = true, searchError = null, hasSearched = true) }
            }
            is DualPaneEvent.SearchResultsUpdated -> {
                _searchUiState.update { it.copy(results = it.results + event.results) }
            }
            is DualPaneEvent.SearchCompleted -> {
                _searchUiState.update { it.copy(isSearching = false) }
            }
            is DualPaneEvent.SearchFailed -> {
                _searchUiState.update { it.copy(isSearching = false, searchError = event.error) }
            }
            is DualPaneEvent.SearchCancelled -> {
                _searchUiState.update { it.copy(isSearching = false) }
            }
            is DualPaneEvent.SearchHistoryUpdated -> {
                _searchUiState.update { it.copy(searchHistory = event.history) }
            }
            else -> {}
        }
    }

    override fun requestCopy(state: DualPaneState, items: List<FileItem>, destination: StorageLocation) {
        planningJob?.cancel()
        planningJob = viewModelScope.launch {
            copyOrchestrator.execute(state.copy(activePanelId = operationPanelId), items, destination.path, destination.rootId)
        }
    }

    override fun requestMove(state: DualPaneState, items: List<FileItem>, destination: StorageLocation) {
        planningJob?.cancel()
        planningJob = viewModelScope.launch {
            moveOrchestrator.execute(state.copy(activePanelId = operationPanelId), items, destination.path, destination.rootId)
        }
    }

    override fun requestDelete(state: DualPaneState, items: List<FileItem>) {
        viewModelScope.launch {
            deleteOrchestrator.execute(state, items)
        }
    }

    override fun requestRename(state: DualPaneState, item: FileItem, newName: String) {
        viewModelScope.launch {
            renameOrchestrator.execute(state, item, newName)
        }
    }

    override fun requestSearch(query: FileSearchQuery) {
        viewModelScope.launch {
            searchOrchestrator.executeSearch(query)
        }
    }

    fun resolveConflictDecision(choice: ConflictChoice, applyToAll: Boolean) {
        val current = _operationState.value as? OperationUiState.ConflictResolution ?: return
        val conflict = current.currentConflict ?: return
        if (conflict.isBatchCollision && choice == ConflictChoice.OVERWRITE) return
        val decisions = current.resolvedDecisions.toMutableMap()
        for (candidate in current.pendingConflicts.drop(current.currentConflictIndex)) {
            if (candidate.isBatchCollision && choice == ConflictChoice.OVERWRITE) continue
            decisions[candidate.source] = choice
            if (!applyToAll) break
        }
        val next = current.pendingConflicts.indexOfFirst { it.source !in decisions }
        if (next >= 0) {
            _operationState.value = current.copy(currentConflictIndex = next, resolvedDecisions = decisions)
            return
        }
        _operationState.value = current.copy(resolvedDecisions = decisions)
        planningJob?.cancel()
        planningJob = viewModelScope.launch {
            if (current.isMove) moveOrchestrator.executeResolved(current.allSources, current.destinationDir, decisions)
            else copyOrchestrator.executeResolved(current.allSources, current.destinationDir, decisions)
        }
    }

    fun showConflict(
        isMove: Boolean,
        conflicts: List<FileConflict>,
        destinationDir: StorageLocation,
        allSources: List<StorageLocation>
    ) {
        val previous = _operationState.value as? OperationUiState.ConflictResolution
        val decisions = if (previous != null && previous.allSources == allSources && previous.destinationDir == destinationDir && previous.isMove == isMove) {
            previous.resolvedDecisions - conflicts.map { it.source }.toSet()
        } else emptyMap()
        _operationState.value = OperationUiState.ConflictResolution(
            resolvedDecisions = decisions,
            isMove = isMove,
            pendingConflicts = conflicts,
            destinationDir = destinationDir,
            allSources = allSources
        )
    }
}
