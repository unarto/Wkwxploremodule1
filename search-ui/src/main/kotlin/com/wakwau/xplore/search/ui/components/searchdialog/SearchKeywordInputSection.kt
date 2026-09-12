// [Jalur Class/Modul]: search-ui/src/main/kotlin/com/wakwau/xplore/search/ui/components/searchdialog/SearchKeywordInputSection.kt
// [Penjelasan]: Komponen input teks pencarian kata kunci dengan ikon pemicu dialog histori pencarian.
package com.wakwau.xplore.search.ui.components.searchdialog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wakwau.xplore.core.utils.ui.theme.XPloreTheme
import com.wakwau.xplore.search.ui.R

@Composable
fun SearchKeywordInputSection(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = XPloreTheme.colors

    OutlinedTextField(
        value = keyword,
        onValueChange = onKeywordChange,
        label = { Text(stringResource(R.string.label_temukan)) },
        trailingIcon = {
            IconButton(
                onClick = onOpenHistory,
                modifier = Modifier.testTag("search_history_icon_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.cd_search_history),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.treeLineColor.copy(alpha = 0.5f),
            focusedLabelColor = colors.primary,
            unfocusedLabelColor = colors.textSecondary
        ),
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .testTag("search_keyword_input")
    )
}
