// [Jalur Class/Modul]: search-ui/src/main/kotlin/com/wakwau/xplore/search/ui/components/searchdialog/FileSearchResultItem.kt
// [Penjelasan]: Komponen render baris individual untuk berkas hasil pencarian dengan ikon, nama berkas, path induk, tanggal modifikasi, dan ukuran.
package com.wakwau.xplore.search.ui.components.searchdialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.FileType
import com.wakwau.xplore.core.utils.ui.components.FileIcon
import com.wakwau.xplore.core.utils.formatter.ByteFormatter
import com.wakwau.xplore.core.utils.formatter.DateFormatter
import com.wakwau.xplore.core.utils.mime.MimeTypeDetector
import com.wakwau.xplore.core.utils.ui.theme.XPloreTheme

@Composable
fun FileSearchResultItem(
    item: FileItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = XPloreTheme.colors
    val isDir = item.type == FileType.DIRECTORY
    val ext = item.name.substringAfterLast('.', "")
    val category = MimeTypeDetector.getCategory(item.name, isDir)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("search_result_item_${item.name}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileIcon(
            category = category,
            isDirectory = isDir,
            extension = ext,
            size = 22.dp
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = colors.textPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val parentDir = item.location.path.substringBeforeLast('/', "")
            val displayParent = if (parentDir.isNotEmpty()) "$parentDir/" else "/"
            Text(
                text = displayParent,
                color = colors.textSecondary,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = DateFormatter.formatShort(item.metadata.modifiedTime),
                    color = colors.textSecondary,
                    fontSize = 10.sp
                )
                if (!isDir) {
                    Text(
                        text = ByteFormatter.format(item.metadata.size),
                        color = colors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
