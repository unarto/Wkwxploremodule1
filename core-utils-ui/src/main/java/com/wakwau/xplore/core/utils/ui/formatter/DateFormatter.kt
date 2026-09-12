// [Jalur Class/Modul]: core-utils-ui/src/main/java/com/wakwau/xplore/core/utils/ui/formatter/DateFormatter.kt
// [Penjelasan]: Formatter presentasi tanggal/waktu untuk layer UI di bawah modul :core-utils-ui yang mendelegasikan pemformatan ke :core-utils tanpa kebocoran langsung dependensi foundation ke feature UI.
package com.wakwau.xplore.core.utils.ui.formatter

import com.wakwau.xplore.core.utils.formatter.DateFormatter as CoreDateFormatter

object DateFormatter {
    fun format(timestamp: Long): String = CoreDateFormatter.format(timestamp)
    fun formatShort(timestamp: Long): String = CoreDateFormatter.formatShort(timestamp)
}
