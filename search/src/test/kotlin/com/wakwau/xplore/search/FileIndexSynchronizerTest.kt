// [Jalur Class/Modul]: search/src/test/kotlin/com/wakwau/xplore/search/FileIndexSynchronizerTest.kt
// [Penjelasan]: Unit test untuk FileIndexSynchronizer dalam menyinkronkan penambahan, pembaruan, dan penghapusan entitas indeks FileIndexRepository.
package com.wakwau.xplore.search

import com.wakwau.xplore.core.storage.model.FileIndexItem
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.FileMetadata
import com.wakwau.xplore.core.storage.model.FileType
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.repository.FileIndexRepository
import com.wakwau.xplore.search.sync.FileIndexSynchronizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FileIndexSynchronizerTest {

    private lateinit var fakeRepository: FakeFileIndexRepository
    private lateinit var synchronizer: FileIndexSynchronizer

    @Before
    fun setup() {
        fakeRepository = FakeFileIndexRepository()
        synchronizer = FileIndexSynchronizer(fakeRepository)
    }

    @Test
    fun syncSingle_addsEntityToRepository() = runTest {
        val item = FileItem(
            id = "/storage/emulated/0/test.txt",
            name = "test.txt",
            location = StorageLocation("/storage/emulated/0/test.txt"),
            type = FileType.FILE,
            metadata = FileMetadata.EMPTY.copy(size = 100L, modifiedTime = 12345L)
        )

        // [Jalur Class/Modul]: search/src/test/kotlin/com/wakwau/xplore/search/FileIndexSynchronizerTest.kt
        // [Penjelasan]: Menguji bahwa syncSingle mengonversi FileItem dan menyimpan entitas ke dalam repository.
        synchronizer.syncSingle(item)

        assertEquals(1, fakeRepository.indexMap.size)
        val indexed = fakeRepository.indexMap["/storage/emulated/0/test.txt"]
        assertEquals("test.txt", indexed?.fileName)
        assertEquals("txt", indexed?.extension)
        assertEquals(100L, indexed?.size)
        assertEquals(false, indexed?.isDirectory)
    }

    @Test
    fun syncBatch_addsMultipleEntities() = runTest {
        val items = listOf(
            FileItem(
                id = "/storage/emulated/0/a.jpg",
                name = "a.jpg",
                location = StorageLocation("/storage/emulated/0/a.jpg"),
                type = FileType.FILE,
                metadata = FileMetadata.EMPTY.copy(size = 500L)
            ),
            FileItem(
                id = "/storage/emulated/0/folder",
                name = "folder",
                location = StorageLocation("/storage/emulated/0/folder"),
                type = FileType.DIRECTORY,
                metadata = FileMetadata.EMPTY
            )
        )

        // [Jalur Class/Modul]: search/src/test/kotlin/com/wakwau/xplore/search/FileIndexSynchronizerTest.kt
        // [Penjelasan]: Menguji bahwa syncBatch memproses dan menyimpan daftar FileItem secara bersamaan.
        synchronizer.syncBatch(items)

        assertEquals(2, fakeRepository.indexMap.size)
        assertTrue(fakeRepository.indexMap["/storage/emulated/0/folder"]?.isDirectory == true)
    }

    @Test
    fun removeSingle_removesFromRepository() = runTest {
        val item = FileItem(
            id = "/storage/emulated/0/delete.txt",
            name = "delete.txt",
            location = StorageLocation("/storage/emulated/0/delete.txt"),
            type = FileType.FILE,
            metadata = FileMetadata.EMPTY
        )
        synchronizer.syncSingle(item)
        assertEquals(1, fakeRepository.indexMap.size)

        // [Jalur Class/Modul]: search/src/test/kotlin/com/wakwau/xplore/search/FileIndexSynchronizerTest.kt
        // [Penjelasan]: Menguji penghapusan entitas tunggal dari repository berdasarkan filePath.
        synchronizer.removeSingle("/storage/emulated/0/delete.txt")

        assertEquals(0, fakeRepository.indexMap.size)
    }

    @Test
    fun removeByPrefix_removesSubtree() = runTest {
        val items = listOf(
            FileItem(
                id = "/storage/emulated/0/folder/sub1.txt",
                name = "sub1.txt",
                location = StorageLocation("/storage/emulated/0/folder/sub1.txt"),
                type = FileType.FILE,
                metadata = FileMetadata.EMPTY
            ),
            FileItem(
                id = "/storage/emulated/0/folder/sub2.txt",
                name = "sub2.txt",
                location = StorageLocation("/storage/emulated/0/folder/sub2.txt"),
                type = FileType.FILE,
                metadata = FileMetadata.EMPTY
            ),
            FileItem(
                id = "/storage/emulated/0/other.txt",
                name = "other.txt",
                location = StorageLocation("/storage/emulated/0/other.txt"),
                type = FileType.FILE,
                metadata = FileMetadata.EMPTY
            )
        )
        synchronizer.syncBatch(items)
        assertEquals(3, fakeRepository.indexMap.size)

        // [Jalur Class/Modul]: search/src/test/kotlin/com/wakwau/xplore/search/FileIndexSynchronizerTest.kt
        // [Penjelasan]: Menguji penghapusan hierarki prefix sehingga hanya item di luar prefix yang tersisa.
        synchronizer.removeByPrefix("/storage/emulated/0/folder")

        assertEquals(1, fakeRepository.indexMap.size)
        assertTrue(fakeRepository.indexMap.containsKey("/storage/emulated/0/other.txt"))
    }

    @Test
    fun syncRename_updatesOldAndNewKeys() = runTest {
        val oldPath = "/storage/emulated/0/old.txt"
        val oldItem = FileItem(
            id = oldPath,
            name = "old.txt",
            location = StorageLocation(oldPath),
            type = FileType.FILE,
            metadata = FileMetadata.EMPTY
        )
        synchronizer.syncSingle(oldItem)

        val newItem = FileItem(
            id = "/storage/emulated/0/renamed.txt",
            name = "renamed.txt",
            location = StorageLocation("/storage/emulated/0/renamed.txt"),
            type = FileType.FILE,
            metadata = FileMetadata.EMPTY
        )

        // [Jalur Class/Modul]: search/src/test/kotlin/com/wakwau/xplore/search/FileIndexSynchronizerTest.kt
        // [Penjelasan]: Menguji bahwa syncRename menghapus path lama dan menambahkan entitas baru secara konsisten.
        synchronizer.syncRename(oldPath, newItem)

        assertEquals(1, fakeRepository.indexMap.size)
        assertTrue(!fakeRepository.indexMap.containsKey(oldPath))
        assertTrue(fakeRepository.indexMap.containsKey("/storage/emulated/0/renamed.txt"))
    }
}

private class FakeFileIndexRepository : FileIndexRepository {
    val indexMap = mutableMapOf<String, FileIndexItem>()

    override suspend fun addOrUpdateIndex(item: FileIndexItem) {
        indexMap[item.filePath] = item
    }

    override suspend fun addOrUpdateIndexBatch(items: List<FileIndexItem>) {
        items.forEach { indexMap[it.filePath] = it }
    }

    override suspend fun removeIndex(filePath: String) {
        indexMap.remove(filePath)
        val prefix = if (filePath.endsWith("/")) filePath else "$filePath/"
        indexMap.keys.removeAll { it.startsWith(prefix) }
    }

    override suspend fun removeIndexBatch(filePaths: List<String>) {
        filePaths.forEach { removeIndex(it) }
    }

    override suspend fun removeIndexByPrefix(locationPrefix: String) {
        indexMap.keys.removeAll { it.startsWith(locationPrefix) }
    }

    override suspend fun removeIndexByPrefixes(locationPrefixes: List<String>) {
        locationPrefixes.forEach { removeIndexByPrefix(it) }
    }

    override suspend fun replacePrefixIndex(locationPrefix: String, items: List<FileIndexItem>) {
        removeIndexByPrefix(locationPrefix)
        addOrUpdateIndexBatch(items)
    }

    override suspend fun syncRename(oldPath: String, newItem: FileIndexItem) {
        removeIndex(oldPath)
        addOrUpdateIndex(newItem)
    }

    override suspend fun syncMove(sourcePath: String, destinationItem: FileIndexItem) {
        removeIndex(sourcePath)
        addOrUpdateIndex(destinationItem)
    }

    override suspend fun clearIndex() {
        indexMap.clear()
    }

    override fun searchFiles(
        locationPrefix: String,
        keyword: String,
        minSize: Long?,
        maxSize: Long?,
        extension: String?
    ): Flow<List<FileIndexItem>> {
        return flowOf(indexMap.values.filter { 
            it.fileName.contains(keyword, ignoreCase = true) && 
            it.filePath.startsWith(locationPrefix)
        })
    }

    override fun getFilesByCategory(category: String): Flow<List<FileIndexItem>> {
        return flowOf(indexMap.values.filter { it.category == category })
    }
}
