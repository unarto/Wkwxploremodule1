// [Jalur Class/Modul]: core-storage/src/test/kotlin/com/wakwau/xplore/core/storage/db/dao/FileIndexDaoEscapeTest.kt
// [Penjelasan]: Unit test untuk memverifikasi fungsi escapeSqlLikeWildcards dalam menangani karakter wildcard SQL LIKE ('\', '%', '_').
package com.wakwau.xplore.core.storage.db.dao

import org.junit.Assert.assertEquals
import org.junit.Test

class FileIndexDaoEscapeTest {

    @Test
    fun escapeSqlLikeWildcards_emptyString_returnsEmptyString() {
        assertEquals("", escapeSqlLikeWildcards(""))
    }

    @Test
    fun escapeSqlLikeWildcards_plainPath_returnsUnchanged() {
        val path = "/storage/emulated/0/Download/file.txt"
        assertEquals(path, escapeSqlLikeWildcards(path))
    }

    @Test
    fun escapeSqlLikeWildcards_percentSign_escapedProperly() {
        val input = "/storage/emulated/0/100%discount/file.txt"
        val expected = """/storage/emulated/0/100\%discount/file.txt"""
        assertEquals(expected, escapeSqlLikeWildcards(input))
    }

    @Test
    fun escapeSqlLikeWildcards_underscore_escapedProperly() {
        val input = "my_folder_name"
        val expected = """my\_folder\_name"""
        assertEquals(expected, escapeSqlLikeWildcards(input))
    }

    @Test
    fun escapeSqlLikeWildcards_backslash_escapedProperly() {
        val input = """path\with\backslash"""
        val expected = """path\\with\\backslash"""
        assertEquals(expected, escapeSqlLikeWildcards(input))
    }
}
