// [Jalur Class/Modul]: filemanager-ui/src/main/kotlin/com/wakwau/xplore/filemanager/ui/tree/TreeUpDirNavigationHandler.kt
// [Penjelasan]: Helper state holder dan pengelola navigasi hierarki (findNode, navigateUp dengan collapse rekursif) untuk FileTreeEngine.
package com.wakwau.xplore.filemanager.ui.tree

import com.wakwau.xplore.core.storage.constant.StorageConstants
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.filemanager.usecase.GetParentLocationUseCase
import com.wakwau.xplore.treeview.model.TreeNode
import com.wakwau.xplore.treeview.state.TreeState

class TreeUpDirNavigationHandler(
    private val treeState: TreeState<FileItem>,
    private val getParentLocationUseCase: GetParentLocationUseCase = GetParentLocationUseCase()
) {

    fun findNodeByPath(path: String): TreeNode<FileItem>? {
        fun search(nodes: List<TreeNode<FileItem>>): TreeNode<FileItem>? {
            for (node in nodes) {
                if (node.data.location.path == path || node.id == path) return node
                val found = search(node.children)
                if (found != null) return found
            }
            return null
        }
        return search(treeState.roots)
    }

    fun navigateUp(currentPath: String?): StorageLocation? {
        if (currentPath == null) {
            val firstRoot = treeState.roots.firstOrNull() ?: return null
            return firstRoot.data.location
        }

        val currentNode = findNodeByPath(currentPath)
        if (currentNode != null) {
            val parentNode = currentNode.parent
            if (parentNode != null) {
                treeState.collapseRecursively(currentNode)
                if (!treeState.isExpanded(parentNode)) {
                    treeState.expand(parentNode)
                }
                treeState.forceRefresh()
                return parentNode.data.location
            } else {
                // Sudah berada pada batas volume root (isRoot = true), tetap di root tanpa navigasi keluar
                return currentNode.data.location
            }
        } else {
            val rootId = treeState.roots.firstOrNull()?.data?.location?.rootId ?: StorageConstants.UNKNOWN_ROOT_ID
            val parentLocation = getParentLocationUseCase(StorageLocation(currentPath, rootId))
            if (parentLocation != null) {
                val parentNode = findNodeByPath(parentLocation.path)
                if (parentNode != null) {
                    if (!treeState.isExpanded(parentNode)) {
                        treeState.expand(parentNode)
                    }
                    treeState.forceRefresh()
                    return parentNode.data.location
                } else {
                    return parentLocation
                }
            }
            val firstRoot = treeState.roots.firstOrNull()
            if (firstRoot != null) {
                return firstRoot.data.location
            }
            return null
        }
    }
}
