// [Jalur Class/Modul]: search-ui/src/main/kotlin/com/wakwau/xplore/search/ui/components/searchdialog/SearchResultsListSection.kt
// [Penjelasan]: Komponen tampilan daftar berkas hasil pencarian dengan LazyColumn dan penanganan status kosong/sedang mencari.
package com.wakwau.xplore.search.ui.components.searchdialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.utils.ui.theme.XPloreTheme

@Composable
fun SearchResultsListSection(
    results: List<FileItem>,
    isSearching: Boolean,
    onFileClick: (FileItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = XPloreTheme.colors

    if (isSearching && results.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = colors.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.testTag("search_progress_indicator")
            )
        }
    } else if (results.isNotEmpty()) {
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .testTag("search_results_list")
        ) {
            items(results, key = { it.location.path }) { item ->
                FileSearchResultItem(
                    item = item,
                    onClick = { onFileClick(item) }
                )
                HorizontalDivider(
                    color = colors.treeLineColor.copy(alpha = 0.2f),
                    thickness = 0.5.dp
                )
            }
        }
    }
}
