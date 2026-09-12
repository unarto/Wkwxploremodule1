    init {
        // [CopyFix]: Connect observeProgress flow to UI events berdasarkan copy.md
        viewModelScope.launch {
            backgroundOperationClient.observeProgress().collect { result ->
                when (result) {
                    is com.wakwau.xplore.core.storage.operation.FileOperationResult.Success -> {
                        internalDispatch(DualPaneEvent.OperationProgress(result.data))
                    }
                    is com.wakwau.xplore.core.storage.operation.FileOperationResult.Completed -> {
                        val msgRes = when (result.operationType) {
                            com.wakwau.xplore.core.storage.operation.BackgroundOperationType.COPY -> FileOperationConstants.SUCCESS_COPY
                            com.wakwau.xplore.core.storage.operation.BackgroundOperationType.MOVE -> FileOperationConstants.SUCCESS_MOVE
                            com.wakwau.xplore.core.storage.operation.BackgroundOperationType.DELETE -> FileOperationConstants.SUCCESS_DELETE
                        }
                        internalDispatch(DualPaneEvent.OperationSuccess(msgRes))
                    }
                    is com.wakwau.xplore.core.storage.operation.FileOperationResult.Failure -> {
                        internalDispatch(DualPaneEvent.OperationFailed(result.error.name))
                    }
                    is com.wakwau.xplore.core.storage.operation.FileOperationResult.Cancelled -> {
                        internalDispatch(DualPaneEvent.OperationCancelled)
                    }
                }
            }
        }
    }
