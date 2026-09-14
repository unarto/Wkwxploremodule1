package com.wakwau.xplore.fileoperations.executor

import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.BackgroundOperationType
import com.wakwau.xplore.fileoperations.conflict.ResolvedTransferItem

data class FileOperationRequest(
    val type: BackgroundOperationType,
    val sources: List<StorageLocation> = emptyList(),
    val destination: StorageLocation? = null,
    val resolvedItems: List<ResolvedTransferItem>? = null
)
