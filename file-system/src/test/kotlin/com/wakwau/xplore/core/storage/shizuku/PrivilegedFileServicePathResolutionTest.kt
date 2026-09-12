// [Jalur Class/Modul]: file-system/src/test/kotlin/com/wakwau/xplore/core/storage/shizuku/PrivilegedFileServicePathResolutionTest.kt
// [Penjelasan]: Unit test untuk verifikasi resolusi path fisik Shizuku pada PrivilegedFileService (heuristic match, fallback saat tidak ada/tidak dapat diakses, dan un-matched path).
package com.wakwau.xplore.core.storage.shizuku

import org.junit.Assert.assertEquals
import org.junit.Test

class PrivilegedFileServicePathResolutionTest {

    @Test
    fun resolvePhysicalPath_forUnmatchedPath_remainsUnchanged() {
        val path = "/system/etc/hosts"
        val resolved = PrivilegedFileService.resolvePath(path)
        assertEquals(path, resolved)

        val rootPath = "/"
        assertEquals(rootPath, PrivilegedFileService.resolvePath(rootPath))

        val customPath = "/data/local/tmp/test"
        assertEquals(customPath, PrivilegedFileService.resolvePath(customPath))
    }

    @Test
    fun resolvePhysicalPath_whenTransformedPathIsAccessible_returnsTransformedPath() {
        val inputPath = "/storage/emulated/0/Download"
        val expectedTransformed = "/data/media/0/Download"

        val resolved = PrivilegedFileService.resolvePath(
            path = inputPath,
            isPathAccessible = { target -> target == expectedTransformed },
            isParentAccessible = { false }
        )

        assertEquals(expectedTransformed, resolved)
    }

    @Test
    fun resolvePhysicalPath_whenTransformedPathNotAccessible_andOriginalAccessible_fallbacksToOriginal() {
        val inputPath = "/storage/emulated/0/Download"

        val resolved = PrivilegedFileService.resolvePath(
            path = inputPath,
            isPathAccessible = { target -> target == inputPath }, // original accessible, transformed not accessible
            isParentAccessible = { false }
        )

        assertEquals(inputPath, resolved)
    }

    @Test
    fun resolvePhysicalPath_whenTransformedPathDoesNotExist_andParentDoesNotExist_fallbacksToOriginal() {
        val nonExistentPath = "/storage/emulated/999/non_existent_folder_xyz"

        val resolved = PrivilegedFileService.resolvePath(
            path = nonExistentPath,
            isPathAccessible = { false },
            isParentAccessible = { false }
        )

        assertEquals(nonExistentPath, resolved)
    }

    @Test
    fun resolvePhysicalPath_whenSdcardTransformedPathNotAccessible_fallbacksToOriginal() {
        val nonExistentSdcardPath = "/sdcard/non_existent_folder_abc"

        val resolved = PrivilegedFileService.resolvePath(
            path = nonExistentSdcardPath,
            isPathAccessible = { false },
            isParentAccessible = { false }
        )

        assertEquals(nonExistentSdcardPath, resolved)
    }
}

