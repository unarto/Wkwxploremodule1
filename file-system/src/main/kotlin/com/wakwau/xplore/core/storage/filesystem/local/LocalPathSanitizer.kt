// [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/filesystem/local/LocalPathSanitizer.kt
// [Penjelasan]: Helper terisolasi untuk memeriksa dan memvalidasi path terlindungi serta mencegah path traversal pada sistem berkas lokal.
package com.wakwau.xplore.core.storage.filesystem.local

import com.wakwau.xplore.core.storage.constant.StorageConstants

object LocalPathSanitizer {

    private val protectedPaths = setOf(
        "/storage",
        "/storage/emulated",
        "/system",
        "/vendor",
        "/apex",
        "/proc",
        "/sys",
        "/dev",
        "/etc",
        "/bin",
        "/sbin"
    )

    fun isRootOrProtectedPath(path: String): Boolean {
        val clean = path.trim().trimEnd('/')
        if (clean.isEmpty() || clean == "/" || clean == StorageConstants.ROOT_PATH) return true
        val primaryStorage = StorageConstants.DEFAULT_PRIMARY_STORAGE_PATH.trimEnd('/')
        if (clean.equals(primaryStorage, ignoreCase = true)) return true
        return protectedPaths.contains(clean.lowercase())
    }
}
