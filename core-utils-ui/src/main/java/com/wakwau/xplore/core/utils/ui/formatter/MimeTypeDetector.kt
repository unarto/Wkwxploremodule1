// [Jalur Class/Modul]: core-utils-ui/src/main/java/com/wakwau/xplore/core/utils/ui/formatter/MimeTypeDetector.kt
// [Penjelasan]: Utilitas deteksi MIME type dan klasifikasi FileCategory untuk kebutuhan visual/presentasi di bawah modul :core-utils-ui yang mendelegasikan ke :core-utils.
package com.wakwau.xplore.core.utils.ui.formatter

import com.wakwau.xplore.core.utils.mime.MimeTypeDetector as CoreMimeTypeDetector

typealias FileCategory = com.wakwau.xplore.core.utils.mime.FileCategory

object MimeTypeDetector {
    fun getMimeType(fileName: String): String = CoreMimeTypeDetector.getMimeType(fileName)
    fun getCategory(fileName: String, isDirectory: Boolean): FileCategory = CoreMimeTypeDetector.getCategory(fileName, isDirectory)
    fun isTextOrCode(fileName: String): Boolean = CoreMimeTypeDetector.isTextOrCode(fileName)
    fun isTextOrCode(category: FileCategory): Boolean = category == FileCategory.TEXT || category == FileCategory.CODE
}
