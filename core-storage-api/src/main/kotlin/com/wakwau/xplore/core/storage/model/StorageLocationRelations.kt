package com.wakwau.xplore.core.storage.model

import java.net.URI
import com.wakwau.xplore.core.storage.constant.StorageConstants

/** Segment-based comparison, including encoded SAF document IDs; no filesystem I/O. */
fun StorageLocation.isSameOrDescendantOf(parent: StorageLocation): Boolean {
    if (rootId.isNotEmpty() && parent.rootId.isNotEmpty() && rootId != parent.rootId) return false
    fun parts(location: StorageLocation): Pair<String, List<String>> {
        val uri = if (location.path.startsWith(StorageConstants.CONTENT_SCHEME_PREFIX)) URI(location.path.substringBefore('#')) else null
        val path = if (uri != null) {
            val decoded = uri.path.orEmpty()
            decoded.substringAfter("/document/", decoded.substringAfter("/tree/", decoded)) +
                (location.path.substringAfter('#', "").takeIf { it.isNotEmpty() }?.let { "/$it" } ?: "")
        } else location.path
        val segments = mutableListOf<String>()
        for (part in path.split('/')) when (part) {
            "", "." -> Unit
            ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
            else -> segments.add(part)
        }
        return (uri?.scheme.orEmpty() + ":" + uri?.authority.orEmpty()) to segments
    }
    val (space, child) = parts(this)
    val (parentSpace, ancestor) = parts(parent)
    return space == parentSpace && child.size >= ancestor.size && child.take(ancestor.size) == ancestor
}
