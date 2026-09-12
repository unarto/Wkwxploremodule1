// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/operation/FileOperationComponentProvider.kt
// [Penjelasan]: Antarmuka penyedia dependensi operasi berkas bagi Android Service (seperti FileCopyService) tanpa Service Locator atau mutable global state.
package com.wakwau.xplore.core.storage.operation

import com.wakwau.xplore.core.storage.repository.FileRepository

interface FileOperationComponentProvider {
    val fileRepository: FileRepository
    val operationProgressDispatcher: FileOperationProgressDispatcher
}
