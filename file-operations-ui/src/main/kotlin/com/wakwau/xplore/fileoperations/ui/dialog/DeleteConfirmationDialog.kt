// [Jalur Class/Modul]: file-operations-ui/src/main/kotlin/com/wakwau/xplore/fileoperations/ui/dialog/DeleteConfirmationDialog.kt
// [Penjelasan]: Dialog komponen untuk konfirmasi penghapusan file atau folder tunggal maupun jamak dalam sub-paket dialog.
package com.wakwau.xplore.fileoperations.ui.dialog

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wakwau.xplore.core.utils.ui.R
import com.wakwau.xplore.core.utils.ui.components.AppDialog

@Composable
fun DeleteConfirmationDialog(
    itemCount: Int,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val title = stringResource(R.string.dialog_title_delete_confirmation)

    val message = if (itemCount > 1) {
        stringResource(R.string.msg_confirm_delete_plural, itemCount)
    } else {
        stringResource(R.string.msg_confirm_delete_single)
    }

    AppDialog(
        title = title,
        onDismissRequest = onDismissRequest,
        confirmButtonText = stringResource(R.string.btn_delete),
        onConfirm = onConfirm,
        confirmButtonColor = MaterialTheme.colorScheme.error
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
