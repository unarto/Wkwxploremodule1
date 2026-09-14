// [Jalur Class/Modul]: file-operations/src/test/kotlin/com/wakwau/xplore/fileoperations/conflict/ResolveTransferUseCaseTest.kt
// [Penjelasan]: Unit test untuk memverifikasi ResolveTransferUseCase dalam menentukan tipe berkas vs direktori secara akurat melalui DetailedMetadataReader murni (bukan heuristik string '.'), mencakup berkas tanpa ekstensi (Makefile), folder bertitik (.gradle, v1.0.0), berkas normal, direktori normal, dan penyimpanan SAF / SD Card.
package com.wakwau.xplore.fileoperations.conflict

import com.wakwau.xplore.core.storage.metadata.DetailedMetadataReader
import com.wakwau.xplore.core.storage.model.FileDetailedMetadata
import com.wakwau.xplore.core.storage.model.StorageLocation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ResolveTransferUseCaseTest {

    private val existingNamesMap = mutableSetOf<String>()
    private val metadataStore = mutableMapOf<String, FileDetailedMetadata>()
    private val conflictsToReturn = mutableListOf<FileConflict>()

    private val fakeConflictDetector = object : ConflictDetector {
        override suspend fun getExistingNames(destinationDir: StorageLocation): Set<String> {
            return existingNamesMap
        }

        override suspend fun detectConflicts(
            sources: List<StorageLocation>,
            destinationDir: StorageLocation
        ): List<FileConflict> {
            return conflictsToReturn.filter { conflict -> sources.any { it.path == conflict.source.path } }
        }
    }

    private val fakeConflictResolver = DefaultConflictResolver()

    private val fakeMetadataReader = object : DetailedMetadataReader {
        override suspend fun readDetailedMetadata(location: StorageLocation): FileDetailedMetadata {
            return metadataStore[location.path] ?: throw IllegalArgumentException("No metadata for ${location.path}")
        }
    }

    private lateinit var useCase: ResolveTransferUseCase

    @Before
    fun setUp() {
        existingNamesMap.clear()
        metadataStore.clear()
        conflictsToReturn.clear()
        useCase = ResolveTransferUseCase(
            conflictDetector = fakeConflictDetector,
            conflictResolver = fakeConflictResolver,
            detailedMetadataReader = fakeMetadataReader
        )
    }

    private fun registerMetadata(
        path: String,
        name: String,
        isDirectory: Boolean,
        parentPath: String = "/parent"
    ) {
        metadataStore[path] = FileDetailedMetadata(
            fileName = name,
            fullPath = path,
            parentPath = parentPath,
            sizeBytes = if (isDirectory) 0L else 1024L,
            isDirectory = isDirectory,
            lastModifiedTimestamp = 1000L,
            isReadable = true,
            isWritable = true,
            isExecutable = isDirectory,
            isHidden = name.startsWith('.'),
            posixPermissions = if (isDirectory) "rwxr-xr-x" else "rw-r--r--",
            mimeType = if (isDirectory) "inode/directory" else "application/octet-stream"
        )
    }

    @Test
    fun resolve_fileWithoutExtension_suchAsMakefile_isResolvedAsFile() = runTest {
        // [Jalur Class/Modul]: file-operations/src/test/kotlin/com/wakwau/xplore/fileoperations/conflict/ResolveTransferUseCaseTest.kt
        // [Penjelasan]: Verifikasi berkas tanpa ekstensi seperti 'Makefile' diidentifikasi sebagai FILE (isDirectory = false) menggunakan DetailedMetadataReader, bukan direktori akibat heuristik lama.
        val source = StorageLocation("/project/Makefile", "local")
        val dest = StorageLocation("/dest", "local")
        registerMetadata(path = source.path, name = "Makefile", isDirectory = false)

        val result = useCase(
            sources = listOf(source),
            destinationDir = dest,
            conflictDecisions = emptyMap()
        )

        assertEquals(1, result.size)
        val item = result.first()
        assertEquals("Makefile", item.originalName)
        assertEquals("Makefile", item.targetName)
        assertFalse("Makefile harus diidentifikasi sebagai file, bukan direktori", item.isDirectory)
    }

    @Test
    fun resolve_hiddenFolderWithDotPrefix_suchAsDotGradle_isResolvedAsDirectory() = runTest {
        // [Jalur Class/Modul]: file-operations/src/test/kotlin/com/wakwau/xplore/fileoperations/conflict/ResolveTransferUseCaseTest.kt
        // [Penjelasan]: Verifikasi folder berawalan titik seperti '.gradle' diidentifikasi sebagai DIREKTORI (isDirectory = true), tidak keliru dianggap berkas ber-ekstensi.
        val source = StorageLocation("/project/.gradle", "local")
        val dest = StorageLocation("/dest", "local")
        registerMetadata(path = source.path, name = ".gradle", isDirectory = true)

        val result = useCase(
            sources = listOf(source),
            destinationDir = dest,
            conflictDecisions = emptyMap()
        )

        assertEquals(1, result.size)
        val item = result.first()
        assertEquals(".gradle", item.originalName)
        assertTrue(".gradle harus diidentifikasi sebagai direktori", item.isDirectory)
    }

    @Test
    fun resolve_folderWithDotInMiddle_suchAsVersionFolder_isResolvedAsDirectory() = runTest {
        // [Jalur Class/Modul]: file-operations/src/test/kotlin/com/wakwau/xplore/fileoperations/conflict/ResolveTransferUseCaseTest.kt
        // [Penjelasan]: Verifikasi folder yang memiliki titik di tengah nama seperti 'release-v1.0.0' diidentifikasi sebagai DIREKTORI (isDirectory = true).
        val source = StorageLocation("/project/release-v1.0.0", "local")
        val dest = StorageLocation("/dest", "local")
        registerMetadata(path = source.path, name = "release-v1.0.0", isDirectory = true)

        val result = useCase(
            sources = listOf(source),
            destinationDir = dest,
            conflictDecisions = emptyMap()
        )

        assertEquals(1, result.size)
        val item = result.first()
        assertEquals("release-v1.0.0", item.originalName)
        assertTrue("release-v1.0.0 harus diidentifikasi sebagai direktori", item.isDirectory)
    }

    @Test
    fun resolve_fileWithNormalExtension_suchAsReportPdf_isResolvedAsFile() = runTest {
        // [Jalur Class/Modul]: file-operations/src/test/kotlin/com/wakwau/xplore/fileoperations/conflict/ResolveTransferUseCaseTest.kt
        // [Penjelasan]: Verifikasi berkas dengan ekstensi standar seperti 'report.pdf' diidentifikasi sebagai FILE (isDirectory = false).
        val source = StorageLocation("/documents/report.pdf", "local")
        val dest = StorageLocation("/dest", "local")
        registerMetadata(path = source.path, name = "report.pdf", isDirectory = false)

        val result = useCase(
            sources = listOf(source),
            destinationDir = dest,
            conflictDecisions = emptyMap()
        )

        assertEquals(1, result.size)
        val item = result.first()
        assertEquals("report.pdf", item.originalName)
        assertFalse("report.pdf harus diidentifikasi sebagai berkas", item.isDirectory)
    }

    @Test
    fun resolve_normalDirectory_suchAsDocuments_isResolvedAsDirectory() = runTest {
        // [Jalur Class/Modul]: file-operations/src/test/kotlin/com/wakwau/xplore/fileoperations/conflict/ResolveTransferUseCaseTest.kt
        // [Penjelasan]: Verifikasi direktori normal tanpa titik seperti 'Documents' diidentifikasi sebagai DIREKTORI (isDirectory = true).
        val source = StorageLocation("/storage/Documents", "local")
        val dest = StorageLocation("/dest", "local")
        registerMetadata(path = source.path, name = "Documents", isDirectory = true)

        val result = useCase(
            sources = listOf(source),
            destinationDir = dest,
            conflictDecisions = emptyMap()
        )

        assertEquals(1, result.size)
        val item = result.first()
        assertEquals("Documents", item.originalName)
        assertTrue("Documents harus diidentifikasi sebagai direktori", item.isDirectory)
    }

    @Test
    fun resolve_safSourceOnSdCardOrOtg_usesMetadataContractCorrectly() = runTest {
        // [Jalur Class/Modul]: file-operations/src/test/kotlin/com/wakwau/xplore/fileoperations/conflict/ResolveTransferUseCaseTest.kt
        // [Penjelasan]: Verifikasi bahwa berkas dan folder pada SAF / SD Card dengan ID URI buram dan nama ber-titik diproses secara tepat berdasarkan kontrak DetailedMetadataReader.
        val safFileSource = StorageLocation("content://com.android.externalstorage.documents/document/0000-0000%3A1111", "saf_sdcard")
        val safDirSource = StorageLocation("content://com.android.externalstorage.documents/tree/0000-0000%3A/document/0000-0000%3A2222", "saf_sdcard")
        val dest = StorageLocation("/dest", "local")

        registerMetadata(path = safFileSource.path, name = "raw_binary_executable", isDirectory = false)
        registerMetadata(path = safDirSource.path, name = ".hidden_camera_cache", isDirectory = true)

        val result = useCase(
            sources = listOf(safFileSource, safDirSource),
            destinationDir = dest,
            conflictDecisions = emptyMap()
        )

        assertEquals(2, result.size)
        val fileItem = result.first { it.source == safFileSource }
        val dirItem = result.first { it.source == safDirSource }

        assertFalse("Berkas SAF tanpa ekstensi harus isDirectory = false", fileItem.isDirectory)
        assertTrue("Direktori SAF tersembunyi harus isDirectory = true", dirItem.isDirectory)
    }

    @Test
    fun resolve_whenConflictExists_usesConflictDecisionAndPreservesIsDirectory() = runTest {
        // [Jalur Class/Modul]: file-operations/src/test/kotlin/com/wakwau/xplore/fileoperations/conflict/ResolveTransferUseCaseTest.kt
        // [Penjelasan]: Verifikasi saat terjadi konflik, resolusi mengikuti keputusan user (misal RENAME) dan menjaga nilai isDirectory yang berasal dari FileConflict.
        val source = StorageLocation("/source/data.zip", "local")
        val dest = StorageLocation("/dest", "local")
        registerMetadata(path = source.path, name = "data.zip", isDirectory = false)
        existingNamesMap.add("data.zip")

        conflictsToReturn.add(
            FileConflict(
                source = source,
                sourceName = "data.zip",
                targetName = "data.zip",
                isDirectory = false,
                destinationDir = dest
            )
        )

        val result = useCase(
            sources = listOf(source),
            destinationDir = dest,
            conflictDecisions = mapOf(source to ConflictChoice.RENAME)
        )

        assertEquals(1, result.size)
        val item = result.first()
        assertEquals("data.zip", item.originalName)
        assertEquals("data (1).zip", item.targetName)
        assertFalse(item.isDirectory)
        assertEquals(ConflictChoice.RENAME, item.choice)
    }
}
