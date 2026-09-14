// [Jalur Class/Modul]: app/src/main/kotlin/com/wakwau/xplore/di/AppCompositionRoot.kt
// [Penjelasan]: Entry point utama Dependency Injection pada modul :app yang menampung konteks aplikasi dan modul storage inti.

package com.wakwau.xplore.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import com.wakwau.xplore.core.storage.operation.FileOperationProgressDispatcher
import com.wakwau.xplore.core.worker.client.FileCopyServiceClient
import com.wakwau.xplore.core.worker.dispatcher.DefaultFileOperationProgressDispatcher
import com.wakwau.xplore.fileoperations.client.BackgroundOperationClient
import com.wakwau.xplore.orchestrator.AppOrchestratorViewModel
import com.wakwau.xplore.settings.ui.engine.SettingsViewModel

class AppCompositionRoot(val applicationContext: Context) {
    val storageModule: StorageModule by lazy {
        StorageModule(applicationContext)
    }

    internal val fileOpsProgressDispatcherInternal: FileOperationProgressDispatcher by lazy {
        DefaultFileOperationProgressDispatcher()
    }

    internal val backgroundOperationClientInternal: BackgroundOperationClient by lazy {
        FileCopyServiceClient(
            context = applicationContext,
            progressDispatcher = fileOpsProgressDispatcherInternal
        )
    }

    internal val fileOperationsModuleInternal: FileOperationsModule by lazy {
        FileOperationsModule(
            directoryRepository = storageModule.directoryRepository,
            detailedMetadataReader = storageModule.detailedMetadataReader,
            fileRepository = storageModule.fileRepository,
            backgroundOperationClient = backgroundOperationClientInternal
        )
    }

    internal val fileManagerUseCaseModuleInternal: FileManagerUseCaseModule by lazy {
        FileManagerUseCaseModule(
            directoryRepository = storageModule.directoryRepository,
            storageVolumeRepository = storageModule.storageVolumeRepository,
            storagePermissionChecker = storageModule.storagePermissionChecker,
            detailedMetadataReader = storageModule.detailedMetadataReader,
            appPreferencesRepository = storageModule.appPreferencesRepository,
            fileSearchService = storageModule.fileSearchService,
            fileOperationsModule = fileOperationsModuleInternal,
            safPermissionHandler = storageModule.safPermissionHandler,
            fileChecksumReader = storageModule.fileChecksumReader
        )
    }

    internal val appOrchestratorInternal: AppOrchestratorViewModel by lazy {
        AppOrchestratorViewModel(
            useCaseModule = fileManagerUseCaseModuleInternal,
            storageErrorMapper = storageModule.storageErrorMapper,
            backgroundOperationClient = backgroundOperationClientInternal,
            fileIndexSynchronizer = storageModule.fileIndexSynchronizer
        )
    }

    internal val fileManagerPresentationModuleInternal: FileManagerPresentationModule by lazy {
        FileManagerPresentationModule(
            useCaseModule = fileManagerUseCaseModuleInternal,
            appPreferencesRepository = storageModule.appPreferencesRepository,
            backgroundOperationClient = backgroundOperationClientInternal,
            storageErrorMapper = storageModule.storageErrorMapper,
            appOrchestrator = appOrchestratorInternal
        )
    }

    internal val dualPaneViewModelFactoryInternal: ViewModelProvider.Factory by lazy {
        fileManagerPresentationModuleInternal.createViewModelFactory()
    }

    val settingsViewModelFactory: ViewModelProvider.Factory by lazy {
        SettingsViewModel.provideFactory(storageModule.appPreferencesRepository)
    }
}
