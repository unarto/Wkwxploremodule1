// [Jalur Class/Modul]: core-worker/src/main/kotlin/com/wakwau/xplore/core/worker/dispatcher/DefaultFileOperationProgressDispatcher.kt
// [Penjelasan]: Implementasi konkret FileOperationProgressDispatcher menggunakan MutableSharedFlow terisolasi di modul :core-worker.
package com.wakwau.xplore.core.worker.dispatcher

import com.wakwau.xplore.core.storage.operation.BackgroundOperationEvent
import com.wakwau.xplore.core.storage.operation.FileOperationProgressDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class DefaultFileOperationProgressDispatcher : FileOperationProgressDispatcher {
    private val _progressFlow = MutableSharedFlow<BackgroundOperationEvent>(extraBufferCapacity = 64)
    override val progressFlow: Flow<BackgroundOperationEvent> = _progressFlow.asSharedFlow()

    override suspend fun emitProgress(event: BackgroundOperationEvent) {
        _progressFlow.emit(event)
    }
}
