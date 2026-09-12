// [Modul: :file-system] [Jalur Class]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/permission/CompositeStoragePermissionChecker.kt
// [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API
package com.wakwau.xplore.core.storage.permission

class CompositeStoragePermissionChecker(
    private val androidPermissionChecker: StoragePermissionChecker,
    private val shizukuPermissionChecker: ShizukuPermissionChecker,
    private val suPermissionChecker: SuPermissionChecker? = null
) : StoragePermissionChecker {

    override fun hasAllFilesAccess(): Boolean {
        return androidPermissionChecker.hasAllFilesAccess() ||
                shizukuPermissionChecker.hasAccess() ||
                (suPermissionChecker?.hasAccess() == true)
    }

    override fun shouldRequestAllFilesAccess(targetPath: String): Boolean {
        return !hasAllFilesAccess() && androidPermissionChecker.shouldRequestAllFilesAccess(targetPath)
    }

    override fun hasAccess(): Boolean {
        return androidPermissionChecker.hasAccess() ||
                shizukuPermissionChecker.hasAccess() ||
                (suPermissionChecker?.hasAccess() == true)
    }

    override fun getRequiredPermissionType(): StoragePermissionType {
        return androidPermissionChecker.getRequiredPermissionType()
    }
}

