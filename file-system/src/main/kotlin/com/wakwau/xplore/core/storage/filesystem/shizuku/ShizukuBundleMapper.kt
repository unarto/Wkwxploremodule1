// [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/filesystem/shizuku/ShizukuBundleMapper.kt
// [Penjelasan]: Helper terisolasi untuk membongkar Bundle data/metadata IPC Shizuku AIDL (IPrivilegedFileService) menjadi entitas domain (FileItem).
package com.wakwau.xplore.core.storage.filesystem.shizuku

import android.os.Bundle
import com.wakwau.xplore.core.storage.constant.StorageConstants
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.FileMetadata
import com.wakwau.xplore.core.storage.model.FileType
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.shizuku.ShizukuIpcConstants

class ShizukuBundleMapper {
    fun mapBundleToFileItem(bundle: Bundle, rootId: String): FileItem? {
        val name = bundle.getString(ShizukuIpcConstants.KEY_NAME) ?: return null
        val path = bundle.getString(ShizukuIpcConstants.KEY_PATH) ?: return null
        val isDirectory = bundle.getBoolean(ShizukuIpcConstants.KEY_IS_DIRECTORY)
        val isHidden = bundle.getBoolean(ShizukuIpcConstants.KEY_IS_HIDDEN)
        
        val type = if (isDirectory) FileType.DIRECTORY else FileType.FILE

        val metadata = FileMetadata(
            size = bundle.getLong(ShizukuIpcConstants.KEY_SIZE),
            modifiedTime = bundle.getLong(ShizukuIpcConstants.KEY_LAST_MODIFIED),
            createdTime = null,
            isReadable = true,
            isWritable = true,
            isExecutable = true,
            isHidden = isHidden || name.startsWith(".")
        )

        return FileItem(
            id = path,
            name = name.ifEmpty { StorageConstants.DEFAULT_UNKNOWN_FILE_NAME },
            location = StorageLocation(path, rootId),
            type = type,
            metadata = metadata
        )
    }
}
