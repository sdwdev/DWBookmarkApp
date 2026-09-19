package com.gameitstudio.dwbookmarkapp.util

import com.gameitstudio.dwbookmarkapp.data.model.Bookmark

/** Literal search over saved data, preserving database order. */
object BookmarkSearch {
    fun filter(items: List<Bookmark>, folderId: Long, query: String, source: BookmarkSource? = null): List<Bookmark> {
        val term = query.trim()
        return items.filter { item ->
            val inFolder = when (folderId) {
                -1L -> true
                -2L -> item.folderId == null
                else -> item.folderId == folderId
            }
            inFolder && (source == null || BookmarkSource.fromUrl(item.url) == source) && (term.isEmpty() || item.title.contains(term, ignoreCase = true) ||
                item.url.contains(term, ignoreCase = true) || item.note.contains(term, ignoreCase = true))
        }
    }
}
