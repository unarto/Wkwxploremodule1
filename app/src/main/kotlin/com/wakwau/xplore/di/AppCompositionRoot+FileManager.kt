// [Jalur Class/Modul]: app/src/main/kotlin/com/wakwau/xplore/di/AppCompositionRoot+FileManager.kt
// [Penjelasan]: Extension provider File Manager pada AppCompositionRoot untuk use cases, presentation module, app orchestrator, ViewModelFactory, dan TreeNavigationAdapter.

package com.wakwau.xplore.di

import androidx.lifecycle.ViewModelProvider
import com.wakwau.xplore.filemanager.ui.tree.TreeNavigationAdapter
import com.wakwau.xplore.orchestrator.AppOrchestratorViewModel

val AppCompositionRoot.fileManagerUseCaseModule: FileManagerUseCaseModule
    get() = fileManagerUseCaseModuleInternal

val AppCompositionRoot.fileManagerPresentationModule: FileManagerPresentationModule
    get() = fileManagerPresentationModuleInternal

val AppCompositionRoot.appOrchestrator: AppOrchestratorViewModel
    get() = appOrchestratorInternal

val AppCompositionRoot.dualPaneViewModelFactory: ViewModelProvider.Factory
    get() = dualPaneViewModelFactoryInternal

val AppCompositionRoot.treeNavigationAdapter: TreeNavigationAdapter
    get() = fileManagerPresentationModuleInternal.treeNavigationAdapter
