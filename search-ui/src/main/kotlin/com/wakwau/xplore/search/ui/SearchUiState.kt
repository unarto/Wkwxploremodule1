// [Jalur Class/Modul]: search/src/main/kotlin/com/wakwau/xplore/search/SearchUiState.kt
// [Penjelasan]: State UI pencarian berkas yang menampung visibilitas dialog, status pencarian, pesan galat, riwayat pencarian, dan daftar hasil pencarian.
package com.wakwau.xplore.search.ui

import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.StorageLocation

data class SearchUiState(
    val isSearchDialogOpen: Boolean = false,
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val results: List<FileItem> = emptyList(),
    val hasSearched: Boolean = false,
    val searchScope: StorageLocation? = null,
    val searchHistory: List<String> = emptyList()
)
