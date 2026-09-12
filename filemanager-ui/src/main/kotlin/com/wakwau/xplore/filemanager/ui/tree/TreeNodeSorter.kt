// [Jalur Class/Modul]: filemanager-ui/src/main/kotlin/com/wakwau/xplore/filemanager/ui/components/filetree/TreeNodeSorter.kt
// [Penjelasan]: Helper penentu komparator penyortiran node pohon berkas berdasarkan preferensi pengguna (nama, tanggal, ukuran, tipe, asc/desc, dan direktori duluan).
package com.wakwau.xplore.filemanager.ui.tree

import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.FileType
import com.wakwau.xplore.core.storage.preferences.AppPreferencesRepository
import com.wakwau.xplore.core.storage.preferences.FileSortDirection
import com.wakwau.xplore.core.storage.preferences.FileSortOrder
import com.wakwau.xplore.treeview.model.TreeNode

class TreeNodeSorter(
    private val appPreferencesRepository: AppPreferencesRepository? = null
) {
    fun getComparator(): java.util.Comparator<TreeNode<FileItem>> {
        val prefs = appPreferencesRepository?.preferencesState?.value
        val order = prefs?.sortOrder ?: FileSortOrder.NAME
        val direction = prefs?.sortDirection ?: FileSortDirection.ASCENDING

        val baseComparator = when (order) {
            FileSortOrder.NAME -> compareBy<TreeNode<FileItem>> { it.data.name.lowercase() }
            FileSortOrder.DATE -> compareBy { it.data.metadata.modifiedTime }
            FileSortOrder.SIZE -> compareBy { it.data.metadata.size }
            FileSortOrder.TYPE -> compareBy { it.data.name.substringAfterLast('.', "") }
        }

        val directedComparator = if (direction == FileSortDirection.DESCENDING) {
            baseComparator.reversed()
        } else {
            baseComparator
        }

        return compareBy<TreeNode<FileItem>> { it.data.type != FileType.DIRECTORY }.then(directedComparator)
    }
}
