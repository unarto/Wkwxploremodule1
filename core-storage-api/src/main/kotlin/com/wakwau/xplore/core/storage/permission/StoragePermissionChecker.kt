// [Modul: :core-storage-api] [Jalur Class]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/permission/StoragePermissionChecker.kt
// [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API
package com.wakwau.xplore.core.storage.permission

interface StoragePermissionChecker {
    fun hasAllFilesAccess(): Boolean
    fun shouldRequestAllFilesAccess(targetPath: String): Boolean

    fun hasAccess(): Boolean = hasAllFilesAccess()
    fun getRequiredPermissionType(): StoragePermissionType = StoragePermissionType.MANAGE_EXTERNAL_STORAGE
}

