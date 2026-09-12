// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/operation/FileOperationError.kt
// [Penjelasan]: Enum klasifikasi tipe kesalahan operasi berkas (Not Found, Access Denied, IO Error, dsb.).
package com.wakwau.xplore.core.storage.operation

enum class FileOperationError {
    NOT_FOUND,
    ACCESS_DENIED,
    ALREADY_EXISTS,
    INVALID_NAME,
    INVALID_LOCATION,
    IO_ERROR,
    NOT_SUPPORTED,
    UNKNOWN
}
