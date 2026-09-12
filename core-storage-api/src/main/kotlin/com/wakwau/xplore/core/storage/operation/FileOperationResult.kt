// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/operation/FileOperationResult.kt
// [Penjelasan]: Sealed class hasil operasi berkas asynchronous (Success, Failure, Cancelled, Completed).
package com.wakwau.xplore.core.storage.operation

sealed class FileOperationResult<out T> {
    data class Success<out T>(val data: T) : FileOperationResult<T>()
    data class Failure(val error: FileOperationError) : FileOperationResult<Nothing>()
    data object Cancelled : FileOperationResult<Nothing>()
    data class Completed(val operationType: BackgroundOperationType) : FileOperationResult<Nothing>()
}
