// [Jalur Class/Modul]: app/src/main/kotlin/com/wakwau/xplore/orchestrator/fileops/CopyOperationOrchestrator.kt
// [Penjelasan]: Orchestrator operasi penyalinan berkas/direktori dengan deteksi konflik, resolusi transfer, dan penanganan status progres/kegagalan.

package com.wakwau.xplore.orchestrator.fileops

import com.wakwau.xplore.core.storage.api.error.StorageErrorMapper
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.filemanager.constant.FileOperationConstants
import com.wakwau.xplore.filemanager.event.DualPaneEvent
import com.wakwau.xplore.filemanager.state.DualPaneState
import com.wakwau.xplore.filemanager.state.PanelId
import com.wakwau.xplore.fileoperations.conflict.ConflictChoice
import com.wakwau.xplore.fileoperations.conflict.DetectConflictsUseCase
import com.wakwau.xplore.fileoperations.conflict.FileConflict
import com.wakwau.xplore.fileoperations.conflict.ResolveTransferUseCase
import com.wakwau.xplore.fileoperations.copy.CopyFilesUseCase
import com.wakwau.xplore.fileoperations.conflict.ResolvedTransferItem
import kotlinx.coroutines.CancellationException

class CopyOperationOrchestrator(
    private val copyFilesUseCase: CopyFilesUseCase,
    private val detectConflictsUseCase: DetectConflictsUseCase,
    private val resolveTransferUseCase: ResolveTransferUseCase,
    private val storageErrorMapper: StorageErrorMapper,
    private val dispatch: (DualPaneEvent) -> Unit,
    private val onEnqueued: (String, List<ResolvedTransferItem>) -> Unit = { _, _ -> },
    private val onShowConflict: ((isMove: Boolean, conflicts: List<FileConflict>, destinationDir: StorageLocation, allSources: List<StorageLocation>) -> Unit)? = null
) {
    suspend fun execute(
        state: DualPaneState,
        itemsToCopy: List<FileItem>,
        destinationPath: String
    ) {
        val targetPanel = state.inactivePanel
        if (itemsToCopy.isEmpty()) return

        try {
            val sources = itemsToCopy.map { it.location }
            val destinationDir = StorageLocation(
                path = destinationPath,
                rootId = targetPanel.currentLocation?.rootId ?: ""
            )

            val conflicts = detectConflictsUseCase(sources, destinationDir)
            if (conflicts.isNotEmpty() && onShowConflict != null) {
                onShowConflict.invoke(false, conflicts, destinationDir, sources)
                return
            }

            val resolved = resolveTransferUseCase(
                sources = sources,
                destinationDir = destinationDir,
                conflictDecisions = emptyMap()
            )

            dispatch(DualPaneEvent.OperationStarted(FileOperationConstants.OPERATION_COPY))
            val operationId = copyFilesUseCase.invoke(resolved)
            onEnqueued(operationId, resolved)

        } catch (e: CancellationException) {
            dispatch(DualPaneEvent.OperationCancelled)
            throw e
        } catch (e: Exception) {
            val error = storageErrorMapper.map(e)
            dispatch(DualPaneEvent.OperationFailed(error.name))
        }
    }

    suspend fun executeResolved(
        sources: List<StorageLocation>,
        destinationDir: StorageLocation,
        decisions: Map<StorageLocation, ConflictChoice>
    ) {
        try {
            val resolved = resolveTransferUseCase(
                sources = sources,
                destinationDir = destinationDir,
                conflictDecisions = decisions
            )

            dispatch(DualPaneEvent.OperationStarted(FileOperationConstants.OPERATION_COPY))
            val operationId = copyFilesUseCase.invoke(resolved)
            onEnqueued(operationId, resolved)

        } catch (e: CancellationException) {
            dispatch(DualPaneEvent.OperationCancelled)
            throw e
        } catch (e: Exception) {
            val error = storageErrorMapper.map(e)
            dispatch(DualPaneEvent.OperationFailed(error.name))
        }
    }
}
