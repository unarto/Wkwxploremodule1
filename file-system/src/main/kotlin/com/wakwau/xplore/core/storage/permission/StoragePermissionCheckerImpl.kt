// [Modul: :file-system] [Jalur Class]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/permission/StoragePermissionCheckerImpl.kt
// [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API
package com.wakwau.xplore.core.storage.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat

class StoragePermissionCheckerImpl(
    private val context: Context? = null
) : StoragePermissionChecker {

    override fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            if (context != null) {
                val readGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                val writeGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                readGranted && writeGranted
            } else {
                true
            }
        }
    }

    override fun shouldRequestAllFilesAccess(targetPath: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false
        }
        if (hasAllFilesAccess()) {
            return false
        }
        val normalized = targetPath.trim()
        val externalStorage = try {
            Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"
        } catch (_: Throwable) {
            "/storage/emulated/0"
        }
        return normalized.startsWith("/storage") ||
                normalized.startsWith("/sdcard") ||
                normalized.startsWith("/mnt") ||
                normalized.startsWith(externalStorage)
    }

    override fun hasAccess(): Boolean = hasAllFilesAccess()

    override fun getRequiredPermissionType(): StoragePermissionType {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            StoragePermissionType.MANAGE_EXTERNAL_STORAGE
        } else {
            StoragePermissionType.READ_WRITE_STORAGE
        }
    }
}
