// [Jalur Class/Modul]: filemanager-ui/src/main/kotlin/com/wakwau/xplore/filemanager/ui/list/FileListItem.kt
// [Penjelasan]: Komponen perender baris berkas individual yang bersih dengan mengomposisikan FileListItemThumbnail, FileListItemMetadata, dan FileListItemActionMenu tanpa God Class.
package com.wakwau.xplore.filemanager.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.FileType
import com.wakwau.xplore.core.utils.mime.FileCategory
import com.wakwau.xplore.core.utils.mime.MimeTypeDetector
import com.wakwau.xplore.core.utils.ui.theme.XPloreTheme
import com.wakwau.xplore.filemanager.ui.components.fileitem.FileListItemActionMenu
import com.wakwau.xplore.filemanager.ui.components.fileitem.FileListItemMetadata
import com.wakwau.xplore.filemanager.ui.components.fileitem.FileListItemThumbnail
import com.wakwau.xplore.filemanager.ui.icon.StorageIconMapper
import com.wakwau.xplore.filemanager.ui.selection.FolderCheckCycleState
import com.wakwau.xplore.treeview.component.treeScopeBorder
import com.wakwau.xplore.treeview.model.BorderPosition

object FileListItemDefaults {
    val RowCornerRadius: Dp = 3.dp
    val ArrowBoxSize: Dp = 18.dp
    val ArrowIconSize: Dp = 11.dp
    val FileIconSize: Dp = 24.dp
    val CheckBoxSize: Dp = 28.dp
    val AllSelectedBoxSize: Dp = 19.dp
    val AllSelectedIconSize: Dp = 14.dp
    val SingleCheckIconSize: Dp = 18.dp
    val NameFontSize = 13.sp
    val MetadataFontSize = 10.5.sp
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    item: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCheckToggle: () -> Unit,
    onIconClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    borderPosition: BorderPosition = BorderPosition.NONE,
    isPathSelected: Boolean = borderPosition != BorderPosition.NONE,
    showExpandArrow: Boolean = true,
    selectionState: FolderCheckCycleState = if (isSelected) FolderCheckCycleState.CHECKED else FolderCheckCycleState.UNCHECKED
) {
    val colors = XPloreTheme.colors
    val isDir = item.type == FileType.DIRECTORY
    val ext = item.name.substringAfterLast('.', "")
    val category = MimeTypeDetector.getCategory(item.name, isDir)
    val isArchive = category == FileCategory.ARCHIVE
    val hasExpandArrow = showExpandArrow && (isDir || isArchive)
    val isFocused = borderPosition != BorderPosition.NONE || isPathSelected

    val effectiveBorderPosition = when {
        borderPosition != BorderPosition.NONE -> borderPosition
        isPathSelected -> BorderPosition.SINGLE
        else -> BorderPosition.NONE
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FileListItemDefaults.RowCornerRadius))
            .background(Color.Transparent)
            .treeScopeBorder(
                position = effectiveBorderPosition,
                borderColor = colors.folderSelectionColor,
                strokeWidth = 1.dp,
                cornerRadius = FileListItemDefaults.RowCornerRadius
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Expand arrow or spacing / dot indicator
        if (hasExpandArrow) {
            Box(
                modifier = Modifier
                    .size(FileListItemDefaults.ArrowBoxSize)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = colors.treeExpandArrow,
                    modifier = Modifier.size(FileListItemDefaults.ArrowIconSize)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(FileListItemDefaults.ArrowBoxSize)
                    .clickable(onClick = onIconClick),
                contentAlignment = Alignment.Center
            ) {
                if (isFocused) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .border(1.5.dp, colors.treeExpandArrow, CircleShape)
                            .padding(2.dp)
                            .background(colors.primary, CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(colors.textSecondary.copy(alpha = 0.6f), CircleShape)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(3.dp))

        val isInternalStorage = StorageIconMapper.isInternalStorage(item)

        // Thumbnail / File Icon
        FileListItemThumbnail(
            item = item,
            category = category,
            isDir = isDir,
            isInternalStorage = isInternalStorage,
            extension = ext,
            onIconClick = onIconClick,
            size = FileListItemDefaults.FileIconSize
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Name and Metadata (Date + Size)
        FileListItemMetadata(
            item = item,
            isDir = isDir,
            isArchive = isArchive,
            isInternalStorage = isInternalStorage,
            isFocused = isFocused,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Selection Checkmark button (3-State for folder, 2-State for file)
        FileListItemActionMenu(
            selectionState = selectionState,
            onCheckToggle = onCheckToggle
        )
    }
}
