// [Jalur Class/Modul]: filemanager/src/main/kotlin/com/wakwau/xplore/filemanager/action/PanelRefreshHandler.kt
// [Penjelasan]: Domain Action Handler pemuatan direktori panel via ListDirectoryUseCase dan dispatch event hasil muat ke state panel.
package com.wakwau.xplore.filemanager.action

import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import com.wakwau.xplore.filemanager.event.DualPaneEvent
import com.wakwau.xplore.filemanager.state.PanelId
import com.wakwau.xplore.filemanager.usecase.ListDirectoryUseCase
import kotlinx.coroutines.CancellationException

class PanelRefreshHandler(
    private val listDirectoryUseCase: ListDirectoryUseCase,
    private val dispatch: (DualPaneEvent) -> Unit
) {
    suspend fun loadDirectory(panelId: PanelId, location: StorageLocation) {
        dispatch(DualPaneEvent.LoadingStarted(panelId))
        try {
            when (val result = listDirectoryUseCase(location)) {
                is FileOperationResult.Success -> {
                    dispatch(DualPaneEvent.DirectoryLoaded(panelId, location, result.data))
                }
                is FileOperationResult.Failure -> {
                    dispatch(DualPaneEvent.DirectoryLoadFailed(panelId, result.error.name))
                }
                is FileOperationResult.Cancelled -> {
                    // Pemuatan dibatalkan
                }
                is FileOperationResult.Completed -> {
                    // Selesai
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            dispatch(DualPaneEvent.DirectoryLoadFailed(panelId, e.message ?: "Unknown error"))
        }
    }
}
