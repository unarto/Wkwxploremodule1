// [Modul: :file-system] [Jalur Class]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/permission/ShizukuPermissionChecker.kt
// [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API
package com.wakwau.xplore.core.storage.permission

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

class ShizukuPermissionChecker : StoragePermissionChecker {
    override fun hasAllFilesAccess(): Boolean = hasAccess()

    override fun shouldRequestAllFilesAccess(targetPath: String): Boolean = !hasAccess()

    override fun hasAccess(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    override fun getRequiredPermissionType(): StoragePermissionType {
        return StoragePermissionType.SHIZUKU_ROOT
    }

    fun isShizukuAvailable(): Boolean {
        return Shizuku.pingBinder()
    }
    
    fun requestPermission(requestCode: Int) {
        if (isShizukuAvailable() && !hasAccess()) {
            Shizuku.requestPermission(requestCode)
        }
    }
}
