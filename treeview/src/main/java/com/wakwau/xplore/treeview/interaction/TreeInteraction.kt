// [Jalur Class/Modul]: treeview/src/main/java/com/wakwau/xplore/treeview/interaction/TreeInteraction.kt
// [Penjelasan]: Implementasi komponen UI / struktur data untuk TreeView yang bersifat generik dan dapat digunakan ulang tanpa keterikatan domain spesifik.
package com.wakwau.xplore.treeview.interaction

import com.wakwau.xplore.treeview.model.TreeNode

interface TreeInteraction<T> {
    fun onNodeClick(node: TreeNode<T>) {}
    fun onNodeLongClick(node: TreeNode<T>) {}
    fun onToggle(node: TreeNode<T>) {}
}
