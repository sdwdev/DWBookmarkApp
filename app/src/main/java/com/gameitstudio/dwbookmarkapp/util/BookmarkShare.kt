package com.gameitstudio.dwbookmarkapp.util
import com.gameitstudio.dwbookmarkapp.data.model.Bookmark

object BookmarkShare {
    fun text(items: List<Bookmark>): String =
        items.joinToString("\n\n") { "${it.title}\n${it.url}" }
}
