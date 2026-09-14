package com.wakwau.xplore.orchestrator

import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.BackgroundOperationType
import com.wakwau.xplore.fileoperations.conflict.ResolvedTransferItem

internal sealed interface PendingIndexMutation {
    data class Transfer(
        val type: BackgroundOperationType,
        val items: List<ResolvedTransferItem>
    ) : PendingIndexMutation

    data class Delete(val sources: List<StorageLocation>) : PendingIndexMutation
}

internal class PendingIndexMutations {
    private val mutations = mutableMapOf<String, PendingIndexMutation>()

    fun put(operationId: String, mutation: PendingIndexMutation) {
        mutations[operationId] = mutation
    }

    fun take(operationId: String, type: BackgroundOperationType): PendingIndexMutation? {
        val mutation = mutations[operationId] ?: return null
        val matchesType = when (mutation) {
            is PendingIndexMutation.Transfer -> mutation.type == type
            is PendingIndexMutation.Delete -> type == BackgroundOperationType.DELETE
        }
        return if (matchesType) mutations.remove(operationId) else null
    }

    fun remove(operationId: String): Boolean = mutations.remove(operationId) != null
}
