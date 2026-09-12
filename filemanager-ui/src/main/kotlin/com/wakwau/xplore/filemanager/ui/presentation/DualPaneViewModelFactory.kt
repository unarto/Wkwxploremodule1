// [Jalur Class/Modul]: filemanager-ui/src/main/kotlin/com/wakwau/xplore/filemanager/ui/presentation/DualPaneViewModelFactory.kt
// [Penjelasan]: Factory penyedia ViewModel yang menginstansiasi adapter DualPaneViewModel dengan menyuntikkan factory DualPaneStateHolder murni dan dependensi presentasi.
package com.wakwau.xplore.filemanager.ui.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.wakwau.xplore.core.storage.preferences.AppPreferencesRepository
import com.wakwau.xplore.filemanager.action.CreateDirectoryOperationHandler
import com.wakwau.xplore.filemanager.action.FileDetailHandler
import com.wakwau.xplore.filemanager.action.PanelRefreshHandler
import com.wakwau.xplore.filemanager.reducer.DualPaneReducer
import com.wakwau.xplore.filemanager.state.DualPaneStateHolder
import com.wakwau.xplore.filemanager.ui.action.PanelNavigationHandler
import com.wakwau.xplore.filemanager.ui.tree.TreeNavigationAdapter
import com.wakwau.xplore.filemanager.usecase.CheckStoragePermissionUseCase
import com.wakwau.xplore.filemanager.usecase.GetStorageVolumesUseCase
import com.wakwau.xplore.filemanager.usecase.LinkStorageUseCase
import com.wakwau.xplore.filemanager.usecase.ToggleShowHiddenFilesUseCase
import kotlinx.coroutines.CoroutineScope

class DualPaneViewModelFactory(
    private val stateHolderFactory: (CoroutineScope) -> DualPaneStateHolder,
    private val treeNavigationAdapter: TreeNavigationAdapter,
    private val navigationHandler: PanelNavigationHandler,
    private val actionDelegate: com.wakwau.xplore.filemanager.ui.action.FileOperationActionDelegate? = null
) : ViewModelProvider.Factory {

    constructor(
        reducer: DualPaneReducer,
        refreshHandler: PanelRefreshHandler,
        navigationHandler: PanelNavigationHandler,
        createDirectoryHandler: CreateDirectoryOperationHandler,
        fileDetailHandler: FileDetailHandler,
        appPreferencesRepository: AppPreferencesRepository,
        treeNavigationAdapter: TreeNavigationAdapter,
        toggleShowHiddenFilesUseCase: ToggleShowHiddenFilesUseCase,
        checkStoragePermissionUseCase: CheckStoragePermissionUseCase,
        getStorageVolumesUseCase: GetStorageVolumesUseCase,
        linkStorageUseCase: LinkStorageUseCase,
        actionDelegate: com.wakwau.xplore.filemanager.ui.action.FileOperationActionDelegate
    ) : this(
        stateHolderFactory = { scope ->
            DualPaneStateHolder(
                scope = scope,
                reducer = reducer,
                refreshHandler = refreshHandler,
                createDirectoryHandler = createDirectoryHandler,
                fileDetailHandler = fileDetailHandler,
                appPreferencesRepository = appPreferencesRepository,
                toggleShowHiddenFilesUseCase = toggleShowHiddenFilesUseCase,
                checkStoragePermissionUseCase = checkStoragePermissionUseCase,
                getStorageVolumesUseCase = getStorageVolumesUseCase,
                linkStorageUseCase = linkStorageUseCase
            )
        },
        treeNavigationAdapter = treeNavigationAdapter,
        navigationHandler = navigationHandler,
        actionDelegate = actionDelegate
    )

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DualPaneViewModel::class.java)) {
            return DualPaneViewModel(
                stateHolderFactory = stateHolderFactory,
                treeNavigationAdapter = treeNavigationAdapter,
                navigationHandler = navigationHandler,
                actionDelegate = actionDelegate
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
