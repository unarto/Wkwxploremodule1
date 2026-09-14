// [Jalur Class/Modul]: treeview/src/main/java/com/wakwau/xplore/treeview/model/FlattenedTreeNode.kt
// [Penjelasan]: Implementasi komponen UI / struktur data untuk TreeView yang bersifat generik dan dapat digunakan ulang tanpa keterikatan domain spesifik.
package com.wakwau.xplore.treeview.model

data class FlattenedTreeNode<T>(
    val node: TreeNode<T>,
    val depth: Int,
    val isLastChild: Boolean = false,
    val ancestorHasNextSibling: List<Boolean> = emptyList()
)
