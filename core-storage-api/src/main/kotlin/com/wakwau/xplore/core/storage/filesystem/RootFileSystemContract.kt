// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/filesystem/RootFileSystemContract.kt
// [Penjelasan]: Kontrak interface sistem berkas Root Superuser (SU) murni tanpa ketergantungan pada Android OS, libsu, atau binary runtime.

package com.wakwau.xplore.core.storage.filesystem

interface RootFileSystemContract : FileSystemContract {
    fun isAvailable(): Boolean
}
