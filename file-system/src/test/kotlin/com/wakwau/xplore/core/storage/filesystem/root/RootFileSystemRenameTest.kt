// [Jalur Class/Modul]: file-system/src/test/kotlin/com/wakwau/xplore/core/storage/filesystem/root/RootFileSystemRenameTest.kt
// [Penjelasan]: Unit test untuk verifikasi bahwa RootFileSystem menolak operasi rename pada root path dan protected path dengan SecurityException.
package com.wakwau.xplore.core.storage.filesystem.root

import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.permission.SuPermissionChecker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootFileSystemRenameTest {

    private lateinit var rootFileSystem: RootFileSystem
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        val grantedChecker = SuPermissionChecker(rootCheck = { true })
        rootFileSystem = RootFileSystem(
            suPermissionChecker = grantedChecker,
            ioDispatcher = dispatcher
        )
    }

    @Test
    fun rename_protectedSystemPath_throwsSecurityException() = runTest {
        try {
            rootFileSystem.rename(StorageLocation("/system"), "system_renamed")
            fail("Expected SecurityException when renaming protected path /system")
        } catch (e: SecurityException) {
            assertEquals("Cannot rename root or protected storage path: /system", e.message)
        }
    }

    @Test
    fun rename_protectedStorageRoot_throwsSecurityException() = runTest {
        try {
            rootFileSystem.rename(StorageLocation("/storage/emulated/0"), "storage_renamed")
            fail("Expected SecurityException when renaming protected path /storage/emulated/0")
        } catch (e: SecurityException) {
            assertEquals("Cannot rename root or protected storage path: /storage/emulated/0", e.message)
        }
    }

    @Test
    fun rename_rootPath_throwsSecurityException() = runTest {
        try {
            rootFileSystem.rename(StorageLocation("/"), "new_root")
            fail("Expected SecurityException when renaming root path /")
        } catch (e: SecurityException) {
            assertEquals("Cannot rename root or protected storage path: /", e.message)
        }
    }
}
