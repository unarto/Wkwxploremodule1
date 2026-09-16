// [Jalur Class/Modul]: file-system/src/test/kotlin/com/wakwau/xplore/core/storage/filesystem/ProtectedPathPolicyTest.kt
// [Penjelasan]: Unit test untuk validasi ProtectedPathPolicy terhadap berbagai direktori sistem, root path, dan jalur penyimpanan normal.
package com.wakwau.xplore.core.storage.filesystem

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectedPathPolicyTest {

    @Test
    fun isProtectedPath_rootPaths_returnsTrue() {
        assertTrue(ProtectedPathPolicy.isProtectedPath("/"))
        assertTrue(ProtectedPathPolicy.isProtectedPath(""))
        assertTrue(ProtectedPathPolicy.isProtectedPath("   "))
    }

    @Test
    fun isProtectedPath_systemAndStorageRoots_returnsTrue() {
        assertTrue(ProtectedPathPolicy.isProtectedPath("/storage"))
        assertTrue(ProtectedPathPolicy.isProtectedPath("/storage/"))
        assertTrue(ProtectedPathPolicy.isProtectedPath("/storage/emulated"))
        assertTrue(ProtectedPathPolicy.isProtectedPath("/storage/emulated/0"))
        assertTrue(ProtectedPathPolicy.isProtectedPath("/storage/emulated/0/"))
        assertTrue(ProtectedPathPolicy.isProtectedPath("/system"))
        assertTrue(ProtectedPathPolicy.isProtectedPath("/system/"))
        assertTrue(ProtectedPathPolicy.isProtectedPath("/vendor"))
        assertTrue(ProtectedPathPolicy.isProtectedPath("/apex"))
        assertTrue(ProtectedPathPolicy.isProtectedPath("/proc"))
        assertTrue(ProtectedPathPolicy.isProtectedPath("/sys"))
        assertTrue(ProtectedPathPolicy.isProtectedPath("/dev"))
        assertTrue(ProtectedPathPolicy.isProtectedPath("/etc"))
        assertTrue(ProtectedPathPolicy.isProtectedPath("/bin"))
        assertTrue(ProtectedPathPolicy.isProtectedPath("/sbin"))
    }

    @Test
    fun isProtectedPath_caseInsensitive_returnsTrue() {
        assertTrue(ProtectedPathPolicy.isProtectedPath("/SYSTEM"))
        assertTrue(ProtectedPathPolicy.isProtectedPath("/Storage"))
        assertTrue(ProtectedPathPolicy.isProtectedPath("/VENDOR"))
        assertTrue(ProtectedPathPolicy.isProtectedPath("/Storage/Emulated/0"))
    }

    @Test
    fun isProtectedPath_userDirectoriesAndFiles_returnsFalse() {
        assertFalse(ProtectedPathPolicy.isProtectedPath("/storage/emulated/0/Download"))
        assertFalse(ProtectedPathPolicy.isProtectedPath("/storage/emulated/0/Documents"))
        assertFalse(ProtectedPathPolicy.isProtectedPath("/storage/emulated/0/test.txt"))
        assertFalse(ProtectedPathPolicy.isProtectedPath("/sdcard/Download"))
        assertFalse(ProtectedPathPolicy.isProtectedPath("/data/local/tmp"))
    }
}
