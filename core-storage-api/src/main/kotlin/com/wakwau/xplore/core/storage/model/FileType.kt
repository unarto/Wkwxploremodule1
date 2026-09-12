// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/model/FileType.kt
// [Penjelasan]: Enum klasifikasi jenis entitas penyimpanan (File, Directory, Symbolic Link, Unknown).
package com.wakwau.xplore.core.storage.model

enum class FileType {
    FILE,
    DIRECTORY,
    LINK,
    UNKNOWN
}
