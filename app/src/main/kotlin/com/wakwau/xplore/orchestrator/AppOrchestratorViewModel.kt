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

class AppOrchestratorViewModel(
    private val useCaseModule: FileManagerUseCaseModule,
    private val storageErrorMapper: StorageErrorMapper,
    private val backgroundOperationClient: com.wakwau.xplore.fileoperations.client.BackgroundOperationClient,
    private val fileIndexSynchronizer: FileIndexSynchronizer
) : ViewModel(), FileOperationActionDelegate {
    private val pendingIndexMutations = PendingIndexMutations()

    init {
        // [CopyFix]: Connect observeProgress flow to UI events berdasarkan copy.md
        viewModelScope.launch {
            backgroundOperationClient.observeProgress().collect { event ->
                val result = event.result
                when (result) {
                    is com.wakwau.xplore.core.storage.operation.FileOperationResult.Success -> {
                        internalDispatch(DualPaneEvent.OperationProgress(result.data))
                    }
                    is com.wakwau.xplore.core.storage.operation.FileOperationResult.Completed -> {
                        try {
                            if (!syncCompletedOperation(event.operationId, result.operationType)) return@collect
                        } catch (e: Exception) {
                            internalDispatch(DualPaneEvent.OperationFailed(e.message ?: "Index synchronization failed"))
                            return@collect
                        }
                        // [CopyFix]: Wiring bridge & sync ViewModel progress completion berdasarkan copy.md
                        val msgRes = when (result.operationType) {
                            com.wakwau.xplore.core.storage.operation.BackgroundOperationType.COPY -> FileOperationConstants.SUCCESS_COPY
                            com.wakwau.xplore.core.storage.operation.BackgroundOperationType.MOVE -> FileOperationConstants.SUCCESS_MOVE
                            com.wakwau.xplore.core.storage.operation.BackgroundOperationType.DELETE -> FileOperationConstants.SUCCESS_DELETE
                        }
                        internalDispatch(DualPaneEvent.OperationSuccess(msgRes))
                        internalDispatch(DualPaneEvent.Refresh(com.wakwau.xplore.filemanager.state.PanelId.LEFT))
                        internalDispatch(DualPaneEvent.Refresh(com.wakwau.xplore.filemanager.state.PanelId.RIGHT))
                    }
                    is com.wakwau.xplore.core.storage.operation.FileOperationResult.Failure -> {
                        pendingIndexMutations.remove(event.operationId)
                        internalDispatch(DualPaneEvent.OperationFailed(result.error.name))
                    }
                    is com.wakwau.xplore.core.storage.operation.FileOperationResult.Cancelled -> {
                        pendingIndexMutations.remove(event.operationId)
                        internalDispatch(DualPaneEvent.OperationCancelled)
                    }
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
            pendingIndexMutations.put(
                operationId,
                PendingIndexMutation.Transfer(
                    com.wakwau.xplore.core.storage.operation.BackgroundOperationType.COPY,
                    items
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
            pendingIndexMutations.put(
                operationId,
                PendingIndexMutation.Transfer(
                    com.wakwau.xplore.core.storage.operation.BackgroundOperationType.MOVE,
                    items
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

    private suspend fun syncCompletedOperation(
        operationId: String,
        type: com.wakwau.xplore.core.storage.operation.BackgroundOperationType
    ): Boolean {
        when (val mutation = pendingIndexMutations.take(operationId, type)) {
            is PendingIndexMutation.Transfer -> when (type) {
                com.wakwau.xplore.core.storage.operation.BackgroundOperationType.COPY -> mutation.items.forEach {
                    fileIndexSynchronizer.syncCopied(it.destinationDir, it.targetLocation, it.targetName)
                }
                com.wakwau.xplore.core.storage.operation.BackgroundOperationType.MOVE -> mutation.items.forEach {
                    fileIndexSynchronizer.syncMoved(it.source, it.destinationDir, it.targetLocation, it.targetName)
                }
                com.wakwau.xplore.core.storage.operation.BackgroundOperationType.DELETE -> Unit
            }
            is PendingIndexMutation.Delete -> if (type == com.wakwau.xplore.core.storage.operation.BackgroundOperationType.DELETE) {
                mutation.sources.forEach { fileIndexSynchronizer.removeByPrefix(it.path) }
            }
            null -> return false
        }
        return true
    }

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
            is DualPaneEvent.ClearOperationState -> {
                _operationState.value = OperationUiState.Idle
            }
            is DualPaneEvent.ShowOperationConfirmation -> {
                _operationState.value = OperationUiState.Confirming(
                    isMove = event.isMove,
                    items = event.items,
                    targetPath = event.targetPath
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

    override fun requestCopy(state: DualPaneState, items: List<FileItem>, targetPath: String) {
        viewModelScope.launch {
            copyOrchestrator.execute(state, items, targetPath)
        }
    }

    override fun requestMove(state: DualPaneState, items: List<FileItem>, targetPath: String) {
        viewModelScope.launch {
            moveOrchestrator.execute(state, items, targetPath)
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

    fun resolveCopyConflict(
        sources: List<StorageLocation>,
        dest: StorageLocation,
        decisions: Map<StorageLocation, ConflictChoice>
    ) {
        viewModelScope.launch {
            copyOrchestrator.executeResolved(sources, dest, decisions)
        }
    }

    fun resolveMoveConflict(
        sources: List<StorageLocation>,
        dest: StorageLocation,
        decisions: Map<StorageLocation, ConflictChoice>
    ) {
        viewModelScope.launch {
            moveOrchestrator.executeResolved(sources, dest, decisions)
        }
    }

    fun showConflict(
        isMove: Boolean,
        conflicts: List<FileConflict>,
        destinationDir: StorageLocation,
        allSources: List<StorageLocation>
    ) {
        _operationState.value = OperationUiState.ConflictResolution(
            isMove = isMove,
            pendingConflicts = conflicts,
            destinationDir = destinationDir,
            allSources = allSources
        )
    }
}
