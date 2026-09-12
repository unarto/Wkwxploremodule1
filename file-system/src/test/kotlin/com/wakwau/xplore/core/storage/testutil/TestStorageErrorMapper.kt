package com.wakwau.xplore.core.storage.testutil

import com.wakwau.xplore.core.storage.api.error.StorageErrorMapper
import com.wakwau.xplore.core.storage.operation.FileOperationError
import java.io.FileNotFoundException
import java.io.IOException

class TestStorageErrorMapper : StorageErrorMapper {
    override fun map(throwable: Throwable): FileOperationError {
        return when (throwable) {
            is FileNotFoundException -> FileOperationError.NOT_FOUND
            is SecurityException -> FileOperationError.ACCESS_DENIED
            is IllegalArgumentException -> {
                val message = throwable.message ?: ""
                if (message.contains("name", ignoreCase = true)) {
                    FileOperationError.INVALID_NAME
                } else {
                    FileOperationError.INVALID_LOCATION
                }
            }
            is IOException -> {
                val message = throwable.message ?: ""
                when {
                    message.contains("ENOSPC", ignoreCase = true) -> FileOperationError.IO_ERROR
                    message.contains("EACCES", ignoreCase = true) || message.contains("permission", ignoreCase = true) || message.contains("access denied", ignoreCase = true) -> FileOperationError.ACCESS_DENIED
                    message.contains("EEXIST", ignoreCase = true) || message.contains("already exists", ignoreCase = true) -> FileOperationError.ALREADY_EXISTS
                    message.contains("not found", ignoreCase = true) || message.contains("ENOENT", ignoreCase = true) -> FileOperationError.NOT_FOUND
                    else -> FileOperationError.IO_ERROR
                }
            }
            is UnsupportedOperationException -> FileOperationError.NOT_SUPPORTED
            else -> FileOperationError.UNKNOWN
        }
    }
}
