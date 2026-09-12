// [Jalur Class/Modul]: filemanager-ui/src/main/kotlin/com/wakwau/xplore/filemanager/ui/tree/FileTreeEngine.kt
// [Penjelasan]: Engine pohon direktori berkas yang memuat hierarki folder secara asinkron menggunakan ListDirectoryUseCase, TreeState, FileTreeItemFactory, TreeUpDirNavigationHandler, dan TreeNodeSorter tanpa God Class.
package com.wakwau.xplore.filemanager.ui.tree

import com.wakwau.xplore.core.storage.constant.StorageConstants
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.FileType
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import com.wakwau.xplore.core.storage.preferences.AppPreferencesRepository
import com.wakwau.xplore.filemanager.factory.FileTreeItemFactory
import com.wakwau.xplore.filemanager.usecase.GetParentLocationUseCase
import com.wakwau.xplore.filemanager.usecase.ListDirectoryUseCase
import com.wakwau.xplore.treeview.model.BorderPosition
import com.wakwau.xplore.treeview.model.TreeNode
import com.wakwau.xplore.treeview.model.TreeScopeCalculator
import com.wakwau.xplore.treeview.state.TreeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class FileTreeEngine(
    private val listDirectoryUseCase: ListDirectoryUseCase,
    private val appPreferencesRepository: AppPreferencesRepository? = null,
    private val fileTreeItemFactory: FileTreeItemFactory = FileTreeItemFactory(),
    getParentLocationUseCase: GetParentLocationUseCase = GetParentLocationUseCase(),
    val treeState: TreeState<FileItem> = TreeState()
) {
    private val loadingNodes = ConcurrentHashMap.newKeySet<String>()

    private val expansionState = TreeUpDirNavigationHandler(treeState, getParentLocationUseCase)
    private val nodeSorter = TreeNodeSorter(appPreferencesRepository)

    private val _selectedPath = MutableStateFlow<String?>(null)
    val selectedPath: StateFlow<String?> = _selectedPath.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    suspend fun refreshExpandedNodes() {
        // [RenameFix]: Mencegah stale node query & double refresh berdasarkan ubahnama.md
        // Hanya refresh pada root nodes dan handle _errorState di per child scope, tidak secara global
        suspend fun refreshNode(node: TreeNode<FileItem>) {
            if (treeState.isExpanded(node) && node.data.type == FileType.DIRECTORY) {
                val previousExpandedIds = node.children.filter { it.isExpanded }.map { it.id }.toSet()
                
                try {
                    when (val result = listDirectoryUseCase(node.data.location)) {
                        is FileOperationResult.Success -> {
                            node.clearChildren()
                            val comparator = nodeSorter.getComparator()
                            val sortedItems = result.data.map { item ->
                                TreeNode(data = item, id = item.location.path)
                            }.sortedWith(comparator)
                            
                            sortedItems.forEach { node.addChild(it) }
                            
                            for (child in node.children) {
                                if (previousExpandedIds.contains(child.id)) {
                                    treeState.expand(child)
                                    refreshNode(child)
                                }
                            }
                        }
                        else -> {
                            // Do not globally set _errorState here to avoid UI flicker on a single stale node
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        }
        for (root in treeState.roots) {
            refreshNode(root)
        }
        treeState.forceRefresh()
    }

    suspend fun refreshNodeByPath(path: String) {
        val node = findNodeByPath(path)
        if (node != null && node.data.type == FileType.DIRECTORY && treeState.isExpanded(node)) {
            val previousExpandedIds = node.children.filter { it.isExpanded }.map { it.id }.toSet()
            loadChildren(node)
            for (child in node.children) {
                if (previousExpandedIds.contains(child.id)) {
                    treeState.expand(child)
                }
            }
            treeState.forceRefresh()
        } else {
            refreshExpandedNodes()
        }
    }

    fun setSelectedPath(path: String?) {
        _selectedPath.value = path
    }

    fun findNodeByPath(path: String): TreeNode<FileItem>? {
        return expansionState.findNodeByPath(path)
    }

    fun navigateUp(): StorageLocation? {
        val destination = expansionState.navigateUp(_selectedPath.value)
        if (destination != null) {
            _selectedPath.value = destination.path
        }
        return destination
    }

    suspend fun loadVolumesAsRoots(volumes: List<com.wakwau.xplore.core.storage.model.StorageVolumeItem>) {
        _errorState.value = null
        val roots = volumes.map { volume ->
            val rootItem = fileTreeItemFactory.createVolumeRoot(volume)
            TreeNode(data = rootItem, id = volume.rootPath)
        }
        treeState.setRoots(roots)
        if (_selectedPath.value == null && roots.isNotEmpty()) {
            _selectedPath.value = roots.first().data.location.path
        }
    }

    suspend fun loadRoot(rootItem: FileItem) {
        _errorState.value = null
        val rootNode = TreeNode(data = rootItem, id = rootItem.location.path)
        treeState.setRoots(listOf(rootNode))
        loadChildren(rootNode)
    }

    suspend fun expandNode(node: TreeNode<FileItem>) {
        _errorState.value = null
        if (!treeState.isExpanded(node)) {
            if (!node.hasChildren && node.data.type == FileType.DIRECTORY) {
                loadChildren(node)
            } else {
                treeState.expand(node)
            }
        }
    }

    suspend fun toggleNode(node: TreeNode<FileItem>) {
        _errorState.value = null
        if (treeState.isExpanded(node)) {
            treeState.collapse(node)
        } else {
            if (!node.hasChildren && node.data.type == FileType.DIRECTORY) {
                loadChildren(node)
            } else {
                treeState.expand(node)
            }
        }
    }

    private suspend fun loadChildren(node: TreeNode<FileItem>) {
        if (!loadingNodes.add(node.id)) return
        try {
            when (val result = listDirectoryUseCase(node.data.location)) {
                is FileOperationResult.Success -> {
                    node.clearChildren()
                    val comparator = nodeSorter.getComparator()
                    val sortedItems = result.data.map { item ->
                        TreeNode(data = item, id = item.location.path)
                    }.sortedWith(comparator)
                    
                    sortedItems.forEach { node.addChild(it) }
                    treeState.expand(node)
                }
                is FileOperationResult.Failure -> {
                    _errorState.value = result.error.name
                }
                is FileOperationResult.Cancelled -> {
                    // Ignore
                }
                is FileOperationResult.Completed -> {
                    // No-op
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _errorState.value = e.message
        } finally {
            loadingNodes.remove(node.id)
        }
    }

    fun reSortCurrentNodes() {
        val comparator = nodeSorter.getComparator()
        treeState.roots.forEach { it.sortChildren(comparator) }
        treeState.forceRefresh()
    }

    fun clearError() {
        _errorState.value = null
    }

    fun getFocusRange(): IntRange? {
        val visibleNodes = treeState.visibleNodes.value
        val path = _selectedPath.value
        return TreeScopeCalculator.calculateFocusRange(visibleNodes, path) { it.location.path }
    }

    fun getBorderPositionForIndex(index: Int): BorderPosition {
        val range = getFocusRange()
        return TreeScopeCalculator.getBorderPosition(index, range)
    }

    fun getSelectedItems(selectedIds: Set<String>): List<FileItem> {
        val selectedItems = mutableListOf<FileItem>()
        fun traverse(nodes: List<TreeNode<FileItem>>) {
            for (node in nodes) {
                if (!node.isRoot && (selectedIds.contains(node.data.location.path))) {
                    selectedItems.add(node.data)
                } else {
                    traverse(node.children)
                }
            }
        }
        traverse(treeState.roots)
        return selectedItems
    }

    fun updateSearchResults(keyword: String, items: List<FileItem>) {
        val searchRootId = StorageConstants.VIRTUAL_SEARCH_ROOT_ID
        
        // Remove existing search results root if any
        val filteredRoots = treeState.roots.filter { it.id != searchRootId }.toMutableList()
        
        val countLabel = "${StorageConstants.SEARCH_RESULTS_PREFIX}(${items.size})"
        val rootItem = fileTreeItemFactory.createSearchResultsRoot(
            keyword = keyword
        ).copy(name = countLabel)
        val searchRootNode = TreeNode(data = rootItem, id = searchRootId)
        
        val comparator = nodeSorter.getComparator()
        val sortedItems = items.map { item ->
            val searchItem = item.copy(id = "${StorageConstants.SEARCH_RESULT_ID_PREFIX}${item.location.path}")
            TreeNode(data = searchItem, id = searchItem.id)
        }.sortedWith(comparator)
        
        sortedItems.forEach { searchRootNode.addChild(it) }
        
        filteredRoots.add(searchRootNode)
        treeState.setRoots(filteredRoots)
        treeState.expand(searchRootNode)
        treeState.forceRefresh()
    }

    fun clear() {
        loadingNodes.clear()
        _selectedPath.value = null
        _errorState.value = null
        treeState.setRoots(emptyList())
    }
}
