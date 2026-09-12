// [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/shizuku/ShizukuHelper.kt
// [Penjelasan]: Kelas pembantu untuk berinteraksi dengan Shizuku, yang mendelegasikan state lifecycle ke ShizukuConnectionClient tanpa pola Manager.
package com.wakwau.xplore.core.storage.shizuku

object ShizukuHelper {
    @Volatile
    private var connectionClient: ShizukuConnectionClient? = null

    suspend fun getPrivilegedService(packageName: String): IPrivilegedFileService? {
        val client = connectionClient ?: synchronized(this) {
            connectionClient ?: ShizukuConnectionClient(packageName).also {
                connectionClient = it
            }
        }
        return client.getServiceWithRetry()
    }
}

