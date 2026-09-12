// [Jalur Class/Modul]: filemanager-ui/src/main/kotlin/com/wakwau/xplore/filemanager/ui/screen/FileManagerContent.kt
// [Penjelasan]: Menghubungkan trigger UI SideActionBar dan item long click dengan dialog CreateDirectoryDialog, RenameDialog, DeleteConfirmationDialog, serta FileDetailDialog dan FileSearchDialog secara murni MVI.
package com.wakwau.xplore.filemanager.ui.screen

import android.net.Uri
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.wakwau.xplore.core.storage.constant.StorageConstants
import com.wakwau.xplore.core.storage.model.FileType
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.utils.ui.components.AppDialog
import com.wakwau.xplore.filemanager.ui.dialog.CreateDirectoryDialog
import com.wakwau.xplore.filemanager.ui.component.ActivePanelContent
import com.wakwau.xplore.filemanager.ui.detail.FileDetailDialog
import com.wakwau.xplore.filemanager.ui.component.SideAction
import com.wakwau.xplore.filemanager.ui.component.SideActionBar
import com.wakwau.xplore.filemanager.event.DualPaneEvent
import com.wakwau.xplore.filemanager.ui.gesture.onPanelSwipe
import com.wakwau.xplore.filemanager.state.DualPaneState
import com.wakwau.xplore.filemanager.ui.state.DualPanelStateController
import com.wakwau.xplore.filemanager.ui.state.FileDialogUiState
import com.wakwau.xplore.filemanager.state.FileOperationPanelPosition
import com.wakwau.xplore.filemanager.state.PanelId
import com.wakwau.xplore.filemanager.ui.tree.TreeNavigationAdapter

@Composable
fun FileManagerContent(
    state: DualPaneState,
    dialogUiState: FileDialogUiState,
    treeAdapter: TreeNavigationAdapter,
    operationPanelPosition: FileOperationPanelPosition,
    panelStateController: DualPanelStateController,
    onEvent: (DualPaneEvent) -> Unit,
    showHiddenFiles: Boolean = false,
    onSortClick: () -> Unit = {},
    onRemoveLinkClick: (Uri) -> Unit = {},
    searchDialog: @Composable () -> Unit = {},
    operationDialogs: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isLeftActive = state.activePanelId == PanelId.LEFT
    val activePanel = if (isLeftActive) state.leftPanel else state.rightPanel
    val inactivePanel = if (isLeftActive) state.rightPanel else state.leftPanel
    
    val activeEngine = treeAdapter.getEngine(activePanel.id)
    val visibleNodes by activeEngine.treeState.visibleNodes.collectAsStateWithLifecycle()
    val treeSelectionHandler = remember { com.wakwau.xplore.filemanager.ui.selection.TreeSelectionHandler() }
    val selectedCount = remember(visibleNodes, activePanel.selectedItemIds) {
        visibleNodes.count { 
            treeSelectionHandler.getSelectionState(it.node, activePanel.selectedItemIds) == com.wakwau.xplore.filemanager.ui.selection.FolderCheckCycleState.CHECKED 
        }
    }

    val invalidLocationMsg = stringResource(id = com.wakwau.xplore.filemanager.ui.R.string.err_invalid_location)

    val handleSideAction: (SideAction) -> Unit = { action ->
        val selectedItems = treeAdapter.getSelectedItems(activePanel.id, activePanel.selectedItemIds)
        when (action) {
            SideAction.SWITCH_PANE -> {
                panelStateController.togglePanel()
            }
            SideAction.UP_DIR -> {
                onEvent(DualPaneEvent.NavigateUp(activePanel.id))
            }
            SideAction.NEW_FOLDER -> {
                val getParentPath: (String) -> String = { path ->
                    val trimmed = path.trimEnd('/')
                    val lastSlash = trimmed.lastIndexOf('/')
                    if (lastSlash > 0) trimmed.substring(0, lastSlash)
                    else if (lastSlash == 0) "/"
                    else path
                }
                
                val targetLocation = if (selectedItems.isNotEmpty()) {
                    val firstItem = selectedItems.first()
                    if (firstItem.type == FileType.DIRECTORY) {
                        firstItem.location
                    } else {
                        StorageLocation(path = getParentPath(firstItem.location.path), rootId = firstItem.location.rootId)
                    }
                } else if (activePanel.currentLocation != null) {
                    val cur = activePanel.currentLocation!!
                    StorageLocation(path = cur.path, rootId = cur.rootId)
                } else {
                    val selectedPath = treeAdapter.getSelectedPath(activePanel.id).value
                    if (selectedPath != null) {
                        val rootId = treeAdapter.getEngine(activePanel.id).treeState.roots.firstOrNull()?.data?.location?.rootId
                            ?: StorageConstants.PRIMARY_INTERNAL_VOLUME_ID
                        StorageLocation(path = getParentPath(selectedPath), rootId = rootId)
                    } else {
                        treeAdapter.getEngine(activePanel.id).treeState.roots.firstOrNull()?.data?.location
                    }
                }
                
                if (targetLocation != null) {
                    onEvent(DualPaneEvent.ShowCreateDirectoryDialog(targetLocation))
                }
            }
            SideAction.RENAME -> {
                // [RenameFix]: Lock selection snapshot & safe dialog dismissal berdasarkan ubahnama.md
                val lockedIds = activePanel.selectedItemIds
                if (lockedIds.size == 1) {
                    val lockedPath = lockedIds.first()
                    val targetItem = activeEngine.findNodeByPath(lockedPath)?.data 
                        ?: selectedItems.firstOrNull { it.location.path == lockedPath }
                        
                    if (targetItem != null) {
                        onEvent(DualPaneEvent.ShowRenameDialog(targetItem))
                    }
                } else if (selectedItems.size == 1) {
                    onEvent(DualPaneEvent.ShowRenameDialog(selectedItems.first()))
                }
            }
            SideAction.COPY -> {
                // [CopyFix]: Handle targetPath null warning & lock selection snapshot berdasarkan copy.md
                // [CopyFix]: Final integration validation for Copy-Paste service & UI pipeline berdasarkan copy.md
                val targetPath = inactivePanel.currentLocation?.path 
                    ?: treeAdapter.getSelectedPath(inactivePanel.id).value 
                    ?: treeAdapter.getEngine(inactivePanel.id).treeState.roots.firstOrNull()?.data?.location?.path
                    
                if (targetPath == null) {
                    onEvent(DualPaneEvent.OperationFailed(invalidLocationMsg))
                } else {
                    val lockedIds = activePanel.selectedItemIds.toSet()
                    val lockedItems = lockedIds.mapNotNull { path ->
                        activeEngine.findNodeByPath(path)?.data 
                            ?: selectedItems.firstOrNull { it.location.path == path }
                    }
                    if (lockedItems.isNotEmpty()) {
                        onEvent(DualPaneEvent.ShowOperationConfirmation(isMove = false, items = lockedItems, targetPath = targetPath))
                    } else if (selectedItems.isNotEmpty()) {
                        onEvent(DualPaneEvent.ShowOperationConfirmation(isMove = false, items = selectedItems, targetPath = targetPath))
                    }
                }
            }
            SideAction.MOVE -> {
                val targetPath = inactivePanel.currentLocation?.path 
                    ?: treeAdapter.getSelectedPath(inactivePanel.id).value 
                    ?: treeAdapter.getEngine(inactivePanel.id).treeState.roots.firstOrNull()?.data?.location?.path

                if (selectedItems.isNotEmpty() && targetPath != null) {
                    onEvent(DualPaneEvent.ShowOperationConfirmation(isMove = true, items = selectedItems, targetPath = targetPath))
                }
            }
            SideAction.DELETE -> {
                if (selectedItems.isNotEmpty()) {
                    onEvent(DualPaneEvent.ShowDeleteConfirmationDialog(selectedItems))
                }
            }
            SideAction.SORT -> {
                onSortClick()
            }
            SideAction.TOGGLE_HIDDEN -> {
                onEvent(DualPaneEvent.ToggleShowHiddenFiles)
            }
            SideAction.SEARCH -> {
                onEvent(DualPaneEvent.SearchIconClicked)
            }
        }
    }

    // Input Dialogs (Create Directory, Rename, Delete Confirmation)
    when (val dialog = dialogUiState) {
        is FileDialogUiState.CreateDirectory -> {
            CreateDirectoryDialog(
                onConfirm = { name ->
                    onEvent(DualPaneEvent.CreateDirectory(dialog.parentLocation, name))
                },
                onDismissRequest = { onEvent(DualPaneEvent.DismissInputDialog) }
            )
        }
        FileDialogUiState.None -> Unit
        else -> Unit
    }

    operationDialogs()

    if (state.fileDetailState.isVisible) {
        FileDetailDialog(
            state = state.fileDetailState,
            onDismiss = { onEvent(DualPaneEvent.DismissFileDetails) },
            onCalculateChecksum = { item -> onEvent(DualPaneEvent.CalculateChecksum(item)) },
            onRenameClick = { item ->
                onEvent(DualPaneEvent.DismissFileDetails)
                onEvent(DualPaneEvent.ShowRenameDialog(item))
            },
            onRemoveLinkClick = { item ->
                onEvent(DualPaneEvent.DismissFileDetails)
                onRemoveLinkClick(Uri.parse(item.id))
            }
        )
    }

    searchDialog()

    Row(
        modifier = modifier
            .fillMaxSize()
            .onPanelSwipe(
                onSwipeLeft = { panelStateController.switchToRight() },
                onSwipeRight = { panelStateController.switchToLeft() }
            )
    ) {
        // If RIGHT panel is active: SideActionBar is on the LEFT
        if (!isLeftActive) {
            SideActionBar(
                position = FileOperationPanelPosition.LEFT,
                selectedCount = selectedCount,
                showHiddenFiles = showHiddenFiles,
                onActionClick = handleSideAction
            )
        }

        // Active Panel Content (takes remaining width)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            ActivePanelContent(
                panel = activePanel,
                treeAdapter = treeAdapter,
                onNavigate = { onEvent(DualPaneEvent.OpenLocation(activePanel.id, it)) },
                onItemClick = { item ->
                    if (item.type == FileType.DIRECTORY) {
                        onEvent(DualPaneEvent.OpenLocation(activePanel.id, item.location))
                    }
                },
                onItemLongClick = { item ->
                    // [FileManagerUI]: Penyelarasan identitas seleksi path dan pemicuan atomik onSelectionChange berdasarkan Mark.MD
                    onEvent(DualPaneEvent.SetSelectedItems(activePanel.id, setOf(item.location.path)))
                    onEvent(DualPaneEvent.ShowFileDetails(item))
                },
                onIconClick = { item ->
                    // [FileManagerUI]: Penyelarasan identitas seleksi path dan pemicuan atomik onSelectionChange berdasarkan Mark.MD
                    onEvent(DualPaneEvent.SetSelectedItems(activePanel.id, setOf(item.location.path)))
                    onEvent(DualPaneEvent.ShowFileDetails(item))
                },
                onSelectionChange = { selectedIds ->
                    onEvent(DualPaneEvent.SetSelectedItems(activePanel.id, selectedIds))
                },
                onRetry = { onEvent(DualPaneEvent.Refresh(activePanel.id)) }
            )
        }

        // If LEFT panel is active: SideActionBar is on the RIGHT
        if (isLeftActive) {
            SideActionBar(
                position = FileOperationPanelPosition.RIGHT,
                selectedCount = selectedCount,
                showHiddenFiles = showHiddenFiles,
                onActionClick = handleSideAction
            )
        }
    }
}
