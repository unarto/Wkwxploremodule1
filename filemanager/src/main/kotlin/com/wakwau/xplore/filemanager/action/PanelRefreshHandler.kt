// [Jalur Class/Modul]: filemanager/src/main/kotlin/com/wakwau/xplore/filemanager/action/PanelRefreshHandler.kt
// [Penjelasan]: Domain Action Handler pemuatan direktori panel via ListDirectoryUseCase dan dispatch event hasil muat ke state panel.
package com.wakwau.xplore.filemanager.action

import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import com.wakwau.xplore.filemanager.event.DualPaneEvent
import com.wakwau.xplore.filemanager.state.PanelId
import com.wakwau.xplore.filemanager.usecase.ListDirectoryUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive

class PanelRefreshHandler(
    private val listDirectoryUseCase: ListDirectoryUseCase,
    private val dispatch: (DualPaneEvent) -> Unit
) {
    private val requests = mutableMapOf<PanelId, Long>()

    @Synchronized
    fun beginRequest(panelId: PanelId): Long =
        (requests.getOrDefault(panelId, 0L) + 1).also { requests[panelId] = it }

    @Synchronized
    private fun isCurrent(panelId: PanelId, requestId: Long): Boolean = requests[panelId] == requestId

    suspend fun loadDirectory(panelId: PanelId, location: StorageLocation, requestId: Long = beginRequest(panelId)) {
        if (!isCurrent(panelId, requestId)) return
        dispatch(DualPaneEvent.LoadingStarted(panelId, requestId))
        try {
            val result = listDirectoryUseCase(location)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (!isCurrent(panelId, requestId)) return
            when (result) {
                is FileOperationResult.Success -> {
                    dispatch(DualPaneEvent.DirectoryLoaded(panelId, location, result.data, requestId))
                }
                is FileOperationResult.Failure -> {
                    dispatch(DualPaneEvent.DirectoryLoadFailed(panelId, result.error.name, requestId))
                }
                is FileOperationResult.Cancelled -> {
                    throw CancellationException("Directory listing cancelled")
                }
                is FileOperationResult.Completed -> {
                    error("Unexpected directory listing completion")
                }
            }
        } catch (e: CancellationException) {
            if (isCurrent(panelId, requestId)) dispatch(DualPaneEvent.DirectoryLoadFailed(panelId, e.message ?: "Directory listing cancelled", requestId))
            throw e
        } catch (e: Throwable) {
            if (isCurrent(panelId, requestId)) dispatch(DualPaneEvent.DirectoryLoadFailed(panelId, e.message ?: "Unknown error", requestId))
        }
    }
}
