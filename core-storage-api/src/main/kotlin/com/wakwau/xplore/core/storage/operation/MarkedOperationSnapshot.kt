package com.wakwau.xplore.core.storage.operation

import com.wakwau.xplore.core.storage.model.FileItem

data class MarkedOperationSnapshot(
    val items: List<FileItem>,
    val unresolvedIds: Set<String>
) {
    val isValid: Boolean get() = unresolvedIds.isEmpty()
    val count: Int get() = items.size
}
