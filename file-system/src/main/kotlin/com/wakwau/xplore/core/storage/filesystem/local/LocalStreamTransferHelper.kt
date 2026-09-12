// [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/filesystem/local/LocalStreamTransferHelper.kt
// [Penjelasan]: Helper terisolasi untuk menangani transfer streaming byte I/O lokal (single file copy, channel transfer, buffer fallback, dan verifikasi integritas ukuran).
package com.wakwau.xplore.core.storage.filesystem.local

import com.wakwau.xplore.core.storage.constant.StorageConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class LocalStreamTransferHelper {

    suspend fun copySingleFile(
        source: File,
        dest: File,
        totalBytes: Long,
        onProgress: suspend (Long, String) -> Unit
    ) {
        try {
            val src = source.absolutePath
            val destPath = dest.absolutePath
            // [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/filesystem/local/LocalStreamTransferHelper.kt
            FileInputStream(source).use { input ->
                FileOutputStream(dest).use { output ->
                    val inputChannel = input.channel
                    val outputChannel = output.channel
                    val size = inputChannel.size()
                    var position = 0L
                    val chunkSize = 131072L // 128 KB
                    
                    while (position < size) {
                        if (!currentCoroutineContext().isActive) {
                            throw CancellationException("Local copy cancelled")
                        }
                        val transferred = inputChannel.transferTo(position, chunkSize, outputChannel)
                        if (transferred > 0L) {
                            position += transferred
                            onProgress(transferred, source.name)
                        } else {
                            // Fallback to stream buffer if transferTo stalls
                            val buffer = ByteArray(StorageConstants.Buffer.DEFAULT_I_O_BUFFER_SIZE_BYTES)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } >= 0) {
                                if (!currentCoroutineContext().isActive) {
                                    throw CancellationException("Local copy cancelled")
                                }
                                output.write(buffer, 0, bytesRead)
                                onProgress(bytesRead.toLong(), source.name)
                            }
                            break
                        }
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
            } catch (ex: Exception) {
                android.util.Log.w("FileSystem", "Failed to clean partial file", ex)
            }
            throw e
        }
    }
}
