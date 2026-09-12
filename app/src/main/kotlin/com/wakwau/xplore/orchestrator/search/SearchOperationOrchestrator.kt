// [Jalur Class/Modul]: app/src/main/kotlin/com/wakwau/xplore/orchestrator/search/SearchOperationOrchestrator.kt
// [Penjelasan]: Orchestrator operasi pencarian berkas via SearchFilesUseCase, mendukung pembatalan job pencarian dan pembaruan hasil pencarian.

package com.wakwau.xplore.orchestrator.search

import com.wakwau.xplore.core.storage.search.FileSearchQuery
import com.wakwau.xplore.filemanager.event.DualPaneEvent
import com.wakwau.xplore.search.usecase.SearchFilesUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

class SearchOperationOrchestrator(
    private val searchFilesUseCase: SearchFilesUseCase,
    private val dispatch: (DualPaneEvent) -> Unit
) {
    private var activeSearchJob: Job? = null

    suspend fun executeSearch(query: FileSearchQuery) = coroutineScope {
        activeSearchJob?.cancel()

        val keyword = query.keyword.trim()
        if (keyword.isEmpty()) return@coroutineScope

        dispatch(DualPaneEvent.SearchStarted(keyword))

        activeSearchJob = launch {
            try {
                searchFilesUseCase(query)
                    .catch { e ->
                        if (e is CancellationException) {
                            dispatch(DualPaneEvent.SearchCancelled)
                            throw e
                        } else {
                            dispatch(DualPaneEvent.SearchFailed(e.message ?: "Search failed"))
                        }
                    }
                    .onCompletion { cause ->
                        if (cause == null) {
                            dispatch(DualPaneEvent.SearchCompleted)
                        }
                    }
                    .collect { results ->
                        dispatch(DualPaneEvent.SearchResultsUpdated(keyword, results))
                    }
            } catch (e: CancellationException) {
                dispatch(DualPaneEvent.SearchCancelled)
            }
        }
    }

    fun cancelSearch() {
        activeSearchJob?.cancel()
        activeSearchJob = null
        dispatch(DualPaneEvent.SearchCancelled)
    }
}
