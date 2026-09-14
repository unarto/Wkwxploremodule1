// [Jalur Class/Modul]: core-worker/src/main/kotlin/com/wakwau/xplore/core/worker/service/FileOperationIntentParser.kt
// [Penjelasan]: Helper untuk deserialisasi JSON dari Intent untuk operasi berkas (StorageLocation dan ResolvedTransferItem).
package com.wakwau.xplore.core.worker.service

import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.fileoperations.conflict.ConflictChoice
import com.wakwau.xplore.fileoperations.conflict.ResolvedTransferItem
import org.json.JSONArray
import org.json.JSONObject

object FileOperationIntentParser {
    const val KEY_PATH = "KEY_PATH"
    const val KEY_ROOT_ID = "KEY_ROOT_ID"
    const val KEY_DEST_DIR_PATH = "KEY_DEST_DIR_PATH"
    const val KEY_DEST_DIR_ROOT_ID = "KEY_DEST_DIR_ROOT_ID"
    const val KEY_TARGET_PATH = "KEY_TARGET_PATH"
    const val KEY_TARGET_ROOT_ID = "KEY_TARGET_ROOT_ID"
    const val KEY_ORIGINAL_NAME = "KEY_ORIGINAL_NAME"
    const val KEY_TARGET_NAME = "KEY_TARGET_NAME"
    const val KEY_IS_DIRECTORY = "KEY_IS_DIRECTORY"
    const val KEY_CHOICE = "KEY_CHOICE"

    fun parseResolvedItems(jsonStr: String): List<ResolvedTransferItem> {
        val array = JSONArray(jsonStr)
        val list = mutableListOf<ResolvedTransferItem>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val source = StorageLocation(
                path = obj.getString(KEY_PATH),
                rootId = obj.getString(KEY_ROOT_ID)
            )
            val destDir = StorageLocation(
                path = obj.getString(KEY_DEST_DIR_PATH),
                rootId = obj.getString(KEY_DEST_DIR_ROOT_ID)
            )
            val target = StorageLocation(
                path = obj.getString(KEY_TARGET_PATH),
                rootId = obj.getString(KEY_TARGET_ROOT_ID)
            )
            val origName = obj.getString(KEY_ORIGINAL_NAME)
            val targetName = obj.getString(KEY_TARGET_NAME)
            val isDir = obj.getBoolean(KEY_IS_DIRECTORY)
            val choice = ConflictChoice.valueOf(obj.getString(KEY_CHOICE))
            list.add(
                ResolvedTransferItem(
                    source = source,
                    destinationDir = destDir,
                    targetLocation = target,
                    originalName = origName,
                    targetName = targetName,
                    isDirectory = isDir,
                    choice = choice
                )
            )
        }
        return list
    }

    fun parseStorageLocations(jsonStr: String): List<StorageLocation> {
        val array = JSONArray(jsonStr)
        val list = mutableListOf<StorageLocation>()
        for (i in 0 until array.length()) {
            list.add(parseStorageLocation(array.getJSONObject(i)))
        }
        return list
    }

    fun parseStorageLocation(obj: JSONObject): StorageLocation {
        return StorageLocation(
            path = obj.getString(KEY_PATH),
            rootId = obj.getString(KEY_ROOT_ID)
        )
    }
}
