// [Modul: :filemanager-ui] [Jalur Class]: filemanager-ui/src/main/kotlin/com/wakwau/xplore/filemanager/ui/presentation/DualPaneViewModel.kt
// [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API
package com.wakwau.xplore.filemanager.ui.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakwau.xplore.core.storage.preferences.FilePreferencesState
import com.wakwau.xplore.filemanager.event.DualPaneEvent
import com.wakwau.xplore.filemanager.state.DualPaneState
import com.wakwau.xplore.filemanager.state.DualPaneStateHolder
import com.wakwau.xplore.filemanager.ui.action.PanelNavigationHandler

import com.wakwau.xplore.core.storage.search.FileSearchQuery
import com.wakwau.xplore.filemanager.ui.state.DualPanelStateController
import com.wakwau.xplore.filemanager.ui.tree.TreeNavigationAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.MutableStateFlow
import com.wakwau.xplore.filemanager.ui.state.FileDialogUiState

class DualPaneViewModel(
    stateHolderFactory: (CoroutineScope) -> DualPaneStateHolder,
    private val treeNavigationAdapter: TreeNavigationAdapter,
    private val navigationHandler: PanelNavigationHandler,
    private val actionDelegate: com.wakwau.xplore.filemanager.ui.action.FileOperationActionDelegate? = null
) : ViewModel() {

    private val _dialogUiState = MutableStateFlow<FileDialogUiState>(FileDialogUiState.None)
    val dialogUiState: StateFlow<FileDialogUiState> = _dialogUiState

    val stateHolder: DualPaneStateHolder = stateHolderFactory(viewModelScope)

    constructor(
        stateHolder: DualPaneStateHolder,
        treeNavigationAdapter: TreeNavigationAdapter,
        navigationHandler: PanelNavigationHandler
    ) : this(
        stateHolderFactory = { stateHolder },
        treeNavigationAdapter = treeNavigationAdapter,
        navigationHandler = navigationHandler
    )

    val panelStateController = DualPanelStateController { panelId ->
        dispatch(DualPaneEvent.SetActivePanel(panelId))
    }

    val state: StateFlow<DualPaneState> = stateHolder.state

    val preferencesState: StateFlow<FilePreferencesState> = stateHolder.preferencesState

    private var currentSearchResults: List<com.wakwau.xplore.core.storage.model.FileItem> = emptyList()

    init {
        stateHolder.setEventListener { event ->
            when (event) {
                is DualPaneEvent.Refresh -> {
                    viewModelScope.launch {
                        treeNavigationAdapter.refreshAllNodes()
                    }
                }
                is DualPaneEvent.UpdateSortPreferences -> {
                    treeNavigationAdapter.reSortNodes()
                }
                is DualPaneEvent.ToggleShowHiddenFiles -> {
                    viewModelScope.launch {
                        treeNavigationAdapter.refreshAllNodes()
                    }
                }
                is DualPaneEvent.SearchStarted -> {
                    currentSearchResults = emptyList()
                }
                is DualPaneEvent.SearchResultsUpdated -> {
                    val activePanelId = stateHolder.state.value.activePanelId
                    currentSearchResults = currentSearchResults + event.results
                    treeNavigationAdapter.updateSearchResults(
                        activePanelId,
                        event.keyword,
                        currentSearchResults
                    )
                }
                is DualPaneEvent.OperationStarted,
                is DualPaneEvent.OperationProgress,
                is DualPaneEvent.OperationSuccess,
                is DualPaneEvent.OperationFailed,
                is DualPaneEvent.OperationCancelled,
                is DualPaneEvent.ClearOperationState -> {
                    // [Modul: :filemanager-ui] [Jalur Class]: filemanager-ui/src/main/kotlin/com/wakwau/xplore/filemanager/ui/presentation/DualPaneViewModel.kt
                    // [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API
                    actionDelegate?.dispatchEvent(event)
                    
                    // [RenameFix]: Lock selection snapshot & safe dialog dismissal berdasarkan ubahnama.md
                    if (event is DualPaneEvent.OperationSuccess || event is DualPaneEvent.OperationFailed || event is DualPaneEvent.OperationCancelled) {
                        _dialogUiState.value = FileDialogUiState.None
                    }
                    
                    if (event is DualPaneEvent.OperationSuccess) {
                        // [Modul: :filemanager-ui] [Jalur Class]: filemanager-ui/src/main/kotlin/com/wakwau/xplore/filemanager/ui/presentation/DualPaneViewModel.kt
                        // [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API
                        dispatch(DualPaneEvent.Refresh(stateHolder.state.value.activePanelId))
                    }
                }
                else -> {}
            }
        }
    }

    fun checkPermission() {
        stateHolder.checkPermission()
    }

    fun addLinkedStorage(uri: Uri) {
        stateHolder.addLinkedStorage(uri.toString())
    }

    fun removeLinkedStorage(uri: Uri) {
        stateHolder.removeLinkedStorage(uri.toString())
    }

    fun dispatch(event: DualPaneEvent) {
        when (event) {
            is DualPaneEvent.ShowCreateDirectoryDialog -> {
                _dialogUiState.value = FileDialogUiState.CreateDirectory(event.parentLocation)
            }
            is DualPaneEvent.ShowRenameDialog -> {
                _dialogUiState.value = FileDialogUiState.RenameItem(event.item)
            }
            is DualPaneEvent.ShowDeleteConfirmationDialog -> {
                _dialogUiState.value = FileDialogUiState.DeleteConfirmation(event.items)
            }
            is DualPaneEvent.DismissInputDialog -> {
                _dialogUiState.value = FileDialogUiState.None
            }
            is DualPaneEvent.CreateDirectory -> {
                _dialogUiState.value = FileDialogUiState.None
                stateHolder.dispatch(event)
            }
            is DualPaneEvent.NavigateUp -> {
                navigationHandler.handleNavigateUp(stateHolder.state.value, event.panelId)
            }
            is DualPaneEvent.ExecuteConfirmedCopy -> {
                actionDelegate?.requestCopy(state.value, event.items, event.targetPath)
            }
            is DualPaneEvent.ExecuteConfirmedMove -> {
                actionDelegate?.requestMove(state.value, event.items, event.targetPath)
            }
            is DualPaneEvent.DeleteSelected -> {
                _dialogUiState.value = FileDialogUiState.None
                actionDelegate?.requestDelete(state.value, event.items)
            }
            is DualPaneEvent.RenameItem -> {
                // [RenameFix]: Lock selection snapshot & safe dialog dismissal berdasarkan ubahnama.md
                actionDelegate?.requestRename(state.value, event.item, event.newName)
            }
            is DualPaneEvent.ExecuteSearch -> {
                actionDelegate?.requestSearch(event.query)
            }
            else -> {
                stateHolder.dispatch(event)
            }
        }
    }

    fun onSearchItemClick(item: com.wakwau.xplore.core.storage.model.FileItem) {
        if (item.type == com.wakwau.xplore.core.storage.model.FileType.DIRECTORY) {
            dispatch(com.wakwau.xplore.filemanager.event.DualPaneEvent.OpenLocation(state.value.activePanelId, item.location))
        }
        dispatch(com.wakwau.xplore.filemanager.event.DualPaneEvent.DismissSearchDialog)
    }

    fun executeSearch(query: FileSearchQuery) {
        val queryWithHidden = query.copy(showHidden = preferencesState.value.showHiddenFiles)
        dispatch(com.wakwau.xplore.filemanager.event.DualPaneEvent.ExecuteSearch(queryWithHidden))
    }

    override fun onCleared() {
        super.onCleared()
        treeNavigationAdapter.clear()
    }
}
