// [Jalur Class/Modul]: filemanager-ui/src/main/kotlin/com/wakwau/xplore/filemanager/ui/components/fileitem/FileListItemActionMenu.kt
// [Penjelasan]: Komponen tombol centang seleksi multiselect berkas (mendukung 3-state untuk folder dan 2-state untuk berkas) dan penanda aksi baris.
package com.wakwau.xplore.filemanager.ui.components.fileitem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wakwau.xplore.core.utils.ui.theme.XPloreTheme
import com.wakwau.xplore.filemanager.ui.R
import com.wakwau.xplore.filemanager.ui.list.FileListItemDefaults
import com.wakwau.xplore.filemanager.ui.selection.FolderCheckCycleState

@Composable
fun FileListItemActionMenu(
    selectionState: FolderCheckCycleState,
    onCheckToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = XPloreTheme.colors

    Box(
        modifier = modifier
            .size(FileListItemDefaults.CheckBoxSize)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onCheckToggle),
        contentAlignment = Alignment.Center
    ) {
        when (selectionState) {
            FolderCheckCycleState.CHECKED -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.cd_selected),
                    tint = colors.checkmarkColor,
                    modifier = Modifier.size(FileListItemDefaults.SingleCheckIconSize)
                )
            }
            FolderCheckCycleState.PARTIAL -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.cd_partially_selected),
                    tint = colors.checkmarkColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(FileListItemDefaults.SingleCheckIconSize)
                )
            }
            FolderCheckCycleState.UNCHECKED -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.cd_unselected),
                    tint = colors.checkMarkUnchecked,
                    modifier = Modifier.size(FileListItemDefaults.SingleCheckIconSize)
                )
            }
        }
    }
}
