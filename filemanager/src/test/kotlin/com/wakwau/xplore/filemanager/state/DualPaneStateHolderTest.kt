// [Jalur Class/Modul]: filemanager/src/test/kotlin/com/wakwau/xplore/filemanager/state/DualPaneStateHolderTest.kt
// [Penjelasan]: Pengujian unit pure JVM untuk DualPaneStateHolder tanpa ketergantungan framework AndroidX, UI, fileoperations, atau search.
package com.wakwau.xplore.filemanager.state

import com.wakwau.xplore.core.storage.checksum.FileChecksumReader
import com.wakwau.xplore.core.storage.metadata.DetailedMetadataReader
import com.wakwau.xplore.core.storage.model.FileChecksum
import com.wakwau.xplore.core.storage.model.FileDetailedMetadata
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.model.StorageVolumeItem
import com.wakwau.xplore.core.storage.operation.FileOperationResult
import com.wakwau.xplore.core.storage.permission.SafPermissionHandler
import com.wakwau.xplore.core.storage.permission.StoragePermissionChecker
import com.wakwau.xplore.core.storage.permission.StoragePermissionType
import com.wakwau.xplore.core.storage.preferences.AppLanguage
import com.wakwau.xplore.core.storage.preferences.AppPreferencesRepository
import com.wakwau.xplore.core.storage.preferences.AppThemeMode
import com.wakwau.xplore.core.storage.preferences.FileLayoutMode
import com.wakwau.xplore.core.storage.preferences.FilePreferencesState
import com.wakwau.xplore.core.storage.preferences.FileSortDirection
import com.wakwau.xplore.core.storage.preferences.FileSortOrder
import com.wakwau.xplore.core.storage.preferences.FileSystemAccessMode
import com.wakwau.xplore.core.storage.preferences.SettingsState
import com.wakwau.xplore.core.storage.repository.DirectoryRepository
import com.wakwau.xplore.core.storage.repository.StorageVolumeRepository
import com.wakwau.xplore.filemanager.action.CreateDirectoryOperationHandler
import com.wakwau.xplore.filemanager.action.FileDetailHandler
import com.wakwau.xplore.filemanager.action.PanelRefreshHandler
import com.wakwau.xplore.filemanager.event.DualPaneEvent
import com.wakwau.xplore.filemanager.reducer.DualPaneReducer
import com.wakwau.xplore.filemanager.usecase.CheckStoragePermissionUseCase
import com.wakwau.xplore.filemanager.usecase.ComputeFileChecksumUseCase
import com.wakwau.xplore.filemanager.usecase.CreateDirectoryUseCase
import com.wakwau.xplore.filemanager.usecase.GetFileDetailedMetadataUseCase
import com.wakwau.xplore.filemanager.usecase.GetStorageVolumesUseCase
import com.wakwau.xplore.filemanager.usecase.LinkStorageUseCase
import com.wakwau.xplore.filemanager.usecase.ListDirectoryUseCase
import com.wakwau.xplore.filemanager.usecase.ToggleShowHiddenFilesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DualPaneStateHolderTest {

    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val fakePreferencesRepository = object : AppPreferencesRepository {
        private val _prefs = MutableStateFlow(FilePreferencesState())
        override val preferencesState: StateFlow<FilePreferencesState> = _prefs
        override val settingsState: StateFlow<SettingsState> = MutableStateFlow(SettingsState())
        override val searchHistoryState: StateFlow<List<String>> = MutableStateFlow(emptyList())
        override suspend fun setSortOrder(sortOrder: FileSortOrder) {}
        override suspend fun setSortDirection(sortDirection: FileSortDirection) {}
        override suspend fun setLayoutMode(layoutMode: FileLayoutMode) {}
        override suspend fun setShowHiddenFiles(showHiddenFiles: Boolean) {}
        override suspend fun setLastVisitedPath(lastVisitedPath: String) {}
        override suspend fun setThemeMode(mode: AppThemeMode) {}
        override suspend fun setLanguage(language: AppLanguage) {}
        override suspend fun setFileSystemAccessMode(mode: FileSystemAccessMode) {}
        override suspend fun setRootReadOnly(isReadOnly: Boolean) {}
        override suspend fun addSearchHistory(keyword: String) {}
        override suspend fun clearSearchHistory() {}
    }

    private val fakeDirectoryRepository = object : DirectoryRepository {
        override suspend fun list(location: StorageLocation, showHidden: Boolean): FileOperationResult<List<FileItem>> {
            return FileOperationResult.Success(emptyList())
        }
        override suspend fun create(location: StorageLocation, name: String): FileOperationResult<FileItem> {
            return FileOperationResult.Failure(com.wakwau.xplore.core.storage.operation.FileOperationError.UNKNOWN)
        }
    }

    private val fakeMetadataReader = object : DetailedMetadataReader {
        override suspend fun readDetailedMetadata(location: StorageLocation): FileDetailedMetadata {
            return FileDetailedMetadata(
                fileName = "test.txt",
                fullPath = location.path,
                parentPath = "",
                sizeBytes = 0L,
                isDirectory = false,
                lastModifiedTimestamp = 0L,
                isReadable = true,
                isWritable = true,
                isExecutable = false,
                isHidden = false,
                posixPermissions = "rw-",
                mimeType = "text/plain"
            )
        }
    }

    private val fakeChecksumReader = object : FileChecksumReader {
        override suspend fun calculateChecksum(location: StorageLocation): FileChecksum {
            return FileChecksum("md5", "sha1", "sha256")
        }
    }

    private val fakePermissionChecker = object : StoragePermissionChecker {
        override fun hasAllFilesAccess(): Boolean = true
        override fun shouldRequestAllFilesAccess(targetPath: String): Boolean = false
        override fun hasAccess(): Boolean = true
        override fun getRequiredPermissionType(): StoragePermissionType = StoragePermissionType.READ_WRITE_STORAGE
    }

    private val fakeVolumeRepository = object : StorageVolumeRepository {
        override fun getVolumes(): Flow<List<StorageVolumeItem>> = emptyFlow()
        override suspend fun refreshVolumes() {}
        override fun close() {}
    }

    private val fakeSafHandler = object : SafPermissionHandler {
        override fun takePersistableUriPermission(uriString: String) {}
        override fun releasePersistableUriPermission(uriString: String) {}
        override fun hasPersistedPermission(uriOrPath: String): Boolean = true
    }

    private lateinit var stateHolder: DualPaneStateHolder

    @Before
    fun setUp() {
        stateHolder = DualPaneStateHolder(
            scope = testScope,
            reducer = DualPaneReducer(),
            refreshHandler = PanelRefreshHandler(
                listDirectoryUseCase = ListDirectoryUseCase(fakeDirectoryRepository, fakePreferencesRepository),
                dispatch = { stateHolder.dispatch(it) }
            ),
            createDirectoryHandler = CreateDirectoryOperationHandler(
                createDirectoryUseCase = CreateDirectoryUseCase(fakeDirectoryRepository),
                dispatch = { stateHolder.dispatch(it) }
            ),
            fileDetailHandler = FileDetailHandler(
                getFileDetailedMetadataUseCase = GetFileDetailedMetadataUseCase(fakeMetadataReader),
                computeFileChecksumUseCase = ComputeFileChecksumUseCase(fakeChecksumReader),
                dispatch = { stateHolder.dispatch(it) }
            ),
            appPreferencesRepository = fakePreferencesRepository,
            toggleShowHiddenFilesUseCase = ToggleShowHiddenFilesUseCase(fakePreferencesRepository),
            checkStoragePermissionUseCase = CheckStoragePermissionUseCase(fakePermissionChecker),
            getStorageVolumesUseCase = GetStorageVolumesUseCase(fakeVolumeRepository),
            linkStorageUseCase = LinkStorageUseCase(fakeSafHandler)
        )
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun `initial state has correct permission and default panel`() {
        val state = stateHolder.state.value
        assertTrue(state.hasPermission)
        assertEquals(PanelId.LEFT, state.activePanelId)
    }

    @Test
    fun `dispatch SetActivePanel updates activePanelId in pure state holder`() {
        stateHolder.dispatch(DualPaneEvent.SetActivePanel(PanelId.RIGHT))
        assertEquals(PanelId.RIGHT, stateHolder.state.value.activePanelId)

        stateHolder.dispatch(DualPaneEvent.SetActivePanel(PanelId.LEFT))
        assertEquals(PanelId.LEFT, stateHolder.state.value.activePanelId)
    }

    @Test
    fun `event listener receives dispatched events`() {
        var receivedEvent: DualPaneEvent? = null
        stateHolder.setEventListener { receivedEvent = it }

        val event = DualPaneEvent.SetActivePanel(PanelId.RIGHT)
        stateHolder.dispatch(event)

        assertNotNull(receivedEvent)
        assertEquals(event, receivedEvent)
    }
}
