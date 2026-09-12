// [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/mapper/FileItemToIndexMapper.kt
// [Penjelasan]: Mapper konversi FileItem ke FileIndexItem untuk kebutuhan pengindeksan pencarian.
package com.wakwau.xplore.core.storage.mapper

import com.wakwau.xplore.core.storage.model.FileIndexItem
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.FileType
import com.wakwau.xplore.core.utils.mime.MimeTypeDetector
import java.util.Locale

fun FileItem.toIndexItem(): FileIndexItem {
    val isDir = type == FileType.DIRECTORY
    val extension = name.substringAfterLast('.', "").lowercase(Locale.getDefault())
    val category = MimeTypeDetector.getCategory(name, isDir).name
    return FileIndexItem(
        filePath = location.path,
        fileName = name,
        size = metadata.size,
        extension = extension,
        category = category,
        dateModified = metadata.modifiedTime,
        isDirectory = isDir
    )
}
