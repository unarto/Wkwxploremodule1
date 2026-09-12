// [Jalur Class/Modul]: core-storage/src/main/kotlin/com/wakwau/xplore/core/storage/db/mapper/FileIndexMapper.kt
// [Penjelasan]: Mapper dua arah antara Room FileIndexEntity dan domain model FileIndexItem.
package com.wakwau.xplore.core.storage.db.mapper

import com.wakwau.xplore.core.storage.db.entity.FileIndexEntity
import com.wakwau.xplore.core.storage.model.FileIndexItem

fun FileIndexEntity.toDomain(): FileIndexItem = FileIndexItem(
    filePath = filePath,
    fileName = fileName,
    size = size,
    extension = extension,
    category = category,
    dateModified = dateModified,
    isDirectory = isDirectory
)

fun FileIndexItem.toEntity(): FileIndexEntity = FileIndexEntity(
    filePath = filePath,
    fileName = fileName,
    size = size,
    extension = extension,
    category = category,
    dateModified = dateModified,
    isDirectory = isDirectory
)
