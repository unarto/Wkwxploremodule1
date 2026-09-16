// [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/filesystem/ProtectedPathPolicy.kt
// [Penjelasan]: Kebijakan terpusat untuk memvalidasi direktori root dan sistem yang dilindungi dari modifikasi destruktif (delete, rename) lintas backend filesystem.
package com.wakwau.xplore.core.storage.filesystem

import com.wakwau.xplore.core.storage.constant.StorageConstants

object ProtectedPathPolicy {
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

    fun isProtectedPath(path: String): Boolean {
        val clean = path.trim().trimEnd('/')
        if (clean.isEmpty() || clean == "/" || clean == StorageConstants.ROOT_PATH) return true
        val primaryStorage = StorageConstants.DEFAULT_PRIMARY_STORAGE_PATH.trimEnd('/')
        if (clean.equals(primaryStorage, ignoreCase = true)) return true
        return protectedPaths.contains(clean.lowercase())
    }
}
