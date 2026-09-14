// [Jalur Class/Modul]: search-ui/src/main/kotlin/com/wakwau/xplore/search/ui/components/searchdialog/SearchOptionsSection.kt
// [Penjelasan]: Komponen dropdown filter target pencarian berkas/folder/semua.
package com.wakwau.xplore.search.ui.components.searchdialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakwau.xplore.core.storage.search.SearchTargetType
import com.wakwau.xplore.core.utils.ui.theme.XPloreTheme
import com.wakwau.xplore.search.ui.R

@Composable
fun SearchOptionsSection(
    searchType: SearchTargetType,
    onSearchTypeChange: (SearchTargetType) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = XPloreTheme.colors
    var expandedTypeMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        // Filter Target (semua / file / folder)
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { expandedTypeMenu = true }
                    .padding(vertical = 4.dp, horizontal = 6.dp)
                    .testTag("search_target_type_dropdown")
            ) {
                Text(
                    text = stringResource(R.string.label_temukan),
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                val targetLabel = when (searchType) {
                    SearchTargetType.ALL -> stringResource(R.string.filter_target_all)
                    SearchTargetType.FILE -> stringResource(R.string.filter_target_file)
                    SearchTargetType.FOLDER -> stringResource(R.string.filter_target_folder)
                }
                Text(
                    text = targetLabel,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = expandedTypeMenu,
                onDismissRequest = { expandedTypeMenu = false }
            ) {
                val targets = listOf(
                    SearchTargetType.FILE to stringResource(R.string.filter_target_file),
                    SearchTargetType.FOLDER to stringResource(R.string.filter_target_folder),
                    SearchTargetType.ALL to stringResource(R.string.filter_target_all)
                )
                targets.forEach { (type, label) ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = searchType == type,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = colors.primary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = label, color = colors.textPrimary)
                            }
                        },
                        onClick = {
                            onSearchTypeChange(type)
                            expandedTypeMenu = false
                        }
                    )
                }
            }
        }
    }
}
