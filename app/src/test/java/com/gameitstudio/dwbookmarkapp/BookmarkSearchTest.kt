package com.gameitstudio.dwbookmarkapp

import com.gameitstudio.dwbookmarkapp.data.model.Bookmark
import com.gameitstudio.dwbookmarkapp.util.BookmarkSearch
import org.junit.Assert.*
import org.junit.Test

class BookmarkSearchTest {
    private val items = listOf(
        Bookmark(id = 1, title = "안드로이드 학습", url = "https://developer.android.com", folderId = 10),
        Bookmark(id = 2, title = "GitHub", url = "https://github.com/android", folderId = 20),
        Bookmark(id = 3, title = "할인 50%_sale", url = "https://example.com", folderId = null)
    )
    @Test fun titleAndUrlAreCaseInsensitive() {
        assertEquals(listOf(1L, 2L), BookmarkSearch.filter(items, -1, "ANDROID").map { it.id })
        assertEquals(listOf(1L), BookmarkSearch.filter(items, -1, " 학습 ").map { it.id })
    }
    @Test fun folderAndQueryMustBothMatch() {
        assertEquals(listOf(2L), BookmarkSearch.filter(items, 20, "android").map { it.id })
        assertTrue(BookmarkSearch.filter(items, -2, "android").isEmpty())
        assertEquals(listOf(3L), BookmarkSearch.filter(items, -2, "example").map { it.id })
    }
    @Test fun clearingQueryRestoresFolderInOriginalOrder() {
        assertEquals(items, BookmarkSearch.filter(items, -1, "  "))
        assertEquals(listOf(items[0]), BookmarkSearch.filter(items, 10, ""))
    }
    @Test fun punctuationIsLiteralAndNoMatchIsEmpty() {
        assertEquals(listOf(items[2]), BookmarkSearch.filter(items, -1, "%_"))
        assertTrue(BookmarkSearch.filter(items, -1, "[unknown]").isEmpty())
        assertEquals(3, items.size)
    }
}
