package com.wakwau.xplore.filemanager.state

import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.StorageLocation

data class PanelState(
    val id: PanelId,
    val currentLocation: StorageLocation? = null,
    val items: List<FileItem> = emptyList(),
    val selectedItemIds: Set<String> = emptySet(),
    val selectionRevision: Long = 0,
    val isLoading: Boolean = false,
    val loadingRequestId: Long? = null,
    val error: String? = null
)
