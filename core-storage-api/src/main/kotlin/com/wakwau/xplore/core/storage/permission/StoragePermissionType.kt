// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/permission/StoragePermissionType.kt
// [Penjelasan]: Enum klasifikasi jenis izin penyimpanan Android dan elevated privileges (Storage, Manage External, Shizuku/Root).
package com.wakwau.xplore.core.storage.permission

enum class StoragePermissionType {
    READ_WRITE_STORAGE,
    MANAGE_EXTERNAL_STORAGE,
    SHIZUKU_ROOT
}
