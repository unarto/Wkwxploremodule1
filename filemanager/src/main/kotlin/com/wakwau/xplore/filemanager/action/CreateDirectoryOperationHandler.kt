// [Modul: :filemanager] [Jalur Class]: filemanager/src/main/kotlin/com/wakwau/xplore/filemanager/action/CreateDirectoryOperationHandler.kt
// [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API
package com.wakwau.xplore.filemanager.action

import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import com.wakwau.xplore.filemanager.constant.FileOperationConstants
import com.wakwau.xplore.filemanager.event.DualPaneEvent
import com.wakwau.xplore.filemanager.state.DualPaneState
import com.wakwau.xplore.filemanager.usecase.CreateDirectoryUseCase
import kotlinx.coroutines.CancellationException

class CreateDirectoryOperationHandler(
    private val createDirectoryUseCase: CreateDirectoryUseCase,
    private val dispatch: (DualPaneEvent) -> Unit,
    private val onCreated: suspend (FileItem) -> Unit = {}
) {
    suspend fun execute(state: DualPaneState, parentLocation: StorageLocation, name: String) {
        val panel = state.activePanel
        dispatch(DualPaneEvent.OperationStarted(FileOperationConstants.OPERATION_CREATE_DIR))
        try {
            when (val result = createDirectoryUseCase(parentLocation, name)) {
                is FileOperationResult.Success -> {
                    onCreated(result.data)
                    dispatch(DualPaneEvent.OperationSuccess(FileOperationConstants.SUCCESS_CREATE_DIR))
                    dispatch(DualPaneEvent.Refresh(panel.id))
                }
                is FileOperationResult.Failure -> {
                    dispatch(DualPaneEvent.OperationFailed(result.error.name))
                }
                is FileOperationResult.Cancelled -> {
                    dispatch(DualPaneEvent.OperationCancelled)
                }
                is FileOperationResult.Completed -> {
                    dispatch(DualPaneEvent.OperationSuccess(FileOperationConstants.SUCCESS_CREATE_DIR))
                    dispatch(DualPaneEvent.Refresh(panel.id))
                }
            }
        } catch (e: CancellationException) {
            dispatch(DualPaneEvent.OperationCancelled)
            throw e
        } catch (e: Exception) {
            // [Jalur Class/Modul]: filemanager/src/main/kotlin/com/wakwau/xplore/filemanager/action/CreateDirectoryOperationHandler.kt
            // [Penjelasan]: Hindari hardcoded string pada fallback exception dengan merujuk ke FileOperationError.UNKNOWN.name.
            dispatch(DualPaneEvent.OperationFailed(e.message ?: com.wakwau.xplore.core.storage.operation.FileOperationError.UNKNOWN.name))
        }
    }
}
