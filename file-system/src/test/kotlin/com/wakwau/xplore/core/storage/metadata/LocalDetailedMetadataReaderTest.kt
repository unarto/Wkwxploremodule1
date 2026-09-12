// [Jalur Class/Modul]: file-system/src/test/kotlin/com/wakwau/xplore/core/storage/metadata/LocalDetailedMetadataReaderTest.kt
// [Penjelasan]: Unit test untuk LocalDetailedMetadataReader memastikan integrasi delegasi ekstraksi atribut java.io.File ke FileMetadataReader.
package com.wakwau.xplore.core.storage.metadata

import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.permission.FilePermissionFormatter
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LocalDetailedMetadataReaderTest {

    private lateinit var fileMetadataReader: FileMetadataReader
    private lateinit var detailedMetadataReader: LocalDetailedMetadataReader
    private lateinit var tempDir: File

    @Before
    fun setup() {
        fileMetadataReader = FileMetadataReader()
        detailedMetadataReader = LocalDetailedMetadataReader(
            permissionFormatter = FilePermissionFormatter(),
            fileMetadataReader = fileMetadataReader,
            context = null
        )
        tempDir = Files.createTempDirectory("detailed_metadata_test").toFile()
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun readDetailedMetadata_forLocalFile_returnsAttributesFromMetadataReader() = runTest {
        val file = File(tempDir, "sample.txt")
        file.writeText("hello world")

        val location = StorageLocation(path = file.absolutePath)
        val detailed = detailedMetadataReader.readDetailedMetadata(location)
        val basic = fileMetadataReader.readMetadata(file)

        assertEquals("sample.txt", detailed.fileName)
        assertEquals(file.absolutePath, detailed.fullPath)
        assertEquals(basic.size, detailed.sizeBytes)
        assertEquals(basic.modifiedTime, detailed.lastModifiedTimestamp)
        assertEquals(basic.isReadable, detailed.isReadable)
        assertEquals(basic.isWritable, detailed.isWritable)
        assertEquals(basic.isExecutable, detailed.isExecutable)
        assertEquals(basic.isHidden, detailed.isHidden)
        assertFalse(detailed.isDirectory)
    }

    @Test
    fun readDetailedMetadata_forLocalDirectory_returnsDirectoryProperties() = runTest {
        val dir = File(tempDir, "sample_dir")
        dir.mkdir()

        val location = StorageLocation(path = dir.absolutePath)
        val detailed = detailedMetadataReader.readDetailedMetadata(location)
        val basic = fileMetadataReader.readMetadata(dir)

        assertEquals("sample_dir", detailed.fileName)
        assertEquals(dir.absolutePath, detailed.fullPath)
        assertEquals(basic.size, detailed.sizeBytes)
        assertEquals(0L, detailed.sizeBytes)
        assertTrue(detailed.isDirectory)
    }
}
