// [Jalur Class/Modul]: search/src/main/kotlin/com/wakwau/xplore/search/matcher/SearchQueryFilterMatcher.kt
// [Penjelasan]: Evaluator domain murni untuk memeriksa apakah FileItem memenuhi kriteria filter pencarian (kata kunci nama, pola wildcard regex, ekstensi, batas ukuran min/max, dan berkas tersembunyi).
package com.wakwau.xplore.search.matcher

import kotlinx.coroutines.CancellationException
import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.FileType
import com.wakwau.xplore.core.storage.search.FileSearchQuery
import com.wakwau.xplore.core.storage.search.SearchTargetType
import java.util.Locale

class SearchQueryFilterMatcher {

    fun matches(item: FileItem, query: FileSearchQuery): Boolean {
        val keyword = query.keyword.lowercase(Locale.getDefault())
        val itemNameLower = item.name.lowercase(Locale.getDefault())

        if (keyword.isNotEmpty()) {
            val hasWildcard = keyword.contains("*") || keyword.contains("?")
            if (hasWildcard) {
                try {
                    val pattern = buildString {
                        append("^")
                        for (ch in keyword) {
                            when (ch) {
                                '*' -> append(".*")
                                '?' -> append(".")
                                else -> {
                                    if ("\\.[]{}()+^$|<>".contains(ch)) {
                                        append('\\')
                                    }
                                    append(ch)
                                }
                            }
                        }
                        append("$")
                    }
                    val regex = Regex(pattern)
                    if (!regex.matches(itemNameLower)) {
                        return false
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (!itemNameLower.contains(keyword.replace("*", "").replace("?", ""))) {
                        return false
                    }
                }
            } else {
                if (!itemNameLower.contains(keyword)) {
                    return false
                }
            }
        }

        val isDir = item.type == FileType.DIRECTORY
        when (query.searchType) {
            SearchTargetType.FILE -> if (isDir) return false
            SearchTargetType.FOLDER -> if (!isDir) return false
            SearchTargetType.ALL -> { /* allow both */ }
        }

        val ext = query.extension
        if (ext != null) {
            val extLower = ext.lowercase(Locale.getDefault())
            val suffix = if (extLower.startsWith(".")) extLower else ".$extLower"
            if (!itemNameLower.endsWith(suffix)) return false
        }

        val minS = query.minSize
        if (minS != null && item.metadata.size < minS) {
            return false
        }

        val maxS = query.maxSize
        if (maxS != null && item.metadata.size > maxS) {
            return false
        }

        if (!query.showHidden && item.metadata.isHidden) {
            return false
        }

        return true
    }
}
