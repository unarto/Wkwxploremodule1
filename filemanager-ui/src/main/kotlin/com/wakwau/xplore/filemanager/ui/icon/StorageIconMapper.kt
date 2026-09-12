// [Jalur Class/Modul]: filemanager-ui/src/main/kotlin/com/wakwau/xplore/filemanager/ui/icon/StorageIconMapper.kt
// [Penjelasan]: Menentukan apakah suatu item berkas merupakan root internal storage menggunakan informasi tipe/identitas storage dari domain contract (StorageConstants dan atribut FileItem) tanpa bergantung langsung pada Android Environment API di UI layer sesuai ownership.md.
package com.wakwau.xplore.filemanager.ui.icon

import com.wakwau.xplore.core.storage.constant.StorageConstants
import com.wakwau.xplore.core.storage.model.FileItem

object StorageIconMapper {
    fun isInternalStorage(item: FileItem): Boolean {
        return item.location.rootId == StorageConstants.PRIMARY_INTERNAL_VOLUME_ID ||
               item.name.equals(StorageConstants.DEFAULT_PRIMARY_VOLUME_NAME, ignoreCase = true) ||
               item.location.path.equals(StorageConstants.DEFAULT_PRIMARY_STORAGE_PATH, ignoreCase = true)
    }
}
