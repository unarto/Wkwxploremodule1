// [Jalur Class/Modul]: file-operations/src/main/kotlin/com/wakwau/xplore/fileoperations/state/OperationState.kt
// [Penjelasan]: Model state domain murni yang merepresentasikan siklus hidup eksekusi operasi berkas (Idle, Preparing, Running, Succeeded, Failed, Cancelled) tanpa ketergantungan pada UI toolkit.
package com.wakwau.xplore.fileoperations.state

import com.wakwau.xplore.core.storage.operation.FileOperationError
import com.wakwau.xplore.core.storage.operation.FileOperationProgress

sealed class OperationState {
    object Idle : OperationState()

    data class Preparing(
        val totalItems: Int
    ) : OperationState()

    data class Running(
        val progress: FileOperationProgress
    ) : OperationState()

    data class Succeeded(
        val completedItemsCount: Int
    ) : OperationState()

    data class Failed(
        val error: FileOperationError,
        val message: String? = null
    ) : OperationState()

    object Cancelled : OperationState()
}
