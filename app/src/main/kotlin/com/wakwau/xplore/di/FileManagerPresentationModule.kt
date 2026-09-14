// [Jalur Class/Modul]: app/src/main/kotlin/com/wakwau/xplore/di/FileManagerPresentationModule.kt
// [Penjelasan]: Module DI tingkat presentasi yang merakit ViewModelFactory untuk DualPaneViewModel dengan handler yang terikat pada siklus hidup ViewModel, mencegah memory leak dan stale state.
package com.wakwau.xplore.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.wakwau.xplore.core.storage.api.error.StorageErrorMapper
import com.wakwau.xplore.core.storage.preferences.AppPreferencesRepository
import com.wakwau.xplore.filemanager.action.CreateDirectoryOperationHandler
import com.wakwau.xplore.filemanager.action.FileDetailHandler
import com.wakwau.xplore.filemanager.action.PanelRefreshHandler
import com.wakwau.xplore.filemanager.event.DualPaneEvent
import com.wakwau.xplore.filemanager.reducer.DualPaneReducer
import com.wakwau.xplore.filemanager.ui.action.PanelNavigationHandler
import com.wakwau.xplore.filemanager.ui.presentation.DualPaneViewModel
import com.wakwau.xplore.filemanager.ui.presentation.DualPaneViewModelFactory
import com.wakwau.xplore.filemanager.ui.tree.TreeNavigationAdapter
import com.wakwau.xplore.fileoperations.client.BackgroundOperationClient
import com.wakwau.xplore.orchestrator.AppOrchestratorViewModel

class FileManagerPresentationModule(
    private val useCaseModule: FileManagerUseCaseModule,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val backgroundOperationClient: BackgroundOperationClient,
    private val storageErrorMapper: StorageErrorMapper,
    val appOrchestrator: AppOrchestratorViewModel
) {
    val treeNavigationAdapter: TreeNavigationAdapter by lazy {
        TreeNavigationAdapter(
            listDirectoryUseCase = useCaseModule.listDirectoryUseCase,
            appPreferencesRepository = appPreferencesRepository,
            fileTreeItemFactory = useCaseModule.fileTreeItemFactory,
            getParentLocationUseCase = useCaseModule.getParentLocationUseCase
        )
    }

    // [Jalur Class/Modul]: app/src/main/java/com/wakwau/xplore/di/FileManagerPresentationModule.kt
    // [Penjelasan]: Menghasilkan ViewModelProvider.Factory baru di mana handler presentation diinstansiasi secara lokal dan segar per ViewModel tanpa menahan referensi vm::dispatch di Singleton/Application scope.
    fun createViewModelFactory(): ViewModelProvider.Factory {
        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(DualPaneViewModel::class.java)) {
                    // [Jalur Class/Modul]: app/src/main/java/com/wakwau/xplore/di/FileManagerPresentationModule.kt
                    // [Penjelasan]: Dispatch delegate lokal per instance ViewModel untuk memutus siklus dependensi sirkular dan mencegah memory leak ke level Singleton.
                    var dispatchDelegate: ((DualPaneEvent) -> Unit)? = null
                    val dispatch: (DualPaneEvent) -> Unit = { event -> dispatchDelegate?.invoke(event) }

                    val refreshHandler = PanelRefreshHandler(useCaseModule.listDirectoryUseCase, dispatch)
                    val navigationHandler = PanelNavigationHandler(
                        treeNavigationAdapter = treeNavigationAdapter,
                        getParentLocationUseCase = useCaseModule.getParentLocationUseCase,
                        dispatch = dispatch
                    )
                    val createDirectoryHandler = CreateDirectoryOperationHandler(
                        useCaseModule.createDirectoryUseCase,
                        dispatch,
                        appOrchestrator::syncCreatedItem
                    )
                    val fileDetailHandler = FileDetailHandler(
                        getFileDetailedMetadataUseCase = useCaseModule.getFileDetailedMetadataUseCase,
                        computeFileChecksumUseCase = useCaseModule.computeFileChecksumUseCase,
                        dispatch = dispatch
                    )

                    appOrchestrator.setExternalDispatch(dispatch)

                    val baseFactory = DualPaneViewModelFactory(
                        reducer = DualPaneReducer(),
                        refreshHandler = refreshHandler,
                        navigationHandler = navigationHandler,
                        createDirectoryHandler = createDirectoryHandler,
                        fileDetailHandler = fileDetailHandler,
                        appPreferencesRepository = appPreferencesRepository,
                        treeNavigationAdapter = treeNavigationAdapter,
                        toggleShowHiddenFilesUseCase = useCaseModule.toggleShowHiddenFilesUseCase,
                        checkStoragePermissionUseCase = useCaseModule.checkStoragePermissionUseCase,
                        getStorageVolumesUseCase = useCaseModule.getStorageVolumesUseCase,
                        linkStorageUseCase = useCaseModule.linkStorageUseCase,
                        actionDelegate = appOrchestrator
                    )
                    
                    val vm = baseFactory.create(DualPaneViewModel::class.java)
                    dispatchDelegate = vm::dispatch
                    return vm as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
