// [Jalur Class/Modul]: core-utils/src/test/java/com/wakwau/xplore/core/utils/formatter/ByteFormatterTest.kt
// [Penjelasan]: Unit test untuk memvalidasi pemformatan ukuran byte ke satuan B, KB, MB, GB, dan format detail.
package com.wakwau.xplore.core.utils.formatter

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteFormatterTest {

    @Test
    fun format_negativeBytes_returnsZeroB() {
        assertEquals("0 B", ByteFormatter.format(-1L))
    }

    @Test
    fun format_smallBytes_returnsB() {
        assertEquals("500 B", ByteFormatter.format(500L))
    }

    @Test
    fun format_kilobytes_returnsKB() {
        assertEquals("1 KB", ByteFormatter.format(1024L))
        assertEquals("1.5 KB", ByteFormatter.format(1536L))
    }

    @Test
    fun format_megabytes_returnsMB() {
        assertEquals("1 MB", ByteFormatter.format(1024L * 1024L))
    }

    @Test
    fun format_detailed_returnsHumanAndExactBytes() {
        val result = ByteFormatter.formatDetailed(1234567L, "bytes")
        assertEquals("1.2 MB (1'234'567 bytes)", result)
    }
}
