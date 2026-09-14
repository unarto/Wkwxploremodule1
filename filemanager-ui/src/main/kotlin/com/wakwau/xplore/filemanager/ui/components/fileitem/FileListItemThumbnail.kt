// [Jalur Class/Modul]: filemanager-ui/src/main/kotlin/com/wakwau/xplore/filemanager/ui/components/fileitem/FileListItemThumbnail.kt
// [Penjelasan]: Komponen render visual thumbnail dan ikon berkas, mendukung ikon search root khusus dan thumbnail kategori format berkas.
package com.wakwau.xplore.filemanager.ui.components.fileitem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wakwau.xplore.core.storage.constant.StorageConstants
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.utils.ui.components.FileIcon
import com.wakwau.xplore.core.utils.mime.FileCategory
import com.wakwau.xplore.core.utils.ui.theme.XPloreTheme

@Composable
fun FileListItemThumbnail(
    item: FileItem,
    category: FileCategory,
    isDir: Boolean,
    isInternalStorage: Boolean,
    extension: String,
    onIconClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val colors = XPloreTheme.colors
    val isSearchRoot = item.id == StorageConstants.VIRTUAL_SEARCH_ROOT_ID

    if (isSearchRoot) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = colors.primary,
            modifier = modifier
                .size(size)
                .clickable { onIconClick() }
        )
    } else {
        FileIcon(
            category = category,
            isDirectory = isDir,
            isInternalStorage = isInternalStorage,
            extension = extension,
            size = size,
            modifier = modifier.clickable { onIconClick() }
        )
    }
}
