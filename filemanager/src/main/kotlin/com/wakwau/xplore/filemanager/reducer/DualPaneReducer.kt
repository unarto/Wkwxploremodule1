// [Jalur Class/Modul]: filemanager/src/main/kotlin/com/wakwau/xplore/filemanager/reducer/DualPaneReducer.kt
// [Penjelasan]: Reducer domain murni untuk mengolah transisi status immutable panel ganda, dialog input berkas (buat folder, ganti nama, konfirmasi hapus), rincian metadata, dan perizinan.
package com.wakwau.xplore.filemanager.reducer

import com.wakwau.xplore.filemanager.event.DualPaneEvent
import com.wakwau.xplore.filemanager.state.ChecksumState
import com.wakwau.xplore.filemanager.state.DualPaneState
import com.wakwau.xplore.filemanager.state.FileDetailState
import com.wakwau.xplore.filemanager.state.PanelId
import com.wakwau.xplore.filemanager.state.PanelState

class DualPaneReducer {

    fun reduce(state: DualPaneState, event: DualPaneEvent): DualPaneState {
        return when (event) {
            is DualPaneEvent.SetActivePanel -> {
                state.copy(activePanelId = event.panelId)
            }
            is DualPaneEvent.OpenLocation -> {
                updatePanel(state, event.panelId) {
                    it.copy(currentLocation = event.location, isLoading = true, error = null)
                }
            }
            is DualPaneEvent.LoadingStarted -> {
                updatePanel(state, event.panelId) {
                    it.copy(isLoading = true, error = null)
                }
            }
            is DualPaneEvent.DirectoryLoaded -> {
                updatePanel(state, event.panelId) {
                    it.copy(
                        currentLocation = event.location,
                        items = event.items,
                        isLoading = false,
                        error = null
                        // [DualPaneReducer.kt]: Mencegah auto-clear selection saat direktori selesai dimuat (berdasarkan Mark.md)
                        // selectedItemIds = emptySet() // clear selection on load
                    )
                }
            }
            is DualPaneEvent.DirectoryLoadFailed -> {
                updatePanel(state, event.panelId) {
                    it.copy(isLoading = false, error = event.error)
                }
            }
            is DualPaneEvent.ToggleSelection -> {
                updatePanel(state, event.panelId) {
                    val newSelection = if (it.selectedItemIds.contains(event.itemId)) {
                        it.selectedItemIds - event.itemId
                    } else {
                        it.selectedItemIds + event.itemId
                    }
                    it.copy(selectedItemIds = newSelection)
                }
            }
            is DualPaneEvent.SetSelectedItems -> {
                updatePanel(state, event.panelId) {
                    it.copy(selectedItemIds = event.itemIds)
                }
            }
            is DualPaneEvent.ClearSelection -> {
                updatePanel(state, event.panelId) {
                    it.copy(selectedItemIds = emptySet())
                }
            }

            // File Details & Checksum
            is DualPaneEvent.ShowFileDetails -> {
                state.copy(
                    fileDetailState = FileDetailState(
                        isVisible = true,
                        selectedItem = event.item,
                        isLoadingMetadata = true,
                        checksumState = ChecksumState.Idle
                    )
                )
            }
            is DualPaneEvent.DismissFileDetails -> {
                state.copy(fileDetailState = FileDetailState(isVisible = false))
            }
            is DualPaneEvent.FileDetailsLoadingStarted -> {
                state.copy(fileDetailState = state.fileDetailState.copy(isLoadingMetadata = true, errorMessage = null))
            }
            is DualPaneEvent.FileDetailsLoaded -> {
                state.copy(fileDetailState = state.fileDetailState.copy(isLoadingMetadata = false, metadata = event.metadata, errorMessage = null))
            }
            is DualPaneEvent.FileDetailsFailed -> {
                state.copy(fileDetailState = state.fileDetailState.copy(isLoadingMetadata = false, errorRes = event.errorRes, errorMessage = event.errorMessage))
            }
            is DualPaneEvent.ChecksumCalculationStarted -> {
                state.copy(fileDetailState = state.fileDetailState.copy(checksumState = ChecksumState.Calculating))
            }
            is DualPaneEvent.ChecksumCalculated -> {
                state.copy(fileDetailState = state.fileDetailState.copy(checksumState = ChecksumState.Success(event.checksum)))
            }
            is DualPaneEvent.ChecksumCalculationFailed -> {
                state.copy(fileDetailState = state.fileDetailState.copy(checksumState = ChecksumState.Error(event.errorRes, event.errorMessage)))
            }
            is DualPaneEvent.CalculateChecksum -> {
                state.copy(fileDetailState = state.fileDetailState.copy(checksumState = ChecksumState.Calculating))
            }

            is DualPaneEvent.PermissionStatusUpdated -> {
                state.copy(
                    hasPermission = event.hasPermission,
                    requiredPermissionType = event.requiredPermissionType
                )
            }
            is DualPaneEvent.StorageVolumesUpdated -> {
                state.copy(
                    storageVolumes = event.volumes,
                )
            }

            // Intents handled by external observers / handlers or presentation
            else -> {
                state
            }
        }
    }

    private fun updatePanel(
        state: DualPaneState,
        panelId: PanelId,
        updater: (PanelState) -> PanelState
    ): DualPaneState {
        return if (panelId == PanelId.LEFT) {
            state.copy(leftPanel = updater(state.leftPanel))
        } else {
            state.copy(rightPanel = updater(state.rightPanel))
        }
    }
}
