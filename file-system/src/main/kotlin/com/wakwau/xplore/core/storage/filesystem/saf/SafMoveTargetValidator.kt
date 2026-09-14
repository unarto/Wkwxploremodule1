package com.wakwau.xplore.core.storage.filesystem.saf

import java.io.IOException

internal data class SafMoveTargetState(
    val exists: Boolean,
    val isFile: Boolean,
    val isDirectory: Boolean,
    val size: Long
)

internal class SafMoveTargetValidator {
    fun validate(sourceIsDirectory: Boolean, sourceSize: Long, target: SafMoveTargetState?) {
        if (target == null || !target.exists) {
            throw IOException("Move failed: actual SAF target does not exist")
        }
        if (sourceIsDirectory) {
            if (!target.isDirectory) {
                throw IOException("Move failed: actual SAF target is not a directory")
            }
        } else {
            if (!target.isFile || target.size != sourceSize) {
                throw IOException("Move failed: actual SAF target file is missing or incomplete")
            }
        }
    }
}
