// [Jalur Class/Modul]: file-operations-ui/src/main/kotlin/com/wakwau/xplore/fileoperations/ui/state/OperationUiState.kt
// [Penjelasan]: Status UI untuk operasi berkas termasuk konfirmasi awal, dialog resolusi konflik berkas/folder ganda, progres berjalan, sukses, gagal, dan dibatalkan dipindahkan ke sub-paket state.
package com.wakwau.xplore.fileoperations.ui.state

import com.wakwau.xplore.fileoperations.conflict.ConflictChoice
import com.wakwau.xplore.fileoperations.conflict.FileConflict
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.FileOperationProgress

sealed class OperationUiState {
    object Idle : OperationUiState()
    data class Confirming(
        val isMove: Boolean,
        val items: List<FileItem>,
        val targetPath: String
    ) : OperationUiState()
    data class ConflictResolution(
        val isMove: Boolean,
        val pendingConflicts: List<FileConflict>,
        val currentConflictIndex: Int = 0,
        val destinationDir: StorageLocation,
        val allSources: List<StorageLocation>,
        val resolvedDecisions: Map<StorageLocation, ConflictChoice> = emptyMap()
    ) : OperationUiState() {
        val currentConflict: FileConflict?
            get() = pendingConflicts.getOrNull(currentConflictIndex)
    }
    data class Running(val operationNameRes: Int, val progress: FileOperationProgress? = null) : OperationUiState()
    data class Success(val messageRes: Int) : OperationUiState()
    data class Failure(val errorMessage: String) : OperationUiState()
    object Cancelled : OperationUiState()
}
