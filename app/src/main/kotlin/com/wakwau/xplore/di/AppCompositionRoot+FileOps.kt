// [Jalur Class/Modul]: app/src/main/kotlin/com/wakwau/xplore/di/AppCompositionRoot+FileOps.kt
// [Penjelasan]: Extension provider File Operations pada AppCompositionRoot untuk background client, modul operasi berkas, dan factory orchestrator operasi berkas.

package com.wakwau.xplore.di

import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.FileOperationProgressDispatcher
import com.wakwau.xplore.filemanager.event.DualPaneEvent
import com.wakwau.xplore.fileoperations.client.BackgroundOperationClient
import com.wakwau.xplore.fileoperations.conflict.FileConflict
import com.wakwau.xplore.orchestrator.fileops.CopyOperationOrchestrator
import com.wakwau.xplore.orchestrator.fileops.DeleteOperationOrchestrator
import com.wakwau.xplore.orchestrator.fileops.MoveOperationOrchestrator
import com.wakwau.xplore.orchestrator.fileops.RenameOperationOrchestrator

val AppCompositionRoot.fileOperationProgressDispatcher: FileOperationProgressDispatcher
    get() = fileOpsProgressDispatcherInternal

val AppCompositionRoot.backgroundOperationClient: BackgroundOperationClient
    get() = backgroundOperationClientInternal

val AppCompositionRoot.fileOperationsModule: FileOperationsModule
    get() = fileOperationsModuleInternal

fun AppCompositionRoot.createCopyOrchestrator(
    dispatch: (DualPaneEvent) -> Unit,
    onShowConflict: ((Boolean, List<FileConflict>, StorageLocation, List<StorageLocation>) -> Unit)? = null
): CopyOperationOrchestrator = CopyOperationOrchestrator(
    copyFilesUseCase = fileOperationsModule.copyFilesUseCase,
    detectConflictsUseCase = fileOperationsModule.detectConflictsUseCase,
    resolveTransferUseCase = fileOperationsModule.resolveTransferUseCase,
    storageErrorMapper = storageModule.storageErrorMapper,
    dispatch = dispatch,
    onShowConflict = onShowConflict
)

fun AppCompositionRoot.createMoveOrchestrator(
    dispatch: (DualPaneEvent) -> Unit,
    onShowConflict: ((Boolean, List<FileConflict>, StorageLocation, List<StorageLocation>) -> Unit)? = null
): MoveOperationOrchestrator = MoveOperationOrchestrator(
    moveFilesUseCase = fileOperationsModule.moveFilesUseCase,
    detectConflictsUseCase = fileOperationsModule.detectConflictsUseCase,
    resolveTransferUseCase = fileOperationsModule.resolveTransferUseCase,
    storageErrorMapper = storageModule.storageErrorMapper,
    dispatch = dispatch,
    onShowConflict = onShowConflict
)

fun AppCompositionRoot.createDeleteOrchestrator(
    dispatch: (DualPaneEvent) -> Unit
): DeleteOperationOrchestrator = DeleteOperationOrchestrator(
    deleteFilesUseCase = fileOperationsModule.deleteFilesUseCase,
    storageErrorMapper = storageModule.storageErrorMapper,
    dispatch = dispatch
)

fun AppCompositionRoot.createRenameOrchestrator(
    dispatch: (DualPaneEvent) -> Unit
): RenameOperationOrchestrator = RenameOperationOrchestrator(
    renameFileUseCase = fileOperationsModule.renameFileUseCase,
    dispatch = dispatch
)
