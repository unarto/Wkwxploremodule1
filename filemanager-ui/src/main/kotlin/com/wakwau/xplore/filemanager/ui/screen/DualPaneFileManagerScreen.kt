// [Jalur Class/Modul]: filemanager-ui/src/main/kotlin/com/wakwau/xplore/filemanager/ui/screen/DualPaneFileManagerScreen.kt
// [Penjelasan]: Menggunakan TreeNavigationAdapter untuk menginisialisasi storage volume roots dan menyinkronkan lokasi awal panel ke ViewModel.
package com.wakwau.xplore.filemanager.ui.screen

import android.net.Uri

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.wakwau.xplore.filemanager.ui.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.model.StorageVolumeItem
import com.wakwau.xplore.filemanager.event.DualPaneEvent
import com.wakwau.xplore.filemanager.ui.presentation.DualPaneViewModel
import com.wakwau.xplore.filemanager.state.PanelId
import com.wakwau.xplore.filemanager.ui.tree.TreeNavigationAdapter

@Composable
fun DualPaneFileManagerScreen(
    viewModel: DualPaneViewModel,
    treeAdapter: TreeNavigationAdapter,
    storageVolumes: List<StorageVolumeItem>,
    onSettingsClick: () -> Unit = {},
    
    onRemoveLinkClick: (Uri) -> Unit = {},
    searchDialog: @Composable () -> Unit = {},
    operationDialogs: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    
    var showLinkDialog by remember { mutableStateOf(false) }
    val linkStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            viewModel.addLinkedStorage(it)
        }
    }

    if (showLinkDialog) {
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text(stringResource(R.string.title_link_storage)) },
            text = { Text(stringResource(R.string.msg_link_storage_explanation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLinkDialog = false
                        linkStorageLauncher.launch(null)
                    }
                ) {
                    Text(stringResource(R.string.btn_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLinkDialog = false }) {
                    Text(stringResource(R.string.btn_cancel_operation))
                }
            }
        )
    }

    val volumeIdentityKeys = remember(storageVolumes) {
        storageVolumes.map { "${it.id}:${it.rootPath}:${it.name}" }
    }

    LaunchedEffect(volumeIdentityKeys) {
        if (storageVolumes.isNotEmpty()) {
            val hadLeftRoots = treeAdapter.hasRoots(PanelId.LEFT)
            val hadRightRoots = treeAdapter.hasRoots(PanelId.RIGHT)

            treeAdapter.loadVolumesAsRoots(PanelId.LEFT, storageVolumes)
            treeAdapter.loadVolumesAsRoots(PanelId.RIGHT, storageVolumes)

            if (!hadLeftRoots) {
                storageVolumes.firstOrNull()?.let { volume ->
                    val loc = StorageLocation(volume.rootPath, volume.id)
                    viewModel.dispatch(DualPaneEvent.OpenLocation(PanelId.LEFT, loc))
                }
            }
            if (!hadRightRoots) {
                storageVolumes.firstOrNull()?.let { volume ->
                    val loc = StorageLocation(volume.rootPath, volume.id)
                    viewModel.dispatch(DualPaneEvent.OpenLocation(PanelId.RIGHT, loc))
                }
            }
        }
    }

    FileManagerScreen(
        viewModel = viewModel,
        treeAdapter = treeAdapter,
        onSettingsClick = onSettingsClick,
        onLinkStorageClick = { showLinkDialog = true },
        onRemoveLinkClick = onRemoveLinkClick,
        searchDialog = searchDialog,
        operationDialogs = operationDialogs,
        modifier = modifier
    )
}
