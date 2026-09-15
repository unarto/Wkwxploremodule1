package com.wakwau.xplore.fileoperations.selection

import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.FileType
import com.wakwau.xplore.core.storage.model.isSameOrDescendantOf
import com.wakwau.xplore.core.storage.operation.MarkedOperationSnapshot

object NormalizeMarkedItems {
    operator fun invoke(ids: Set<String>, candidates: List<FileItem>): MarkedOperationSnapshot {
        val byPath = candidates.groupBy { it.location.path }
        val unresolved = ids.filterTo(linkedSetOf()) { id ->
            byPath[id].orEmpty().map { it.location to it.type }.distinct().size != 1
        }
        val selected = mutableListOf<FileItem>()
        for (id in ids.filterNot { it in unresolved }) {
            val item = byPath.getValue(id).first()
            if (selected.none { it.location.isSameOrDescendantOf(item.location) && item.location.isSameOrDescendantOf(it.location) }) {
                selected.add(item)
            }
        }
        val items = selected.filter { item ->
            selected.none { parent ->
                parent.location != item.location && parent.type == FileType.DIRECTORY &&
                    item.location.isSameOrDescendantOf(parent.location)
            }
        }
        return MarkedOperationSnapshot(items, unresolved)
    }
}
