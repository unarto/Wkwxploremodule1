// [Jalur Class/Modul]: core-utils/src/main/java/com/wakwau/xplore/core/utils/formatter/DateFormatter.kt
// [Penjelasan]: Utilitas pemformat tanggal dan waktu untuk konversi timestamp ke format tampilan pengguna dengan caching ThreadLocal untuk efisiensi alokasi memori dan thread-safety.
package com.wakwau.xplore.core.utils.formatter

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFormatter {
    private val fullDateTimeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("d MMM yyyy HH.mm.ss", Locale.getDefault())
    }
    private val shortDateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    }
    private val timeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    }

    fun format(timestamp: Long): String {
        if (timestamp <= 0L) return "-"
        return fullDateTimeFormat.get()?.format(Date(timestamp)) ?: "-"
    }

    fun formatShort(timestamp: Long): String {
        if (timestamp <= 0L) return "-"
        return shortDateFormat.get()?.format(Date(timestamp)) ?: "-"
    }

    fun formatTime(timestamp: Long): String {
        if (timestamp <= 0L) return "-"
        return timeFormat.get()?.format(Date(timestamp)) ?: "-"
    }
}
