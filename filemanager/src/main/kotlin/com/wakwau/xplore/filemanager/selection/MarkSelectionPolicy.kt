package com.wakwau.xplore.filemanager.selection

import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.FileType
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.model.isSameOrDescendantOf

/** Browser selection transitions, independent of Compose and tree rendering. */
object MarkSelectionPolicy {
    private fun enforceStrictScopeIsolation(
        currentSelection: MutableSet<String>,
        activeParentPath: String
    ) {
        val parent = StorageLocation(activeParentPath)
        currentSelection.retainAll { selectedPath ->
            val selected = StorageLocation(selectedPath)
            selected.isSameOrDescendantOf(parent) || parent.isSameOrDescendantOf(selected)
        }
    }

    fun nextSelection(
        item: FileItem,
        parentPath: String,
        directChildren: List<FileItem>,
        descendantPaths: Set<String>,
        isRoot: Boolean,
        currentSelection: Set<String>
    ): Set<String> {
        val newSelection = currentSelection.toMutableSet()
        val nodePath = item.location.path
        fun covers(path: String) = StorageLocation(path, item.location.rootId).isSameOrDescendantOf(item.location)

        // 1. Level File Node (Bukan Direktori) ATAU Seleksi Tunggal Anak
        if (item.type != FileType.DIRECTORY) {
            if (newSelection.contains(nodePath)) {
                newSelection.remove(nodePath)
            } else {
                if (parentPath.isNotEmpty()) {
                    enforceStrictScopeIsolation(newSelection, parentPath)
                }
                newSelection.add(nodePath)
            }
            return newSelection
        }

        // 2. Level Storage Node (Internal Storage / SD Card)
        if (isRoot) {
            newSelection.remove(nodePath)

            if (directChildren.isEmpty()) {
                return newSelection
            }

            val directChildPaths = directChildren.map { it.location.path }.toSet()
            val allDirectChildrenMarked = directChildren.all { child ->
                newSelection.contains(child.location.path)
            }

            if (allDirectChildrenMarked) {
                // Klik 2 pada Storage: Hapus semua tercentang di bawah storage
                newSelection.removeAll { path ->
                    path == nodePath || covers(path) || directChildPaths.contains(path)
                }
            } else {
                // Klik 1 pada Storage: Bersihkan tempat lain, centang HANYA direct children storage ini
                newSelection.clear()
                directChildren.forEach { child ->
                    newSelection.add(child.location.path)
                }
            }
            return newSelection
        }

        // 3. Level Folder Node (3-Step Cycle)
        val isFolderSelfMarked = newSelection.contains(nodePath)

        val hasAnyDescendantMarked = newSelection.any { path ->
            (covers(path) || descendantPaths.contains(path)) && path != nodePath
        }

        when {
            // SIKLUS KLIK 2: Mark All Children + Auto Expand
            isFolderSelfMarked -> {
                // Unmark folder induk
                newSelection.remove(nodePath)

                // Bersihkan item di luar scope folder ini
                enforceStrictScopeIsolation(newSelection, nodePath)

                // Centang semua anak langsung
                if (directChildren.isNotEmpty()) {
                    directChildren.forEach { child ->
                        newSelection.add(child.location.path)
                    }
                }
            }

            // SIKLUS KLIK 3: Unmark All
            hasAnyDescendantMarked -> {
                newSelection.remove(nodePath)
                newSelection.removeAll { path ->
                    covers(path) || descendantPaths.contains(path)
                }
            }

            // SIKLUS KLIK 1: Single Mark Folder
            else -> {
                // Bersihkan centang di luar jalur folder ini (DCIM dll auto hapus)
                if (parentPath.isNotEmpty()) {
                    enforceStrictScopeIsolation(newSelection, parentPath)
                } else {
                    enforceStrictScopeIsolation(newSelection, nodePath)
                }

                // Centang full path folder ini
                newSelection.add(nodePath)
            }
        }

        return newSelection
    }
}
