// [Jalur Class/Modul]: search/src/test/kotlin/com/wakwau/xplore/search/FileIndexSynchronizerTest.kt
// [Penjelasan]: Unit test untuk FileIndexSynchronizer dalam menyinkronkan penambahan, pembaruan, dan penghapusan entitas indeks FileIndexRepository.
package com.wakwau.xplore.search

import com.wakwau.xplore.core.storage.model.FileIndexItem
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.FileMetadata
import com.wakwau.xplore.core.storage.model.FileType
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.repository.FileIndexRepository
import com.wakwau.xplore.core.storage.repository.DirectoryRepository
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import com.wakwau.xplore.search.sync.FileIndexSynchronizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
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
    private lateinit var fakeDirectoryRepository: FakeDirectoryRepository

    @Before
    fun setup() {
        fakeRepository = FakeFileIndexRepository()
        fakeDirectoryRepository = FakeDirectoryRepository()
        synchronizer = FileIndexSynchronizer(fakeRepository, fakeDirectoryRepository)
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
    fun syncCopied_indexesDestinationSubtree() = runTest {
        val destination = StorageLocation("/target")
        val root = directory("/target/copied")
        val child = file("/target/copied/child.txt")
        fakeDirectoryRepository.contents[destination] = listOf(root)
        fakeDirectoryRepository.contents[root.location] = listOf(child)

        synchronizer.syncCopied(destination, root.location, root.name)

        assertEquals(setOf(root.location.path, child.location.path), fakeRepository.indexMap.keys)
    }

    @Test
    fun syncMoved_removesSourceAndIndexesDestinationSubtree() = runTest {
        synchronizer.syncBatch(listOf(directory("/source"), file("/source/old.txt")))
        val destination = StorageLocation("/target")
        val moved = directory("/target/moved")
        val child = file("/target/moved/child.txt")
        fakeDirectoryRepository.contents[destination] = listOf(moved)
        fakeDirectoryRepository.contents[moved.location] = listOf(child)

        synchronizer.syncMoved(StorageLocation("/source"), destination, moved.location, moved.name)

        assertEquals(1, fakeRepository.replacementCalls)
        assertTrue(fakeRepository.indexMap.keys.none { it.startsWith("/source") })
        assertTrue(fakeRepository.indexMap.keys.containsAll(listOf("/target/moved", "/target/moved/child.txt")))
    }

    @Test
    fun syncRenamed_replacesOldSubtreeWithNewSubtree() = runTest {
        synchronizer.syncBatch(listOf(directory("/old"), file("/old/child.txt")))
        val renamed = directory("/new")
        fakeDirectoryRepository.contents[renamed.location] = listOf(file("/new/child.txt"))

        synchronizer.syncRenamed(StorageLocation("/old"), renamed)

        assertEquals(1, fakeRepository.replacementCalls)
        assertTrue(fakeRepository.indexMap.keys.none { it.startsWith("/old") })
        assertTrue(fakeRepository.indexMap.keys.containsAll(listOf("/new", "/new/child.txt")))
    }

    @Test
    fun syncMoved_destinationCollectionFailure_keepsOldIndex() = runTest {
        synchronizer.syncBatch(listOf(directory("/source"), file("/source/old.txt")))
        fakeDirectoryRepository.failure = true

        val result = runCatching {
            synchronizer.syncMoved(
                StorageLocation("/source"),
                StorageLocation("/target"),
                StorageLocation("/target/moved"),
                "moved"
            )
        }

        assertTrue(result.isFailure)
        assertEquals(0, fakeRepository.replacementCalls)
        assertEquals(setOf("/source", "/source/old.txt"), fakeRepository.indexMap.keys)
    }

    @Test
    fun syncMoved_destinationInsertFailure_rollsBackOldIndex() = runTest {
        synchronizer.syncBatch(listOf(directory("/source"), file("/source/old.txt")))
        val destination = StorageLocation("/target")
        val moved = directory("/target/moved")
        fakeDirectoryRepository.contents[destination] = listOf(moved)
        fakeDirectoryRepository.contents[moved.location] = listOf(file("/target/moved/child.txt"))
        fakeRepository.replacementFailure = ReplacementFailure.BEFORE_INSERT

        val result = runCatching {
            synchronizer.syncMoved(StorageLocation("/source"), destination, moved.location, moved.name)
        }

        assertTrue(result.isFailure)
        assertEquals(1, fakeRepository.replacementCalls)
        assertEquals(setOf("/source", "/source/old.txt"), fakeRepository.indexMap.keys)
    }

    @Test
    fun syncRenamed_transactionFailure_rollsBackAllPartialMutations() = runTest {
        synchronizer.syncBatch(listOf(directory("/old"), file("/old/child.txt")))
        val renamed = directory("/new")
        fakeDirectoryRepository.contents[renamed.location] = listOf(file("/new/child.txt"))
        fakeRepository.replacementFailure = ReplacementFailure.AFTER_FIRST_INSERT

        val result = runCatching {
            synchronizer.syncRenamed(StorageLocation("/old"), renamed)
        }

        assertTrue(result.isFailure)
        assertEquals(1, fakeRepository.replacementCalls)
        assertEquals(setOf("/old", "/old/child.txt"), fakeRepository.indexMap.keys)
    }

    @Test
    fun syncMoved_cancellationBeforeCommit_keepsOldIndex() = runTest {
        synchronizer.syncBatch(listOf(directory("/source"), file("/source/old.txt")))
        val destination = StorageLocation("/target")
        val moved = directory("/target/moved")
        fakeDirectoryRepository.contents[destination] = listOf(moved)
        fakeDirectoryRepository.contents[moved.location] = emptyList()
        fakeRepository.replacementFailure = ReplacementFailure.CANCEL_BEFORE_COMMIT

        val result = runCatching {
            synchronizer.syncMoved(StorageLocation("/source"), destination, moved.location, moved.name)
        }

        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(1, fakeRepository.replacementCalls)
        assertEquals(setOf("/source", "/source/old.txt"), fakeRepository.indexMap.keys)
    }

    @Test
    fun syncRenamed_veryDeepTree_usesIterativeTraversal() = runTest {
        synchronizer.syncBatch(listOf(directory("/old")))
        val root = directory("/new")
        var parent = root
        repeat(10_000) { depth ->
            val child = directory("/deep/$depth")
            fakeDirectoryRepository.contents[parent.location] = listOf(child)
            parent = child
        }
        fakeDirectoryRepository.contents[parent.location] = emptyList()

        synchronizer.syncRenamed(StorageLocation("/old"), root)

        assertEquals(10_001, fakeRepository.indexMap.size)
        assertTrue(fakeRepository.indexMap.keys.none { it.startsWith("/old") })
    }

    @Test
    fun syncMoved_largeTree_isPersistedInBoundedBatches() = runTest {
        synchronizer.syncBatch(listOf(directory("/source")))
        val destination = StorageLocation("/target")
        val moved = directory("/target/moved")
        val children = (1..1_200).map { file("/target/moved/file-$it.txt") }
        fakeDirectoryRepository.contents[destination] = listOf(moved)
        fakeDirectoryRepository.contents[moved.location] = children

        synchronizer.syncMoved(StorageLocation("/source"), destination, moved.location, moved.name)

        assertEquals(listOf(500, 500, 201), fakeRepository.replacementBatchSizes)
        assertEquals(1_201, fakeRepository.indexMap.size)
    }

    @Test
    fun syncMoved_cancellationDuringTraversal_rollsBackOldIndex() = runTest {
        synchronizer.syncBatch(listOf(directory("/source"), file("/source/old.txt")))
        val destination = StorageLocation("/target")
        val moved = directory("/target/moved")
        val nested = directory("/target/moved/nested")
        fakeDirectoryRepository.contents[destination] = listOf(moved)
        fakeDirectoryRepository.contents[moved.location] = listOf(nested)
        fakeDirectoryRepository.cancelledLocations += nested.location

        val result = runCatching {
            synchronizer.syncMoved(StorageLocation("/source"), destination, moved.location, moved.name)
        }

        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(setOf("/source", "/source/old.txt"), fakeRepository.indexMap.keys)
    }

    @Test
    fun syncRenamed_listingFailureDuringTraversal_rollsBackOldIndex() = runTest {
        synchronizer.syncBatch(listOf(directory("/old"), file("/old/child.txt")))
        val renamed = directory("/new")
        val nested = directory("/new/nested")
        fakeDirectoryRepository.contents[renamed.location] = listOf(nested)
        fakeDirectoryRepository.failedLocations += nested.location

        val result = runCatching {
            synchronizer.syncRenamed(StorageLocation("/old"), renamed)
        }

        assertTrue(result.isFailure)
        assertEquals(setOf("/old", "/old/child.txt"), fakeRepository.indexMap.keys)
    }

    @Test
    fun syncCreated_indexesCreatedItem() = runTest {
        val created = directory("/created")
        fakeDirectoryRepository.contents[created.location] = emptyList()

        synchronizer.syncCreated(created)

        assertTrue(fakeRepository.indexMap.containsKey("/created"))
    }

    @Test
    fun syncCopied_doesNotIndexWhenDestinationCannotBeRead() = runTest {
        fakeDirectoryRepository.failure = true

        val result = runCatching {
            synchronizer.syncCopied(StorageLocation("/target"), StorageLocation("/target/missing"), "missing")
        }

        assertTrue(result.isFailure)
        assertTrue(fakeRepository.indexMap.isEmpty())
    }

    private fun directory(path: String) = FileItem(
        id = path,
        name = path.substringAfterLast('/'),
        location = StorageLocation(path),
        type = FileType.DIRECTORY,
        metadata = FileMetadata.EMPTY
    )

    private fun file(path: String) = FileItem(
        id = path,
        name = path.substringAfterLast('/'),
        location = StorageLocation(path),
        type = FileType.FILE,
        metadata = FileMetadata.EMPTY
    )
}

private class FakeDirectoryRepository : DirectoryRepository {
    val contents = mutableMapOf<StorageLocation, List<FileItem>>()
    val failedLocations = mutableSetOf<StorageLocation>()
    val cancelledLocations = mutableSetOf<StorageLocation>()
    var failure = false

    override suspend fun list(location: StorageLocation, showHidden: Boolean): FileOperationResult<List<FileItem>> =
        when {
            failure || location in failedLocations ->
                FileOperationResult.Failure(com.wakwau.xplore.core.storage.operation.FileOperationError.IO_ERROR)
            location in cancelledLocations -> FileOperationResult.Cancelled
            else -> FileOperationResult.Success(contents[location].orEmpty())
        }

    override suspend fun create(location: StorageLocation, name: String): FileOperationResult<FileItem> =
        FileOperationResult.Failure(com.wakwau.xplore.core.storage.operation.FileOperationError.UNKNOWN)
}

private class FakeFileIndexRepository : FileIndexRepository {
    val indexMap = mutableMapOf<String, FileIndexItem>()
    var replacementFailure: ReplacementFailure? = null
    var replacementCalls = 0
    val replacementBatchSizes = mutableListOf<Int>()

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
        replacePrefixIndexBatched(locationPrefix, flowOf(items))
    }

    override suspend fun replacePrefixIndexBatched(
        locationPrefix: String,
        batches: Flow<List<FileIndexItem>>
    ) {
        replacementCalls++
        if (replacementFailure == ReplacementFailure.CANCEL_BEFORE_COMMIT) {
            throw CancellationException("cancelled before transaction commit")
        }
        val snapshot = indexMap.toMap()
        try {
            removeIndexByPrefix(locationPrefix)
            if (replacementFailure == ReplacementFailure.BEFORE_INSERT) {
                error("destination insert failed")
            }
            var inserted = 0
            batches.collect { batch ->
                replacementBatchSizes += batch.size
                for (item in batch) {
                    indexMap[item.filePath] = item
                    inserted++
                    if (replacementFailure == ReplacementFailure.AFTER_FIRST_INSERT && inserted == 1) {
                        error("transaction failed after partial insert")
                    }
                }
            }
        } catch (error: Throwable) {
            indexMap.clear()
            indexMap.putAll(snapshot)
            throw error
        }
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

private enum class ReplacementFailure {
    BEFORE_INSERT,
    AFTER_FIRST_INSERT,
    CANCEL_BEFORE_COMMIT
}
