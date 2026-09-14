// [Jalur Class/Modul]: treeview/src/main/java/com/wakwau/xplore/treeview/state/TreeState.kt
// [Penjelasan]: Implementasi state container TreeView.
package com.wakwau.xplore.treeview.state

import com.wakwau.xplore.treeview.model.FlattenedTreeNode
import com.wakwau.xplore.treeview.model.TreeNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TreeState<T> {
    private val _roots = mutableListOf<TreeNode<T>>()
    val roots: List<TreeNode<T>> get() = _roots

    private val _visibleNodes = MutableStateFlow<List<FlattenedTreeNode<T>>>(emptyList())
    val visibleNodes: StateFlow<List<FlattenedTreeNode<T>>> = _visibleNodes.asStateFlow()

    fun setRoots(newRoots: List<TreeNode<T>>) {
        _roots.clear()
        _roots.addAll(newRoots)
        updateVisibleNodes()
    }

    fun clear() {
        _roots.clear()
        updateVisibleNodes()
    }

    fun expand(node: TreeNode<T>) {
        node.expand()
        updateVisibleNodes()
    }

    fun collapse(node: TreeNode<T>) {
        node.collapse()
        updateVisibleNodes()
    }

    fun collapseRecursively(node: TreeNode<T>) {
        node.collapseRecursively()
        updateVisibleNodes()
    }

    fun toggle(node: TreeNode<T>) {
        node.toggleExpanded()
        updateVisibleNodes()
    }

    fun isExpanded(node: TreeNode<T>): Boolean {
        return node.isExpanded
    }

    fun forceRefresh() {
        updateVisibleNodes()
    }

    private fun updateVisibleNodes() {
        val flatList = mutableListOf<FlattenedTreeNode<T>>()
        
        // Iterative DFS stack: stores (node, depth, isLastChild, ancestorsHasNextSibling)
        val stack = java.util.ArrayDeque<Triple<TreeNode<T>, Boolean, List<Boolean>>>()
        
        // Push roots in reverse order so they are processed correctly
        for (i in _roots.indices.reversed()) {
            stack.push(Triple(_roots[i], i == _roots.lastIndex, emptyList()))
        }
        
        while (stack.isNotEmpty()) {
            val (node, isLastChild, ancestorsHasNextSibling) = stack.pop()
            
            flatList.add(
                FlattenedTreeNode(
                    node = node,
                    depth = node.depth,
                    isLastChild = isLastChild,
                    ancestorHasNextSibling = ancestorsHasNextSibling
                )
            )
            
            if (node.isExpanded) {
                val children = node.children
                val nextAncestors = ancestorsHasNextSibling + (!isLastChild)
                
                // Push children in reverse order
                for (i in children.indices.reversed()) {
                    stack.push(Triple(children[i], i == children.lastIndex, nextAncestors))
                }
            }
        }
        
        _visibleNodes.value = flatList
    }
}
