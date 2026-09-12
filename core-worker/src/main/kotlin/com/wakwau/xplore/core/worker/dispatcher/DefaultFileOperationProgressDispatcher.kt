// [Jalur Class/Modul]: core-worker/src/main/kotlin/com/wakwau/xplore/core/worker/dispatcher/DefaultFileOperationProgressDispatcher.kt
// [Penjelasan]: Implementasi konkret FileOperationProgressDispatcher menggunakan MutableSharedFlow terisolasi di modul :core-worker.
package com.wakwau.xplore.core.worker.dispatcher

import com.wakwau.xplore.core.storage.operation.FileOperationProgress
import com.wakwau.xplore.core.storage.operation.FileOperationProgressDispatcher
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class DefaultFileOperationProgressDispatcher : FileOperationProgressDispatcher {
    private val _progressFlow = MutableSharedFlow<FileOperationResult<FileOperationProgress>>(extraBufferCapacity = 64)
    override val progressFlow: Flow<FileOperationResult<FileOperationProgress>> = _progressFlow.asSharedFlow()

    override suspend fun emitProgress(result: FileOperationResult<FileOperationProgress>) {
        _progressFlow.emit(result)
    }
}
