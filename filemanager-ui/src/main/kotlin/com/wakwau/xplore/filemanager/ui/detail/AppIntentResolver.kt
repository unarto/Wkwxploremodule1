// [Jalur Class/Modul]: filemanager-ui/src/main/kotlin/com/wakwau/xplore/filemanager/ui/detail/AppIntentResolver.kt
// [Penjelasan]: Utilitas penyelesai intent Android di modul filemanager-ui untuk membuka berkas dengan Android system chooser secara aman tanpa memerlukan kueri manual daftar aplikasi atau izin package visibility.
package com.wakwau.xplore.filemanager.ui.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.wakwau.xplore.core.storage.provider.ShareableUriResolver
import com.wakwau.xplore.core.storage.provider.ShareableUriResolverProvider
import com.wakwau.xplore.core.utils.mime.MimeTypeDetector

object AppIntentResolver {

    private fun getSafeUri(context: Context, filePath: String, resolver: ShareableUriResolver?): Uri? {
        return try {
            val resolvedUriString = resolver?.resolveShareableUri(filePath)
            if (resolvedUriString != null) {
                resolvedUriString.toUri()
            } else if (filePath.startsWith("content://")) {
                filePath.toUri()
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun openWith(
        context: Context,
        filePath: String,
        mimeType: String,
        resolver: ShareableUriResolver? = (context.applicationContext as? ShareableUriResolverProvider)?.shareableUriResolver
    ) {
        val fallbackName = if (filePath.startsWith("content://")) "" else filePath.substringAfterLast('/')
        val effectiveMimeType = if (mimeType.isNotBlank()) mimeType else MimeTypeDetector.getMimeType(fallbackName)
        val safeUri = getSafeUri(context, filePath, resolver)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            if (safeUri != null) {
                setDataAndType(safeUri, effectiveMimeType)
            } else {
                type = effectiveMimeType
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserIntent = Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(chooserIntent)
        } catch (_: Throwable) {
            try {
                context.startActivity(intent)
            } catch (_: Throwable) {}
        }
    }
}
