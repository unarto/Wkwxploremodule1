// [Jalur Class/Modul]: app/src/main/kotlin/com/wakwau/xplore/orchestrator/fileops/DeleteOperationOrchestrator.kt
// [Penjelasan]: Orchestrator operasi penghapusan berkas/direktori dengan penanganan status progres, pembatalan, dan kegagalan.

package com.wakwau.xplore.orchestrator.fileops

import com.wakwau.xplore.core.storage.api.error.StorageErrorMapper
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.filemanager.constant.FileOperationConstants
import com.wakwau.xplore.filemanager.event.DualPaneEvent
import com.wakwau.xplore.filemanager.state.DualPaneState
import com.wakwau.xplore.fileoperations.delete.DeleteFilesUseCase
import kotlinx.coroutines.CancellationException

class DeleteOperationOrchestrator(
    private val deleteFilesUseCase: DeleteFilesUseCase,
    private val storageErrorMapper: StorageErrorMapper,
    private val dispatch: (DualPaneEvent) -> Unit
) {
    suspend fun execute(state: DualPaneState, itemsToDelete: List<FileItem>) {
        val sourcePanel = state.activePanel
        if (itemsToDelete.isEmpty()) return

        dispatch(DualPaneEvent.OperationStarted(FileOperationConstants.OPERATION_DELETE))

        try {
            val sources = itemsToDelete.map { it.location }
            deleteFilesUseCase.invoke(sources)
            dispatch(DualPaneEvent.OperationSuccess(FileOperationConstants.SUCCESS_DELETE))
            dispatch(DualPaneEvent.ClearSelection(sourcePanel.id))
            dispatch(DualPaneEvent.Refresh(sourcePanel.id))
        } catch (e: CancellationException) {
            dispatch(DualPaneEvent.OperationCancelled)
            throw e
        } catch (e: Exception) {
            val error = storageErrorMapper.map(e)
            dispatch(DualPaneEvent.OperationFailed(error.name))
        }
    }
}
