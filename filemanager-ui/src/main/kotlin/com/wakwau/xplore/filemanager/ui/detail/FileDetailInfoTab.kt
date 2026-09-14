// [Jalur Class/Modul]: filemanager-ui/src/main/kotlin/com/wakwau/xplore/filemanager/ui/detail/FileDetailInfoTab.kt
// [Penjelasan]: Tab informasi berkas yang menampilkan metadata mendalam (jalur, nama, mime type, ukuran byte detail, tanggal ubah, dan pemicu buka berkas melalui system chooser).
package com.wakwau.xplore.filemanager.ui.detail

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.wakwau.xplore.core.storage.model.FileDetailedMetadata
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.FileType
import com.wakwau.xplore.core.utils.ui.components.FileIcon
import com.wakwau.xplore.core.utils.ui.theme.XPloreTheme
import com.wakwau.xplore.core.utils.formatter.ByteFormatter
import com.wakwau.xplore.core.utils.formatter.DateFormatter
import com.wakwau.xplore.core.utils.mime.MimeTypeDetector
import com.wakwau.xplore.filemanager.ui.R

@Composable
fun FileDetailInfoTab(
    item: FileItem,
    metadata: FileDetailedMetadata?,
    modifier: Modifier = Modifier,
    onRenameClick: (FileItem) -> Unit = {},
    onRemoveLinkClick: ((FileItem) -> Unit)? = null
) {
    val context = LocalContext.current
    val colors = XPloreTheme.colors
    val clipboardManager = LocalClipboardManager.current

    val isDir = item.type == FileType.DIRECTORY
    val isSafRoot = isDir && item.location.path.startsWith("content://") && item.location.path == item.id

    val filePath = metadata?.fullPath ?: item.location.path
    val parentPath = metadata?.parentPath ?: if (filePath.contains('/')) filePath.substringBeforeLast('/', "") else ""
    val fileName = metadata?.fileName ?: item.name
    val mimeType = metadata?.mimeType ?: if (isDir) stringResource(R.string.label_directory_folder) else MimeTypeDetector.getMimeType(fileName)
    val sizeBytes = metadata?.sizeBytes ?: item.metadata.size
    val modifiedTime = metadata?.lastModifiedTimestamp ?: item.metadata.modifiedTime

    val copyToClipboard: (String, String) -> Unit = { text, label ->
        clipboardManager.setText(AnnotatedString(text))
        Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard, label), Toast.LENGTH_SHORT).show()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 1. Jalur
        FileDetailRow(
            label = stringResource(R.string.label_path_colon),
            value = parentPath,
            actionIcon = Icons.Default.ContentCopy,
            actionContentDescription = stringResource(R.string.cd_copy_value, stringResource(R.string.label_path_colon)),
            onActionClick = { copyToClipboard(parentPath, context.getString(R.string.label_path_colon)) }
        )

        // 2. Nama
        FileDetailRow(
            label = stringResource(R.string.label_name_colon),
            value = fileName,
            actionIcon = Icons.Default.Edit,
            actionContentDescription = stringResource(R.string.cd_rename),
            onActionClick = { onRenameClick(item) }
        )

        // 3. Mime type
        FileDetailRow(
            label = stringResource(R.string.label_mime_type_colon),
            value = mimeType,
            actionIcon = Icons.Default.Edit,
            actionContentDescription = stringResource(R.string.cd_edit_value, stringResource(R.string.label_mime_type_colon)),
            onActionClick = { copyToClipboard(mimeType, context.getString(R.string.label_mime_type_colon)) }
        )

        // 4. Ukuran
        val sizeFormatted = if (isDir) {
            ByteFormatter.format(sizeBytes)
        } else {
            ByteFormatter.formatDetailed(sizeBytes, stringResource(R.string.label_byte_unit))
        }
        FileDetailRow(
            label = stringResource(R.string.label_size_colon),
            value = sizeFormatted
        )

        // 5. Ubah tanggal
        FileDetailRow(
            label = stringResource(R.string.label_modify_date_colon),
            value = DateFormatter.format(modifiedTime),
            actionIcon = Icons.Default.Edit,
            actionContentDescription = stringResource(R.string.cd_edit_value, stringResource(R.string.label_modify_date_colon)),
            onActionClick = { copyToClipboard(DateFormatter.format(modifiedTime), context.getString(R.string.label_modify_date_colon)) }
        )

        // Hapus Tautan (SAF Root)
        if (isSafRoot && onRemoveLinkClick != null) {
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = { onRemoveLinkClick(item) },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.error
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.cd_remove_link))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 6. Dibuka dengan (Non-direktori) - menggunakan Android system chooser
        if (!isDir) {
            FileDetailRow(
                label = stringResource(R.string.label_open_with_colon),
                value = stringResource(R.string.label_default_app),
                actionIcon = Icons.Default.Edit,
                actionContentDescription = stringResource(R.string.label_open_with_colon),
                onActionClick = {
                    AppIntentResolver.openWith(
                        context = context,
                        filePath = filePath,
                        mimeType = mimeType
                    )
                },
                leadingContent = {
                    val category = MimeTypeDetector.getCategory(fileName, false)
                    FileIcon(category = category, isDirectory = false, size = 18.dp)
                }
            )
        }
    }
}
