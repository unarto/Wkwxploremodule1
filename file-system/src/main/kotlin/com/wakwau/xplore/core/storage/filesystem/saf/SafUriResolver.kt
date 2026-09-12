// [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/filesystem/saf/SafUriResolver.kt
// [Penjelasan]: Helper terisolasi untuk resolusi DocumentFile secara aman dari Uri (tree Uri maupun single Uri) dengan penanganan eksplisit IllegalArgumentException.
package com.wakwau.xplore.core.storage.filesystem.saf

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class SafUriResolver(private val context: Context) {

    fun resolveDocumentFile(uri: Uri): DocumentFile? {
        val treeDoc = resolveTreeDocumentFile(uri)
        if (treeDoc != null) {
            val exists = treeDoc.exists()
            if (exists) return treeDoc
        }

        val singleDoc = resolveSingleDocumentFile(uri)
        if (singleDoc != null) {
            val exists = singleDoc.exists()
            if (exists) return singleDoc
        }

        return treeDoc ?: singleDoc
    }

    fun resolveTreeDocumentFile(uri: Uri): DocumentFile? {
        return try {
            DocumentFile.fromTreeUri(context, uri)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun resolveSingleDocumentFile(uri: Uri): DocumentFile? {
        return try {
            DocumentFile.fromSingleUri(context, uri)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun resolveParentDirectory(uri: Uri): DocumentFile? {
        val parentDoc = resolveTreeDocumentFile(uri) ?: resolveDocumentFile(uri) ?: return null
        return if (parentDoc.exists() && !parentDoc.isDirectory) {
            parentDoc.parentFile ?: parentDoc
        } else {
            parentDoc
        }
    }
}
