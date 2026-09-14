// [Jalur Class/Modul]: file-operations-ui/src/main/kotlin/com/wakwau/xplore/fileoperations/ui/dialog/ConflictResolutionDialog.kt
// [Penjelasan]: Dialog UI resolusi konflik nama berkas/folder ganda dengan pilihan SKIP, OVERWRITE, RENAME, serta opsi Terapkan ke Semua dipindahkan ke modul file-operations-ui sub-paket dialog.
package com.wakwau.xplore.fileoperations.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wakwau.xplore.fileoperations.conflict.ConflictChoice
import com.wakwau.xplore.fileoperations.conflict.FileConflict
import com.wakwau.xplore.fileoperations.ui.R
import com.wakwau.xplore.core.utils.ui.components.AppDialog

@Composable
fun ConflictResolutionDialog(
    conflict: FileConflict?,
    currentIndex: Int = 0,
    totalConflicts: Int = 1,
    onDecision: (ConflictChoice, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    if (conflict == null) return
    var applyToAll by remember { mutableStateOf(false) }

    val dialogTitle = if (conflict.isDirectory) {
        stringResource(R.string.title_conflict_folder)
    } else {
        stringResource(R.string.title_conflict_file)
    }

    AppDialog(
        title = dialogTitle,
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Text(
                text = stringResource(R.string.msg_conflict_target_exists, conflict.targetName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.msg_conflict_item_count,
                    currentIndex + 1,
                    totalConflicts
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = applyToAll,
                    onCheckedChange = { applyToAll = it }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.label_conflict_apply_to_all),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onDecision(ConflictChoice.SKIP, applyToAll) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_conflict_skip))
                }
                Spacer(modifier = Modifier.width(4.dp))
                OutlinedButton(
                    onClick = { onDecision(ConflictChoice.OVERWRITE, applyToAll) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_conflict_overwrite))
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = { onDecision(ConflictChoice.RENAME, applyToAll) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_conflict_rename))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.btn_conflict_cancel))
            }
        }
    }
}
