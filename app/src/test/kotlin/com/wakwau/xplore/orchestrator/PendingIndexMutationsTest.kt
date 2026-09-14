package com.wakwau.xplore.orchestrator

import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.BackgroundOperationType
import com.wakwau.xplore.fileoperations.conflict.ConflictChoice
import com.wakwau.xplore.fileoperations.conflict.ResolvedTransferItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingIndexMutationsTest {
    @Test
    fun overlappingOperations_keepTheirOwnMetadata() {
        val pending = PendingIndexMutations()
        val first = transfer("first.txt")
        val second = transfer("second.txt")

        pending.put("copy-1", PendingIndexMutation.Transfer(BackgroundOperationType.COPY, listOf(first)))
        pending.put("move-2", PendingIndexMutation.Transfer(BackgroundOperationType.MOVE, listOf(second)))

        assertEquals(listOf(first), (pending.take("copy-1", BackgroundOperationType.COPY) as PendingIndexMutation.Transfer).items)
        assertEquals(listOf(second), (pending.take("move-2", BackgroundOperationType.MOVE) as PendingIndexMutation.Transfer).items)
    }

    @Test
    fun staleCompletion_isIgnored() {
        val pending = PendingIndexMutations()
        pending.put("current", PendingIndexMutation.Transfer(BackgroundOperationType.COPY, listOf(transfer("current.txt"))))

        assertNull(pending.take("stale", BackgroundOperationType.COPY))
        assertEquals("current.txt", (pending.take("current", BackgroundOperationType.COPY) as PendingIndexMutation.Transfer).items.single().targetName)
    }

    @Test
    fun failureOrCancellation_removesOnlyMatchingOperation() {
        val pending = PendingIndexMutations()
        pending.put("failed", PendingIndexMutation.Transfer(BackgroundOperationType.COPY, listOf(transfer("failed.txt"))))
        pending.put("cancelled", PendingIndexMutation.Transfer(BackgroundOperationType.MOVE, listOf(transfer("cancelled.txt"))))
        pending.put("active", PendingIndexMutation.Transfer(BackgroundOperationType.MOVE, listOf(transfer("active.txt"))))

        pending.remove("failed")
        pending.remove("cancelled")

        assertNull(pending.take("failed", BackgroundOperationType.COPY))
        assertNull(pending.take("cancelled", BackgroundOperationType.MOVE))
        assertEquals("active.txt", (pending.take("active", BackgroundOperationType.MOVE) as PendingIndexMutation.Transfer).items.single().targetName)
    }

    @Test
    fun successfulCompletion_consumesMetadataOnlyOnce() {
        val pending = PendingIndexMutations()
        pending.put("copy-1", PendingIndexMutation.Transfer(BackgroundOperationType.COPY, listOf(transfer("file.txt"))))

        val firstCompletion = pending.take("copy-1", BackgroundOperationType.COPY)
        val duplicateCompletion = pending.take("copy-1", BackgroundOperationType.COPY)

        assertEquals("file.txt", (firstCompletion as PendingIndexMutation.Transfer).items.single().targetName)
        assertNull(duplicateCompletion)
    }

    private fun transfer(name: String): ResolvedTransferItem {
        val source = StorageLocation("/source/$name", "local")
        val destinationDir = StorageLocation("/destination", "local")
        return ResolvedTransferItem(
            source = source,
            destinationDir = destinationDir,
            targetLocation = StorageLocation("/destination/$name", "local"),
            originalName = name,
            targetName = name,
            isDirectory = false,
            choice = ConflictChoice.RENAME
        )
    }
}
