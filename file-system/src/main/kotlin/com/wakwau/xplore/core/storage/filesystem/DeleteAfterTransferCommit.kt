package com.wakwau.xplore.core.storage.filesystem

import com.wakwau.xplore.core.storage.operation.SourceRemovalCancelled
import com.wakwau.xplore.core.storage.operation.SourceRemovalException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Source deletion is a separate phase: the valid destination must survive it. */
internal suspend fun deleteAfterTransferCommit(deleteSource: suspend () -> Unit) {
    try {
        currentCoroutineContext().ensureActive()
        deleteSource()
    } catch (error: CancellationException) {
        throw SourceRemovalCancelled(error)
    } catch (error: Exception) {
        throw SourceRemovalException(error)
    }
}
