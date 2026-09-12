// [Jalur Class/Modul]: filemanager/src/main/kotlin/com/wakwau/xplore/filemanager/state/DualPaneStateHolder.kt
// [Penjelasan]: Pure state holder dan domain orchestrator untuk panel ganda berbasis Coroutines dan StateFlow, bebas dari dependensi platform AndroidX Lifecycle, Uri, UI adapter, serta bebas dari coupling horizontal ke :file-operations dan :search.
package com.wakwau.xplore.filemanager.state

import com.wakwau.xplore.core.storage.preferences.FilePreferencesState
import com.wakwau.xplore.core.storage.preferences.AppPreferencesRepository
import com.wakwau.xplore.filemanager.action.CreateDirectoryOperationHandler
import com.wakwau.xplore.filemanager.action.FileDetailHandler
import com.wakwau.xplore.filemanager.action.PanelRefreshHandler
import com.wakwau.xplore.filemanager.event.DualPaneEvent
import com.wakwau.xplore.filemanager.reducer.DualPaneReducer
import com.wakwau.xplore.filemanager.usecase.CheckStoragePermissionUseCase
import com.wakwau.xplore.filemanager.usecase.GetParentLocationUseCase
import com.wakwau.xplore.filemanager.usecase.GetStorageVolumesUseCase
import com.wakwau.xplore.filemanager.usecase.LinkStorageUseCase
import com.wakwau.xplore.filemanager.usecase.ToggleShowHiddenFilesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DualPaneStateHolder(
    private val scope: CoroutineScope,
    private val reducer: DualPaneReducer,
    private val refreshHandler: PanelRefreshHandler,
    private val createDirectoryHandler: CreateDirectoryOperationHandler,
    private val fileDetailHandler: FileDetailHandler,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val toggleShowHiddenFilesUseCase: ToggleShowHiddenFilesUseCase,
    private val checkStoragePermissionUseCase: CheckStoragePermissionUseCase,
    private val getStorageVolumesUseCase: GetStorageVolumesUseCase,
    private val linkStorageUseCase: LinkStorageUseCase,
    private val getParentLocationUseCase: GetParentLocationUseCase = GetParentLocationUseCase()
) {
    private var eventListener: ((DualPaneEvent) -> Unit)? = null

    // [Jalur Class/Modul]: filemanager/src/main/kotlin/com/wakwau/xplore/filemanager/state/DualPaneStateHolder.kt
    // [Penjelasan]: Mendaftarkan listener event untuk sinkronisasi presentasi (misal TreeView) tanpa coupling ke modul UI.
    fun setEventListener(listener: (DualPaneEvent) -> Unit) {
        this.eventListener = listener
    }

    private val _state = MutableStateFlow(
        DualPaneState(
            hasPermission = checkStoragePermissionUseCase.hasPermission(),
            requiredPermissionType = checkStoragePermissionUseCase.getRequiredPermission()
        )
    )
    val state: StateFlow<DualPaneState> = _state.asStateFlow()

    val preferencesState: StateFlow<FilePreferencesState> = appPreferencesRepository.preferencesState

    init {
        // [Jalur Class/Modul]: filemanager/src/main/kotlin/com/wakwau/xplore/filemanager/state/DualPaneStateHolder.kt
        // [Penjelasan]: Mengamati pembaruan volume penyimpanan dan menyinkronkannya ke state panel ganda.
        scope.launch {
            getStorageVolumesUseCase.refresh()
            getStorageVolumesUseCase().collect { volumes ->
                dispatch(DualPaneEvent.StorageVolumesUpdated(volumes))
            }
        }

        checkPermission()
    }

    // [Jalur Class/Modul]: filemanager/src/main/kotlin/com/wakwau/xplore/filemanager/state/DualPaneStateHolder.kt
    // [Penjelasan]: Memeriksa status izin akses penyimpanan dan memperbarui volume jika izin tersedia.
    fun checkPermission() {
        val hasPermission = checkStoragePermissionUseCase.hasPermission()
        val requiredType = checkStoragePermissionUseCase.getRequiredPermission()
        dispatch(DualPaneEvent.PermissionStatusUpdated(hasPermission, requiredType))
        if (hasPermission) {
            scope.launch {
                getStorageVolumesUseCase.refresh()
            }
        }
    }

    // [Jalur Class/Modul]: filemanager/src/main/kotlin/com/wakwau/xplore/filemanager/state/DualPaneStateHolder.kt
    // [Penjelasan]: Mendaftarkan storage tertaut menggunakan URI string murni tanpa ketergantungan android.net.Uri.
    fun addLinkedStorage(uriString: String) {
        scope.launch {
            linkStorageUseCase.addLinkedStorage(uriString)
            getStorageVolumesUseCase.refresh()
        }
    }

    // [Jalur Class/Modul]: filemanager/src/main/kotlin/com/wakwau/xplore/filemanager/state/DualPaneStateHolder.kt
    // [Penjelasan]: Menghapus storage tertaut berdasarkan URI string murni tanpa ketergantungan android.net.Uri.
    fun removeLinkedStorage(uriString: String) {
        scope.launch {
            linkStorageUseCase.removeLinkedStorage(uriString)
            getStorageVolumesUseCase.refresh()
        }
    }

    // [Jalur Class/Modul]: filemanager/src/main/kotlin/com/wakwau/xplore/filemanager/state/DualPaneStateHolder.kt
    // [Penjelasan]: Mengurangi state via pure reducer, memicu side effects, dan memberitahukan listener presentation.
    fun dispatch(event: DualPaneEvent) {
        val newState = reducer.reduce(_state.value, event)
        _state.value = newState

        handleSideEffects(event)
        eventListener?.invoke(event)
    }

    // [Jalur Class/Modul]: filemanager/src/main/kotlin/com/wakwau/xplore/filemanager/state/DualPaneStateHolder.kt
    // [Penjelasan]: Menangani side effects domain (I/O, navigasi Up, dialog buat folder/rincian) di dalam CoroutineScope yang aman.
    private fun handleSideEffects(event: DualPaneEvent) {
        val stateSnapshot = _state.value

        when (event) {
            is DualPaneEvent.OpenLocation -> {
                scope.launch {
                    refreshHandler.loadDirectory(event.panelId, event.location)
                }
            }
            is DualPaneEvent.Refresh -> {
                val panel = if (event.panelId == PanelId.LEFT) stateSnapshot.leftPanel else stateSnapshot.rightPanel
                scope.launch {
                    panel.currentLocation?.let { location ->
                        refreshHandler.loadDirectory(event.panelId, location)
                    }
                }
            }
            is DualPaneEvent.NavigateUp -> {
                val panel = if (event.panelId == PanelId.LEFT) stateSnapshot.leftPanel else stateSnapshot.rightPanel
                panel.currentLocation?.let { location ->
                    getParentLocationUseCase(location)?.let { parentLoc ->
                        scope.launch {
                            refreshHandler.loadDirectory(event.panelId, parentLoc)
                        }
                    }
                }
            }
            is DualPaneEvent.CreateDirectory -> {
                scope.launch {
                    createDirectoryHandler.execute(stateSnapshot, event.parentLocation, event.name)
                }
            }
            is DualPaneEvent.ShowFileDetails -> {
                scope.launch {
                    fileDetailHandler.loadDetails(event.item)
                }
            }
            is DualPaneEvent.CalculateChecksum -> {
                scope.launch {
                    fileDetailHandler.computeChecksum(event.item)
                }
            }
            is DualPaneEvent.UpdateSortPreferences -> {
                scope.launch {
                    appPreferencesRepository.setSortOrder(event.fileSortOrder)
                    appPreferencesRepository.setSortDirection(event.fileSortDirection)
                }
            }
            is DualPaneEvent.ToggleShowHiddenFiles -> {
                scope.launch {
                    toggleShowHiddenFilesUseCase()
                }
            }
            is DualPaneEvent.ClearSearchHistory -> {
                scope.launch {
                    appPreferencesRepository.clearSearchHistory()
                }
            }
            else -> {}
        }
    }
}
