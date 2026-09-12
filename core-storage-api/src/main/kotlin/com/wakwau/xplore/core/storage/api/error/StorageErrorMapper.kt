// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/api/error/StorageErrorMapper.kt
// [Penjelasan]: Kontrak interface domain murni untuk memetakan Throwable exception ke FileOperationError standar.

package com.wakwau.xplore.core.storage.api.error

import com.wakwau.xplore.core.storage.operation.FileOperationError

interface StorageErrorMapper {
    fun map(throwable: Throwable): FileOperationError
}
