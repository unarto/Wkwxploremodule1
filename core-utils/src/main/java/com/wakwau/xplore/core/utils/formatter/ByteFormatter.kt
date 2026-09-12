// [Jalur Class/Modul]: core-utils/src/main/java/com/wakwau/xplore/core/utils/formatter/ByteFormatter.kt
// [Penjelasan]: Utilitas pemformat byte untuk konversi ukuran berkas ke satuan terstruktur dan format detail dengan tanda petik pemisah ribuan.
package com.wakwau.xplore.core.utils.formatter

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object ByteFormatter {
    private const val KB = 1024L
    private const val MB = 1024L * 1024L
    private const val GB = 1024L * 1024L * 1024L
    private const val TB = 1024L * 1024L * 1024L * 1024L

    private val decimalFormat = DecimalFormat("#,##0.#", DecimalFormatSymbols(Locale.US))
    private val detailedSymbols = DecimalFormatSymbols(Locale.US).apply { groupingSeparator = '\'' }
    private val detailedFormat = DecimalFormat("#,##0", detailedSymbols)

    fun format(bytes: Long): String {
        if (bytes < 0) return "0 B"
        if (bytes < KB) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return "${decimalFormat.format(kb)} KB"
        val mb = kb / 1024.0
        if (mb < 1024.0) return "${decimalFormat.format(mb)} MB"
        val gb = mb / 1024.0
        if (gb < 1024.0) return "${decimalFormat.format(gb)} GB"
        val tb = gb / 1024.0
        return "${decimalFormat.format(tb)} TB"
    }

    fun formatBytesShort(bytes: Long): String {
        if (bytes < KB) return "${bytes}B"
        if (bytes < MB) return "${bytes / KB}KB"
        if (bytes < GB) return "${bytes / MB}MB"
        return "${bytes / GB}GB"
    }

    fun formatDetailed(bytes: Long, byteLabel: String): String {
        if (bytes < 0) return "0 $byteLabel"
        val human = format(bytes)
        return "$human (${detailedFormat.format(bytes)} $byteLabel)"
    }
}
