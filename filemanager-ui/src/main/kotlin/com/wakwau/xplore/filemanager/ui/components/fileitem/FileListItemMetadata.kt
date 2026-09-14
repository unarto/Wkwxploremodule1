// [Jalur Class/Modul]: filemanager-ui/src/main/kotlin/com/wakwau/xplore/filemanager/ui/components/fileitem/FileListItemMetadata.kt
// [Penjelasan]: Komponen composable untuk menampilkan info teks berkas (nama berkas, path induk hasil pencarian, tanggal modifikasi, dan ukuran berkas).
package com.wakwau.xplore.filemanager.ui.components.fileitem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakwau.xplore.core.storage.constant.StorageConstants
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.utils.formatter.DateFormatter
import com.wakwau.xplore.core.utils.ui.theme.XPloreTheme
import com.wakwau.xplore.core.utils.formatter.ByteFormatter
import com.wakwau.xplore.filemanager.ui.R

@Composable
fun FileListItemMetadata(
    item: FileItem,
    isDir: Boolean,
    isArchive: Boolean,
    isInternalStorage: Boolean,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = XPloreTheme.colors
    val isSearchRoot = item.id == StorageConstants.VIRTUAL_SEARCH_ROOT_ID
    val isSearchResult = item.id.startsWith(StorageConstants.SEARCH_RESULT_ID_PREFIX)

    Column(modifier = modifier) {
        val displayName = if (isSearchRoot) {
            if (item.name.contains("(") && item.name.contains(")")) {
                val countStr = item.name.substringAfter("(").substringBefore(")")
                val count = countStr.toIntOrNull() ?: 0
                stringResource(R.string.label_search_results_count, count)
            } else if (item.name.startsWith(StorageConstants.SEARCH_RESULTS_PREFIX)) {
                val keyword = item.name.removePrefix(StorageConstants.SEARCH_RESULTS_PREFIX).removeSurrounding("'")
                stringResource(R.string.label_search_results_query, keyword)
            } else {
                stringResource(R.string.label_search_results)
            }
        } else {
            item.name
        }

        Text(
            text = displayName,
            color = if (isInternalStorage || isDir || isArchive) colors.treeExpandArrow else colors.textPrimary,
            fontWeight = if (isFocused || isDir || isArchive) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (isSearchResult) {
            val parentDir = item.location.path.substringBeforeLast('/', "")
            val displayParent = if (parentDir.isNotEmpty()) "$parentDir/" else "/"
            Text(
                text = displayParent,
                color = colors.textSecondary,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        if (!isSearchRoot) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = DateFormatter.formatShort(item.metadata.modifiedTime),
                    color = colors.textSecondary,
                    fontSize = 10.5.sp
                )
                if (!isDir) {
                    Text(
                        text = ByteFormatter.format(item.metadata.size),
                        color = colors.textSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
