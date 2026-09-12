// [Jalur Class/Modul]: core-utils-ui/src/main/java/com/wakwau/xplore/core/utils/ui/formatter/ByteFormatter.kt
// [Penjelasan]: Formatter presentasi ukuran byte untuk layer UI di bawah modul :core-utils-ui yang mendelegasikan ke :core-utils tanpa mengekspos kebocoran dependensi langsung foundation ke feature UI.
package com.wakwau.xplore.core.utils.ui.formatter

import com.wakwau.xplore.core.utils.formatter.ByteFormatter as CoreByteFormatter

object ByteFormatter {
    fun format(bytes: Long): String = CoreByteFormatter.format(bytes)
    fun formatBytesShort(bytes: Long): String = CoreByteFormatter.formatBytesShort(bytes)
    fun formatDetailed(bytes: Long, byteLabel: String): String = CoreByteFormatter.formatDetailed(bytes, byteLabel)
}
