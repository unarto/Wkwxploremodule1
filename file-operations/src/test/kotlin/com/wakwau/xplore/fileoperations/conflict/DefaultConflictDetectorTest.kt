// [Jalur Class/Modul]: file-operations/src/test/kotlin/com/wakwau/xplore/fileoperations/conflict/DefaultConflictDetectorTest.kt
// [Penjelasan]: Unit test untuk memverifikasi DefaultConflictDetector di modul file-operations menggunakan abstraksi DirectoryRepository dan DetailedMetadataReader dari core-storage-api.
package com.wakwau.xplore.fileoperations.conflict

import com.wakwau.xplore.core.storage.metadata.DetailedMetadataReader
import com.wakwau.xplore.core.storage.model.FileDetailedMetadata
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.FileMetadata
import com.wakwau.xplore.core.storage.model.FileType
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import com.wakwau.xplore.core.storage.repository.DirectoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DefaultConflictDetectorTest {

    private lateinit var detector: DefaultConflictDetector
    private val directoryContents = mutableMapOf<String, List<FileItem>>()
    private val metadataMap = mutableMapOf<String, FileDetailedMetadata>()

    private val fakeDirectoryRepository = object : DirectoryRepository {
        override suspend fun list(location: StorageLocation, showHidden: Boolean): FileOperationResult<List<FileItem>> {
            val items = directoryContents[location.path] ?: emptyList()
            return FileOperationResult.Success(items)
        }

        override suspend fun create(location: StorageLocation, name: String): FileOperationResult<FileItem> {
            val itemLoc = StorageLocation("${location.path}/$name", location.rootId)
            val newItem = FileItem(
                id = itemLoc.path,
                name = name,
                location = itemLoc,
                type = FileType.DIRECTORY,
                metadata = FileMetadata.EMPTY
            )
            return FileOperationResult.Success(newItem)
        }
    }

    private val fakeMetadataReader = object : DetailedMetadataReader {
        override suspend fun readDetailedMetadata(location: StorageLocation): FileDetailedMetadata {
            return metadataMap[location.path] ?: FileDetailedMetadata(
                fileName = location.path.substringAfterLast('/'),
                fullPath = location.path,
                parentPath = location.path.substringBeforeLast('/'),
                sizeBytes = 100L,
                isDirectory = false,
                lastModifiedTimestamp = 0L,
                isReadable = true,
                isWritable = true,
                isExecutable = false,
                isHidden = false,
                posixPermissions = "rw-r--r--",
                mimeType = "text/plain"
            )
        }
    }

    @Before
    fun setUp() {
        directoryContents.clear()
        metadataMap.clear()
        detector = DefaultConflictDetector(
            directoryRepository = fakeDirectoryRepository,
            detailedMetadataReader = fakeMetadataReader
        )
    }

    private fun createFileItem(name: String, parentPath: String, isDir: Boolean): FileItem {
        val path = "$parentPath/$name"
        val loc = StorageLocation(path, "root1")
        return FileItem(
            id = path,
            name = name,
            location = loc,
            type = if (isDir) FileType.DIRECTORY else FileType.FILE,
            metadata = FileMetadata(
                size = if (isDir) 0L else 1024L,
                modifiedTime = 1000L,
                createdTime = null,
                isReadable = true,
                isWritable = true,
                isExecutable = isDir,
                isHidden = false
            )
        )
    }

    @Test
    fun detectConflicts_whenTargetExists_detectsConflict() = runTest {
        val destDir = StorageLocation("/dest", "root1")
        directoryContents[destDir.path] = listOf(
            createFileItem("document.pdf", "/dest", false)
        )

        val source = StorageLocation("/source/document.pdf", "root1")
        val conflicts = detector.detectConflicts(listOf(source), destDir)

        assertEquals(1, conflicts.size)
        assertEquals("document.pdf", conflicts.first().targetName)
        assertEquals(false, conflicts.first().isDirectory)
    }

    @Test
    fun detectConflicts_whenFolderExists_detectsFolderConflict() = runTest {
        val destDir = StorageLocation("/dest", "root1")
        directoryContents[destDir.path] = listOf(
            createFileItem("Projects", "/dest", true)
        )

        val sourceDir = StorageLocation("/source/Projects", "root1")
        metadataMap[sourceDir.path] = FileDetailedMetadata(
            fileName = "Projects",
            fullPath = "/source/Projects",
            parentPath = "/source",
            sizeBytes = 0L,
            isDirectory = true,
            lastModifiedTimestamp = 1000L,
            isReadable = true,
            isWritable = true,
            isExecutable = true,
            isHidden = false,
            posixPermissions = "rwxr-xr-x",
            mimeType = "inode/directory"
        )

        val conflicts = detector.detectConflicts(listOf(sourceDir), destDir)

        assertEquals(1, conflicts.size)
        assertEquals("Projects", conflicts.first().targetName)
        assertEquals(true, conflicts.first().isDirectory)
    }

    @Test
    fun detectConflicts_whenMultipleItemsExist_detectsMultipleConflicts() = runTest {
        val destDir = StorageLocation("/dest", "root1")
        directoryContents[destDir.path] = listOf(
            createFileItem("a.txt", "/dest", false),
            createFileItem("b.txt", "/dest", false)
        )

        val sources = listOf(
            StorageLocation("/source/a.txt", "root1"),
            StorageLocation("/source/b.txt", "root1"),
            StorageLocation("/source/c.txt", "root1")
        )

        val conflicts = detector.detectConflicts(sources, destDir)

        assertEquals(2, conflicts.size)
        val conflictNames = conflicts.map { it.targetName }.toSet()
        assertTrue(conflictNames.contains("a.txt"))
        assertTrue(conflictNames.contains("b.txt"))
    }

    @Test
    fun detectConflicts_whenDirectoryIntoSelfOrSubdirectory_skipsConflict() = runTest {
        // [Jalur Class/Modul]: file-operations/src/test/kotlin/com/wakwau/xplore/fileoperations/conflict/DefaultConflictDetectorTest.kt
        // [Penjelasan]: Verifikasi bahwa pemindahan direktori ke dirinya sendiri atau subfolder diabaikan tanpa error I/O.
        val sourceDir = StorageLocation("/source/MyFolder", "root1")
        metadataMap[sourceDir.path] = FileDetailedMetadata(
            fileName = "MyFolder",
            fullPath = "/source/MyFolder",
            parentPath = "/source",
            sizeBytes = 0L,
            isDirectory = true,
            lastModifiedTimestamp = 1000L,
            isReadable = true,
            isWritable = true,
            isExecutable = true,
            isHidden = false,
            posixPermissions = "rwxr-xr-x",
            mimeType = "inode/directory"
        )

        // Target adalah dirinya sendiri
        val conflictsSelf = detector.detectConflicts(listOf(sourceDir), StorageLocation("/source/MyFolder", "root1"))
        assertEquals(0, conflictsSelf.size)

        // Target adalah subfolder
        val conflictsSub = detector.detectConflicts(listOf(sourceDir), StorageLocation("/source/MyFolder/sub", "root1"))
        assertEquals(0, conflictsSub.size)
    }

    @Test
    fun detectConflicts_whenSafSourceFileHasConflict_extractsNameFromMetadataAndDetectsConflict() = runTest {
        // [Jalur Class/Modul]: file-operations/src/test/kotlin/com/wakwau/xplore/fileoperations/conflict/DefaultConflictDetectorTest.kt
        // [Penjelasan]: Verifikasi bahwa berkas SAF dengan ID buram URI (misal /12345) mengekstrak nama berkas riil dari DetailedMetadataReader dan mendeteksi konflik dengan benar.
        val destDir = StorageLocation("content://com.android.externalstorage.documents/tree/primary%3ADownload/document/primary%3ADownload", "saf")
        directoryContents[destDir.path] = listOf(
            FileItem(
                id = "${destDir.path}/Laporan_Keuangan.pdf",
                name = "Laporan_Keuangan.pdf",
                location = StorageLocation("${destDir.path}/Laporan_Keuangan.pdf", "saf"),
                type = FileType.FILE,
                metadata = FileMetadata(size = 2048L, modifiedTime = 1000L, createdTime = null, isReadable = true, isWritable = true, isExecutable = false, isHidden = false)
            )
        )

        // Path adalah URI buram dengan ID numerik di ujungnya
        val safSource = StorageLocation("content://com.android.externalstorage.documents/document/primary%3ADownload%2F12345", "saf")
        metadataMap[safSource.path] = FileDetailedMetadata(
            fileName = "Laporan_Keuangan.pdf",
            fullPath = safSource.path,
            parentPath = destDir.path,
            sizeBytes = 2048L,
            isDirectory = false,
            lastModifiedTimestamp = 1000L,
            isReadable = true,
            isWritable = true,
            isExecutable = false,
            isHidden = false,
            posixPermissions = "rw-r--r--",
            mimeType = "application/pdf"
        )

        val conflicts = detector.detectConflicts(listOf(safSource), destDir)

        assertEquals(1, conflicts.size)
        val conflict = conflicts.first()
        assertEquals("Laporan_Keuangan.pdf", conflict.sourceName)
        assertEquals("Laporan_Keuangan.pdf", conflict.targetName)
        assertEquals(false, conflict.isDirectory)
        assertEquals(destDir, conflict.destinationDir)
    }

    @Test
    fun detectConflicts_whenSafSourceDirectoryOnSdCardHasConflict_extractsDirectoryMetadataAndDetectsConflict() = runTest {
        // [Jalur Class/Modul]: file-operations/src/test/kotlin/com/wakwau/xplore/fileoperations/conflict/DefaultConflictDetectorTest.kt
        // [Penjelasan]: Verifikasi bahwa direktori SAF pada volume sekunder (SD Card / OTG) dengan ID buram mendeteksi konflik direktori menggunakan metadata riil.
        val destDir = StorageLocation("content://com.android.externalstorage.documents/tree/0000-0000%3A/document/0000-0000%3A", "saf_sdcard")
        directoryContents[destDir.path] = listOf(
            FileItem(
                id = "${destDir.path}/BackupData",
                name = "BackupData",
                location = StorageLocation("${destDir.path}/BackupData", "saf_sdcard"),
                type = FileType.DIRECTORY,
                metadata = FileMetadata(size = 0L, modifiedTime = 2000L, createdTime = null, isReadable = true, isWritable = true, isExecutable = true, isHidden = false)
            )
        )

        val safDirSource = StorageLocation("content://com.android.externalstorage.documents/tree/0000-0000%3A/document/0000-0000%3A9999", "saf_sdcard")
        metadataMap[safDirSource.path] = FileDetailedMetadata(
            fileName = "BackupData",
            fullPath = safDirSource.path,
            parentPath = destDir.path,
            sizeBytes = 0L,
            isDirectory = true,
            lastModifiedTimestamp = 2000L,
            isReadable = true,
            isWritable = true,
            isExecutable = true,
            isHidden = false,
            posixPermissions = "rwxr-xr-x",
            mimeType = "inode/directory"
        )

        val conflicts = detector.detectConflicts(listOf(safDirSource), destDir)

        assertEquals(1, conflicts.size)
        val conflict = conflicts.first()
        assertEquals("BackupData", conflict.sourceName)
        assertEquals("BackupData", conflict.targetName)
        assertEquals(true, conflict.isDirectory)
    }

    @Test
    fun detectConflicts_whenSafSourceHasNoConflict_returnsEmpty() = runTest {
        // [Jalur Class/Modul]: file-operations/src/test/kotlin/com/wakwau/xplore/fileoperations/conflict/DefaultConflictDetectorTest.kt
        // [Penjelasan]: Verifikasi bahwa item SAF tanpa benturan nama tidak dilaporkan sebagai konflik.
        val destDir = StorageLocation("content://com.android.externalstorage.documents/tree/primary%3APictures/document/primary%3APictures", "saf")
        directoryContents[destDir.path] = listOf(
            FileItem(
                id = "${destDir.path}/Existing.png",
                name = "Existing.png",
                location = StorageLocation("${destDir.path}/Existing.png", "saf"),
                type = FileType.FILE,
                metadata = FileMetadata(size = 100L, modifiedTime = 1000L, createdTime = null, isReadable = true, isWritable = true, isExecutable = false, isHidden = false)
            )
        )

        val safSource = StorageLocation("content://com.android.externalstorage.documents/document/8888", "saf")
        metadataMap[safSource.path] = FileDetailedMetadata(
            fileName = "UniqueVacation.png",
            fullPath = safSource.path,
            parentPath = destDir.path,
            sizeBytes = 100L,
            isDirectory = false,
            lastModifiedTimestamp = 1000L,
            isReadable = true,
            isWritable = true,
            isExecutable = false,
            isHidden = false,
            posixPermissions = "rw-r--r--",
            mimeType = "image/png"
        )

        val conflicts = detector.detectConflicts(listOf(safSource), destDir)
        assertEquals(0, conflicts.size)
    }
}
