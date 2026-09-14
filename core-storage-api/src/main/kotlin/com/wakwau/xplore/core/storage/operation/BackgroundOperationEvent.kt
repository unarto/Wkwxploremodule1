package com.wakwau.xplore.core.storage.operation

data class BackgroundOperationEvent(
    val operationId: String,
    val result: FileOperationResult<FileOperationProgress>
)
