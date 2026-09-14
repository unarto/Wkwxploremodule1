// [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/filesystem/root/RootStreamTransferHelper.kt
// [Penjelasan]: Helper terisolasi untuk menangani transfer streaming berkas root dengan SuFileInputStream / SuFileOutputStream, deteksi pembatalan coroutine, dan proteksi partial copy.
package com.wakwau.xplore.core.storage.filesystem.root

import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
import com.wakwau.xplore.core.storage.constant.StorageConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.io.IOException
import java.util.UUID

class RootStreamTransferHelper {

    suspend fun copySingleFile(
        source: SuFile,
        dest: SuFile,
        totalBytes: Long,
        onProgress: suspend (Long, String) -> Unit
    ) {
        dest.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }

        val temporary = SuFile("${dest.absolutePath}.wkw-${UUID.randomUUID()}.tmp")
        val buffer = ByteArray(StorageConstants.Buffer.DEFAULT_I_O_BUFFER_SIZE_BYTES)
        try {
            SuFileInputStream.open(source).use { input ->
                SuFileOutputStream.open(temporary).use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } >= 0) {
                        if (!currentCoroutineContext().isActive) {
                            throw CancellationException("Root copy operation cancelled")
                        }
                        output.write(buffer, 0, bytesRead)
                        onProgress(bytesRead.toLong(), source.name)
                    }
                    output.flush()
                }
            }
            if (temporary.length() != source.length()) {
                throw IOException("Partial copy detected: temporary size (${temporary.length()}) does not match source size (${source.length()})")
            }
            replaceSafely(temporary, dest)
        } catch (e: Throwable) {
            try {
                if (temporary.exists()) {
                    temporary.delete()
                }
            } catch (_: Exception) {
            }
            throw e
        }
    }

    private fun replaceSafely(temporary: SuFile, destination: SuFile) {
        if (!destination.exists()) {
            if (!temporary.renameTo(destination)) throw IOException("Failed to publish root destination: ${destination.absolutePath}")
            return
        }

        val backup = SuFile("${destination.absolutePath}.wkw-${UUID.randomUUID()}.bak")
        if (!destination.renameTo(backup)) throw IOException("Failed to preserve existing root destination: ${destination.absolutePath}")
        try {
            if (!temporary.renameTo(destination)) throw IOException("Failed to publish root destination: ${destination.absolutePath}")
            if (!backup.delete() && backup.exists()) throw IOException("Failed to remove root destination backup: ${backup.absolutePath}")
        } catch (error: Throwable) {
            if (destination.exists()) destination.delete()
            backup.renameTo(destination)
            throw error
        }
    }
}
