// [Jalur Class/Modul]: filemanager-ui/src/main/kotlin/com/wakwau/xplore/filemanager/ui/component/DirectoryTreeView.kt
// [Penjelasan]: Composable wrapper terisolasi untuk merender tampilan pohon berkas (file tree), menyinkronkan StorageLocation saat navigasi, dan menangani empty state murni di layer UI tanpa menyuntikkan placeholder ke data domain tree.
package com.wakwau.xplore.filemanager.ui.component

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.FileType
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.filemanager.ui.list.FileListEmpty
import com.wakwau.xplore.filemanager.ui.list.FileListError
import com.wakwau.xplore.filemanager.ui.list.FileListItem
import com.wakwau.xplore.filemanager.ui.selection.TreeSelectionHandler
import com.wakwau.xplore.filemanager.state.PanelState
import com.wakwau.xplore.filemanager.ui.tree.TreeNavigationAdapter
import com.wakwau.xplore.treeview.component.ComposeTreeView
import com.wakwau.xplore.treeview.interaction.TreeInteraction
import com.wakwau.xplore.treeview.model.TreeNode
import kotlinx.coroutines.launch

@Composable
fun DirectoryTreeView(
    panelState: PanelState,
    treeAdapter: TreeNavigationAdapter,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    onSelectionChange: (Set<String>) -> Unit,
    onRetry: () -> Unit,
    onNavigate: (StorageLocation) -> Unit = {},
    onIconClick: (FileItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val engine = treeAdapter.getEngine(panelState.id)
    val errorState by engine.errorState.collectAsStateWithLifecycle()
    val selectedPath by engine.selectedPath.collectAsStateWithLifecycle()
    val visibleNodes by engine.treeState.visibleNodes.collectAsStateWithLifecycle()
    val treeSelectionHandler = remember { TreeSelectionHandler() }

    val interaction = remember(panelState.id, treeAdapter, coroutineScope, onNavigate, onItemClick, onItemLongClick) {
        object : TreeInteraction<FileItem> {
            override fun onToggle(node: TreeNode<FileItem>) {
                treeAdapter.setSelectedPath(panelState.id, node.data.location.path)
                onNavigate(node.data.location)
                coroutineScope.launch {
                    treeAdapter.toggleNode(panelState.id, node)
                }
            }
            override fun onNodeClick(node: TreeNode<FileItem>) {
                treeAdapter.setSelectedPath(panelState.id, node.data.location.path)
                onNavigate(node.data.location)
                onItemClick(node.data)
            }
            override fun onNodeLongClick(node: TreeNode<FileItem>) {
                treeAdapter.setSelectedPath(panelState.id, node.data.location.path)
                onNavigate(node.data.location)
                onItemLongClick(node.data)
            }
        }
    }

    if (errorState != null) {
        FileListError(
            error = errorState ?: "",
            onRetry = {
                treeAdapter.clearError(panelState.id)
                onRetry()
            },
            modifier = modifier
        )
    } else if (visibleNodes.isEmpty()) {
        FileListEmpty(modifier = modifier)
    } else {
        val colors = com.wakwau.xplore.core.utils.ui.theme.LocalXPloreColors.current
        ComposeTreeView(
            treeState = engine.treeState,
            borderColor = colors.folderSelectionColor,
            branchColor = colors.treeLine,
            expandArrowTint = colors.treeExpandArrow,
            expandContentDescription = androidx.compose.ui.res.stringResource(com.wakwau.xplore.core.utils.ui.R.string.cd_expand),
            collapseContentDescription = androidx.compose.ui.res.stringResource(com.wakwau.xplore.core.utils.ui.R.string.cd_collapse),
            modifier = modifier,
            focusedId = selectedPath,
            interaction = interaction,
            key = { _, it -> "${it.node.data.location.path}_${it.node.data.id}" }
        ) { node, borderPosition ->
            val selectionState = treeSelectionHandler.getSelectionState(node, panelState.selectedItemIds)
            FileListItem(
                item = node.data,
                isSelected = panelState.selectedItemIds.contains(node.data.location.path),
                borderPosition = borderPosition,
                selectionState = selectionState,
                onClick = {
                    treeAdapter.setSelectedPath(panelState.id, node.data.location.path)
                    onNavigate(node.data.location)
                    if (node.data.type == FileType.DIRECTORY) {
                        coroutineScope.launch {
                            treeAdapter.toggleNode(panelState.id, node)
                        }
                    } else {
                        onItemClick(node.data)
                    }
                },
                onLongClick = {
                    treeAdapter.setSelectedPath(panelState.id, node.data.location.path)
                    onNavigate(node.data.location)
                    onItemLongClick(node.data)
                },
                onCheckToggle = {
                    var shouldExpand = false
                    val newSelection = treeSelectionHandler.nextSelection(node, panelState.selectedItemIds) {
                        if (!engine.treeState.isExpanded(node)) {
                            shouldExpand = true
                        }
                    }
                    // [FileManagerUI]: Penyelarasan identitas seleksi path dan pemicuan atomik onSelectionChange berdasarkan Mark.MD
                    onSelectionChange(newSelection)
                    
                    if (shouldExpand) {
                        coroutineScope.launch {
                            treeAdapter.expandNode(panelState.id, node)
                        }
                    }
                },
                onIconClick = { onIconClick(node.data) },
                showExpandArrow = false
            )
        }
    }
}
