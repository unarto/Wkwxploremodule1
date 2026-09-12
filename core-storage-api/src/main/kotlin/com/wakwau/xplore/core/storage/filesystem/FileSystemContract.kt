// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/filesystem/FileSystemContract.kt
// [Penjelasan]: Kontrak interface dasar untuk seluruh backend sistem berkas (Local, SAF, Shizuku, Root) yang mendefinisikan operasi I/O dan manajemen direktori secara konsisten tanpa ketergantungan pada Android OS, libsu, atau Shizuku.

package com.wakwau.xplore.core.storage.filesystem

import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.FileOperationProgress
import kotlinx.coroutines.flow.Flow

interface FileSystemContract {
    suspend fun listFiles(location: StorageLocation, showHidden: Boolean = true): List<FileItem>

    suspend fun createDirectory(location: StorageLocation, name: String): FileItem

    suspend fun delete(location: StorageLocation)

    suspend fun rename(location: StorageLocation, newName: String): FileItem

    fun copy(source: StorageLocation, destination: StorageLocation): Flow<FileOperationProgress>

    fun move(source: StorageLocation, destination: StorageLocation): Flow<FileOperationProgress>

    suspend fun getFileItem(location: StorageLocation): FileItem?

    suspend fun exists(location: StorageLocation): Boolean
}
