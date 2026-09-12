// [Jalur Class/Modul]: filemanager-ui/src/main/kotlin/com/wakwau/xplore/filemanager/ui/components/filetree/FileTreeNodeItem.kt
// [Penjelasan]: Komponen composable untuk me-render baris item pohon berkas lengkap dengan indentasi, panah ekspansi, dan konten node.
package com.wakwau.xplore.filemanager.ui.components.filetree

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.treeview.component.TreeExpandToggle
import com.wakwau.xplore.treeview.model.FlattenedTreeNode
import com.wakwau.xplore.treeview.model.TreeNode

@Composable
fun FileTreeNodeItem(
    flattenedNode: FlattenedTreeNode<FileItem>,
    branchColor: Color,
    expandArrowTint: Color,
    expandContentDescription: String,
    collapseContentDescription: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (node: TreeNode<FileItem>) -> Unit
) {
    val node = flattenedNode.node

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileTreeIndentSpacer(
            depth = flattenedNode.depth,
            isLastChild = flattenedNode.isLastChild,
            ancestorHasNextSibling = flattenedNode.ancestorHasNextSibling,
            branchColor = branchColor
        )

        TreeExpandToggle(
            isExpanded = node.isExpanded,
            isLeaf = !node.hasChildren,
            tint = expandArrowTint,
            expandContentDescription = expandContentDescription,
            collapseContentDescription = collapseContentDescription,
            onToggle = onToggle
        )

        content(node)
    }
}
