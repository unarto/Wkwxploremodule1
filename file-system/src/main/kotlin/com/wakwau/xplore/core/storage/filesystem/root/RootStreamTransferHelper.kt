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

        val buffer = ByteArray(StorageConstants.Buffer.DEFAULT_I_O_BUFFER_SIZE_BYTES)
        try {
            SuFileInputStream.open(source).use { input ->
                SuFileOutputStream.open(dest).use { output ->
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
            if (dest.length() != source.length()) {
                throw IOException("Partial copy detected: destination size (${dest.length()}) does not match source size (${source.length()})")
            }
        } catch (e: Throwable) {
            try {
                if (dest.exists()) {
                    dest.delete()
                }
            } catch (_: Exception) {
            }
            throw e
        }
    }
}
