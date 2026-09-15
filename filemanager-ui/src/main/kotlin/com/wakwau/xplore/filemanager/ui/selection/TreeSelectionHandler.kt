package com.wakwau.xplore.filemanager.ui.selection

import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.FileType
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.model.isSameOrDescendantOf
import com.wakwau.xplore.filemanager.selection.MarkSelectionPolicy
import com.wakwau.xplore.treeview.model.TreeNode

/** Adapts generic rendered nodes to the browser's selection policy. */
class TreeSelectionHandler {
    fun collectDescendantPaths(node: TreeNode<FileItem>): Set<String> {
        val result = mutableSetOf<String>()
        val pending = ArrayDeque<TreeNode<FileItem>>()
        node.children.forEach { pending.addLast(it) }
        while (pending.isNotEmpty()) {
            val child = pending.removeFirst()
            result.add(child.data.location.path)
            child.children.forEach { pending.addLast(it) }
        }
        return result
    }

    fun getSelectionState(node: TreeNode<FileItem>, selectedIds: Set<String>): FolderCheckCycleState {
        val root = node.isRoot || node.parent == null
        if (!root && node.data.location.path in selectedIds) return FolderCheckCycleState.CHECKED
        val descendants = collectDescendantPaths(node)
        if (node.data.type == FileType.DIRECTORY && selectedIds.any {
            it in descendants || (it != node.data.location.path &&
                StorageLocation(it, node.data.location.rootId).isSameOrDescendantOf(node.data.location))
        }) return FolderCheckCycleState.PARTIAL
        return FolderCheckCycleState.UNCHECKED
    }

    fun needsChildren(node: TreeNode<FileItem>, ids: Set<String>): Boolean =
        node.data.type == FileType.DIRECTORY &&
            (node.isRoot || node.parent == null || node.data.location.path in ids)

    fun nextSelection(node: TreeNode<FileItem>, currentSelection: Set<String>, onAutoExpand: () -> Unit = {}): Set<String> {
        if (needsChildren(node, currentSelection)) onAutoExpand()
        return MarkSelectionPolicy.nextSelection(
            node.data, node.parent?.data?.location?.path.orEmpty(), node.children.map { it.data },
            collectDescendantPaths(node), node.isRoot || node.parent == null, currentSelection
        )
    }
}
