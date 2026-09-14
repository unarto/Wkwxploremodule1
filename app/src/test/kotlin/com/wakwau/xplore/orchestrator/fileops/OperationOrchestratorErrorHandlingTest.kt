// [Jalur Class/Modul]: app/src/test/kotlin/com/wakwau/xplore/orchestrator/fileops/OperationOrchestratorErrorHandlingTest.kt
// [Penjelasan]: Unit test untuk memverifikasi pemisahan CancellationException (OperationCancelled) dan pemetaan exception operasi I/O nyata ke OperationFailed pada Operation Orchestrator.

package com.wakwau.xplore.orchestrator.fileops

import com.wakwau.xplore.core.storage.api.error.StorageErrorMapper
import com.wakwau.xplore.core.storage.model.FileDetailedMetadata
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.operation.BackgroundOperationType
import com.wakwau.xplore.core.storage.operation.BackgroundOperationEvent
import com.wakwau.xplore.core.storage.operation.FileOperationError
import com.wakwau.xplore.core.storage.operation.FileOperationProgress
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import com.wakwau.xplore.filemanager.event.DualPaneEvent
import com.wakwau.xplore.filemanager.state.DualPaneState
import com.wakwau.xplore.filemanager.state.PanelId
import com.wakwau.xplore.filemanager.state.PanelState
import com.wakwau.xplore.fileoperations.client.BackgroundOperationClient
import com.wakwau.xplore.fileoperations.conflict.DetectConflictsUseCase
import com.wakwau.xplore.fileoperations.conflict.ResolveTransferUseCase
import com.wakwau.xplore.fileoperations.copy.CopyFilesUseCase
import com.wakwau.xplore.fileoperations.delete.DeleteFilesUseCase
import com.wakwau.xplore.fileoperations.move.MoveFilesUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

class OperationOrchestratorErrorHandlingTest {

    private val testStorageErrorMapper = object : StorageErrorMapper {
        override fun map(throwable: Throwable): FileOperationError {
            return when (throwable) {
                is FileNotFoundException -> FileOperationError.NOT_FOUND
                is SecurityException -> FileOperationError.ACCESS_DENIED
                is IOException -> {
                    val message = throwable.message ?: ""
                    when {
                        message.contains("ENOSPC", ignoreCase = true) -> FileOperationError.IO_ERROR
                        message.contains("EACCES", ignoreCase = true) -> FileOperationError.ACCESS_DENIED
                        else -> FileOperationError.UNKNOWN
                    }
                }
                else -> FileOperationError.UNKNOWN
            }
        }
    }

    private class TestBackgroundOperationClient : BackgroundOperationClient {
        var errorToThrow: Throwable? = null
        val enqueued = mutableListOf<Triple<BackgroundOperationType, List<StorageLocation>, StorageLocation?>>()

        override fun enqueueOperation(
            type: BackgroundOperationType,
            sources: List<StorageLocation>,
            destination: StorageLocation?
        ): String {
            errorToThrow?.let { throw it }
            enqueued.add(Triple(type, sources, destination))
            return "operation-${enqueued.size}"
        }

        override fun enqueueResolvedOperation(
            type: BackgroundOperationType,
            resolvedItems: List<com.wakwau.xplore.fileoperations.conflict.ResolvedTransferItem>
        ): String {
            errorToThrow?.let { throw it }
            return "resolved-operation"
        }

        override fun cancelOperation() {
            errorToThrow?.let { throw it }
        }

        override fun observeProgress(): Flow<BackgroundOperationEvent> = emptyFlow()
    }

    private lateinit var bgClient: TestBackgroundOperationClient
    private lateinit var copyUseCase: CopyFilesUseCase
    private lateinit var moveUseCase: MoveFilesUseCase
    private lateinit var deleteUseCase: DeleteFilesUseCase
    private lateinit var events: MutableList<DualPaneEvent>
    private lateinit var testState: DualPaneState

    private val testFileItem = FileItem(
        id = "1",
        name = "test.txt",
        location = StorageLocation(path = "/storage/emulated/0/test.txt", rootId = "primary_internal"),
        type = com.wakwau.xplore.core.storage.model.FileType.FILE,
        metadata = com.wakwau.xplore.core.storage.model.FileMetadata(
            size = 100L,
            modifiedTime = 0L,
            createdTime = null,
            isReadable = true,
            isWritable = true,
            isExecutable = false,
            isHidden = false
        )
    )

    private lateinit var detectConflictsUseCase: DetectConflictsUseCase
    private lateinit var resolveTransferUseCase: ResolveTransferUseCase

    @Before
    fun setUp() {
        bgClient = TestBackgroundOperationClient()
        val mockDetector = object : com.wakwau.xplore.fileoperations.conflict.ConflictDetector {
            override suspend fun detectConflicts(sources: List<StorageLocation>, destinationDir: StorageLocation): List<com.wakwau.xplore.fileoperations.conflict.FileConflict> = emptyList()
            override suspend fun getExistingNames(destinationDir: StorageLocation): Set<String> = emptySet()
        }
        val mockResolver = object : com.wakwau.xplore.fileoperations.conflict.ConflictResolver {
            override fun resolveConflict(
                conflict: com.wakwau.xplore.fileoperations.conflict.FileConflict,
                choice: com.wakwau.xplore.fileoperations.conflict.ConflictChoice,
                existingNames: MutableSet<String>
            ): com.wakwau.xplore.fileoperations.conflict.ResolvedTransferItem? = null

            override fun generateUniqueName(
                originalName: String,
                isDirectory: Boolean,
                existingNames: Set<String>
            ): String = originalName

            override fun resolveNonConflictingItem(
                source: StorageLocation,
                destinationDir: StorageLocation,
                isDirectory: Boolean,
                existingNames: MutableSet<String>
            ): com.wakwau.xplore.fileoperations.conflict.ResolvedTransferItem {
                val name = source.path.trimEnd('/').substringAfterLast('/')
                val targetLoc = StorageLocation(destinationDir.path.trimEnd('/') + "/" + name, destinationDir.rootId)
                return com.wakwau.xplore.fileoperations.conflict.ResolvedTransferItem(
                    source = source,
                    destinationDir = destinationDir,
                    targetLocation = targetLoc,
                    originalName = name,
                    targetName = name,
                    isDirectory = isDirectory,
                    choice = com.wakwau.xplore.fileoperations.conflict.ConflictChoice.RENAME
                )
            }
        }
        val mockMetadataReader = object : com.wakwau.xplore.core.storage.metadata.DetailedMetadataReader {
            override suspend fun readDetailedMetadata(location: StorageLocation): FileDetailedMetadata {
                return FileDetailedMetadata(
                    fileName = location.path.substringAfterLast('/'),
                    fullPath = location.path,
                    parentPath = location.path.substringBeforeLast('/'),
                    sizeBytes = 100L,
                    isDirectory = false,
                    lastModifiedTimestamp = 0L,
                    isReadable = true,
                    isWritable = true,
                    isExecutable = false,
                    isHidden = false,
                    posixPermissions = "rwx",
                    mimeType = "text/plain"
                )
            }
        }

        detectConflictsUseCase = DetectConflictsUseCase(mockDetector)
        resolveTransferUseCase = ResolveTransferUseCase(mockDetector, mockResolver, mockMetadataReader)

        copyUseCase = CopyFilesUseCase(bgClient)
        moveUseCase = MoveFilesUseCase(bgClient)
        deleteUseCase = DeleteFilesUseCase(bgClient)
        events = mutableListOf()

        testState = DualPaneState(
            activePanelId = PanelId.LEFT,
            leftPanel = PanelState(
                id = PanelId.LEFT,
                currentLocation = StorageLocation(path = "/storage/emulated/0", rootId = "primary_internal")
            ),
            rightPanel = PanelState(
                id = PanelId.RIGHT,
                currentLocation = StorageLocation(path = "/storage/emulated/0/target", rootId = "primary_internal")
            )
        )
    }

    // --- CopyOperationOrchestrator Tests ---

    @Test
    fun copyOrchestrator_cancellationException_dispatchesOperationCancelled() = runTest {
        bgClient.errorToThrow = CancellationException("Copy user cancelled")
        val orchestrator = CopyOperationOrchestrator(copyUseCase, detectConflictsUseCase, resolveTransferUseCase, testStorageErrorMapper, { events.add(it) })
        try {
            orchestrator.execute(testState, listOf(testFileItem), "/storage/emulated/0/target")
        } catch (e: CancellationException) {}

        assertTrue(events.any { it is DualPaneEvent.OperationCancelled })
        assertTrue(events.none { it is DualPaneEvent.OperationFailed })
    }

    @Test
    fun copyOrchestrator_ioException_dispatchesOperationFailedWithIoError() = runTest {
        bgClient.errorToThrow = IOException("ENOSPC - no space left on device")
        var indexMutationQueued = false
        val orchestrator = CopyOperationOrchestrator(
            copyUseCase, detectConflictsUseCase, resolveTransferUseCase, testStorageErrorMapper,
            { events.add(it) }, onEnqueued = { _, _ -> indexMutationQueued = true }
        )
        orchestrator.execute(testState, listOf(testFileItem), "/storage/emulated/0/target")

        val failedEvent = events.filterIsInstance<DualPaneEvent.OperationFailed>().firstOrNull()
        assertEquals(FileOperationError.IO_ERROR.name, failedEvent?.error)
        assertTrue(events.none { it is DualPaneEvent.OperationCancelled })
        assertTrue(!indexMutationQueued)
    }

    @Test
    fun copyOrchestrator_securityException_dispatchesOperationFailedWithAccessDenied() = runTest {
        bgClient.errorToThrow = SecurityException("Permission denied")
        val orchestrator = CopyOperationOrchestrator(copyUseCase, detectConflictsUseCase, resolveTransferUseCase, testStorageErrorMapper, { events.add(it) })
        orchestrator.execute(testState, listOf(testFileItem), "/storage/emulated/0/target")

        val failedEvent = events.filterIsInstance<DualPaneEvent.OperationFailed>().firstOrNull()
        assertEquals(FileOperationError.ACCESS_DENIED.name, failedEvent?.error)
        assertTrue(events.none { it is DualPaneEvent.OperationCancelled })
    }

    // --- MoveOperationOrchestrator Tests ---

    @Test
    fun moveOrchestrator_cancellationException_dispatchesOperationCancelled() = runTest {
        bgClient.errorToThrow = CancellationException("Move user cancelled")
        val orchestrator = MoveOperationOrchestrator(moveUseCase, detectConflictsUseCase, resolveTransferUseCase, testStorageErrorMapper, { events.add(it) })
        try {
            orchestrator.execute(testState, listOf(testFileItem), "/storage/emulated/0/target")
        } catch (e: CancellationException) {}

        assertTrue(events.any { it is DualPaneEvent.OperationCancelled })
        assertTrue(events.none { it is DualPaneEvent.OperationFailed })
    }

    @Test
    fun moveOrchestrator_ioException_dispatchesOperationFailed() = runTest {
        bgClient.errorToThrow = IOException("ENOSPC - no space left on device")
        var indexMutationQueued = false
        val orchestrator = MoveOperationOrchestrator(
            moveUseCase, detectConflictsUseCase, resolveTransferUseCase, testStorageErrorMapper,
            { events.add(it) }, onEnqueued = { _, _ -> indexMutationQueued = true }
        )
        orchestrator.execute(testState, listOf(testFileItem), "/storage/emulated/0/target")

        val failedEvent = events.filterIsInstance<DualPaneEvent.OperationFailed>().firstOrNull()
        assertEquals(FileOperationError.IO_ERROR.name, failedEvent?.error)
        assertTrue(events.none { it is DualPaneEvent.OperationCancelled })
        assertTrue(!indexMutationQueued)
    }

    // --- DeleteOperationOrchestrator Tests ---

    @Test
    fun deleteOrchestrator_cancellationException_dispatchesOperationCancelled() = runTest {
        bgClient.errorToThrow = CancellationException("Delete user cancelled")
        val orchestrator = DeleteOperationOrchestrator(
            deleteUseCase,
            testStorageErrorMapper,
            dispatch = { events.add(it) }
        )
        try {
            orchestrator.execute(testState, listOf(testFileItem))
        } catch (e: CancellationException) {}

        assertTrue(events.any { it is DualPaneEvent.OperationCancelled })
        assertTrue(events.none { it is DualPaneEvent.OperationFailed })
    }

    @Test
    fun deleteOrchestrator_ioException_dispatchesOperationFailed() = runTest {
        bgClient.errorToThrow = IOException("Disk error")
        var indexMutationQueued = false
        val orchestrator = DeleteOperationOrchestrator(
            deleteUseCase,
            testStorageErrorMapper,
            { events.add(it) },
            onEnqueued = { _, _ -> indexMutationQueued = true }
        )
        orchestrator.execute(testState, listOf(testFileItem))

        val failedEvent = events.filterIsInstance<DualPaneEvent.OperationFailed>().firstOrNull()
        assertEquals(FileOperationError.UNKNOWN.name, failedEvent?.error)
        assertTrue(events.none { it is DualPaneEvent.OperationCancelled })
        assertTrue(!indexMutationQueued)
    }
}
