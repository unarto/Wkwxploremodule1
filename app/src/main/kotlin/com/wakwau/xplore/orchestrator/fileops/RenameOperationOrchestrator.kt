// [Jalur Class/Modul]: app/src/main/kotlin/com/wakwau/xplore/orchestrator/fileops/RenameOperationOrchestrator.kt
// [Penjelasan]: Orchestrator operasi penggantian nama berkas/direktori dengan penanganan status progres, pembatalan, dan kegagalan.

package com.wakwau.xplore.orchestrator.fileops

import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import com.wakwau.xplore.filemanager.constant.FileOperationConstants
import com.wakwau.xplore.filemanager.event.DualPaneEvent
import com.wakwau.xplore.filemanager.state.DualPaneState
import com.wakwau.xplore.fileoperations.rename.RenameFileUseCase
import kotlinx.coroutines.CancellationException

class RenameOperationOrchestrator(
    private val renameFileUseCase: RenameFileUseCase,
    private val dispatch: (DualPaneEvent) -> Unit,
    private val onRenamed: suspend (oldItem: FileItem, newItem: FileItem) -> Unit = { _, _ -> }
) {
    suspend fun execute(state: DualPaneState, item: FileItem, newName: String) {
        val panel = state.activePanel
        dispatch(DualPaneEvent.OperationStarted(FileOperationConstants.OPERATION_RENAME))

        try {
            when (val result = renameFileUseCase(item.location, newName)) {
                is FileOperationResult.Success -> {
                    onRenamed(item, result.data)
                    // [RenameFix]: Mencegah stale node query & double refresh berdasarkan ubahnama.md
                    dispatch(DualPaneEvent.OperationSuccess(FileOperationConstants.SUCCESS_RENAME))
                    dispatch(DualPaneEvent.ClearSelection(panel.id))
                    // dispatch(DualPaneEvent.Refresh(panel.id))
                }
                is FileOperationResult.Failure -> {
                    dispatch(DualPaneEvent.OperationFailed(result.error.name))
                }
                FileOperationResult.Cancelled -> {
                    dispatch(DualPaneEvent.OperationCancelled)
                }
                is FileOperationResult.Completed -> {
                    // [RenameFix]: Mencegah stale node query & double refresh berdasarkan ubahnama.md
                    dispatch(DualPaneEvent.OperationSuccess(FileOperationConstants.SUCCESS_RENAME))
                    dispatch(DualPaneEvent.ClearSelection(panel.id))
                    // dispatch(DualPaneEvent.Refresh(panel.id))
                }
            }
        } catch (e: CancellationException) {
            dispatch(DualPaneEvent.OperationCancelled)
            throw e
        } catch (e: Exception) {
            dispatch(DualPaneEvent.OperationFailed(e.message ?: "Rename failed"))
        }
    }
}
