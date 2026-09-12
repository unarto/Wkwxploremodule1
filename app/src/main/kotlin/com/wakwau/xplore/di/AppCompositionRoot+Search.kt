// [Jalur Class/Modul]: app/src/main/kotlin/com/wakwau/xplore/di/AppCompositionRoot+Search.kt
// [Penjelasan]: Extension provider Search pada AppCompositionRoot untuk SearchOperationOrchestrator.

package com.wakwau.xplore.di

import com.wakwau.xplore.filemanager.event.DualPaneEvent
import com.wakwau.xplore.orchestrator.search.SearchOperationOrchestrator

fun AppCompositionRoot.createSearchOrchestrator(
    dispatch: (DualPaneEvent) -> Unit
): SearchOperationOrchestrator = SearchOperationOrchestrator(
    searchFilesUseCase = fileManagerUseCaseModule.searchFilesUseCase,
    dispatch = dispatch
)
