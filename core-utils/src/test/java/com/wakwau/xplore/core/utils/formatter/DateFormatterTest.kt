// [Jalur Class/Modul]: core-utils/src/test/java/com/wakwau/xplore/core/utils/formatter/DateFormatterTest.kt
// [Penjelasan]: Unit test untuk memverifikasi performa dan keakuratan pemformatan tanggal pada DateFormatter.
package com.wakwau.xplore.core.utils.formatter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DateFormatterTest {

    @Test
    fun format_zeroOrNegative_returnsDash() {
        assertEquals("-", DateFormatter.format(0L))
        assertEquals("-", DateFormatter.format(-100L))
        assertEquals("-", DateFormatter.formatShort(0L))
        assertEquals("-", DateFormatter.formatTime(0L))
    }

    @Test
    fun format_validTimestamp_returnsFormattedString() {
        val timestamp = 1700000000000L
        val formatted = DateFormatter.format(timestamp)
        assertNotEquals("-", formatted)
        val shortFormatted = DateFormatter.formatShort(timestamp)
        assertNotEquals("-", shortFormatted)
        val timeFormatted = DateFormatter.formatTime(timestamp)
        assertNotEquals("-", timeFormatted)
    }
}
