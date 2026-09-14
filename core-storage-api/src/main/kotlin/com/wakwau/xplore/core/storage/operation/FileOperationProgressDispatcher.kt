// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/operation/FileOperationProgressDispatcher.kt
// [Penjelasan]: Antarmuka penyalur progres operasi berkas latar belakang ke subscriber tanpa state mutable global.
package com.wakwau.xplore.core.storage.operation

import kotlinx.coroutines.flow.Flow

interface FileOperationProgressDispatcher {
    val progressFlow: Flow<BackgroundOperationEvent>
    suspend fun emitProgress(event: BackgroundOperationEvent)
}
