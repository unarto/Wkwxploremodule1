package com.wakwau.xplore.core.storage.operation

import com.wakwau.xplore.core.storage.model.StorageLocation

enum class FileOperationItemStatus { COMPLETED, FAILED, CANCELLED, SKIPPED, NOT_STARTED }

data class FileOperationItemOutcome(
    val source: StorageLocation,
    val target: StorageLocation?,
    val status: FileOperationItemStatus,
    val destinationCommitted: Boolean = false
)
