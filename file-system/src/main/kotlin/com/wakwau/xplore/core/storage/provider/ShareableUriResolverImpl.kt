// [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/provider/ShareableUriResolverImpl.kt
// [Penjelasan]: Implementasi ShareableUriResolver berbasis Android FileProvider dan pengecekan keberadaan fisik berkas java.io.File di layer infrastructure file-system.
package com.wakwau.xplore.core.storage.provider

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File

class ShareableUriResolverImpl(
    private val context: Context,
    private val authority: String = "${context.packageName}.fileprovider"
) : ShareableUriResolver {

    override fun resolveShareableUri(filePath: String): String? {
        return try {
            if (filePath.startsWith("content://")) {
                filePath
            } else {
                val file = File(filePath)
                if (file.exists()) {
                    FileProvider.getUriForFile(
                        context,
                        authority,
                        file
                    ).toString()
                } else {
                    null
                }
            }
        } catch (_: Throwable) {
            null
        }
    }
}
