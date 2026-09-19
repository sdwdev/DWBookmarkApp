package com.gameitstudio.dwbookmarkapp

import com.gameitstudio.dwbookmarkapp.util.BookmarkSource
import com.gameitstudio.dwbookmarkapp.util.BookmarkSearch
import com.gameitstudio.dwbookmarkapp.data.model.Bookmark
import org.junit.Assert.*
import org.junit.Test

class BookmarkSourceTest {
    @Test fun officialHostsAndSharedVariants() {
        mapOf(
            "https://www.threads.net/@person/post/123" to BookmarkSource.THREADS,
            "https://www.threads.com/@person/post/123?x=1" to BookmarkSource.THREADS,
            "https://www.instagram.com/reel/123/?igsh=x" to BookmarkSource.INSTAGRAM,
            "instagram.com/p/abc" to BookmarkSource.INSTAGRAM,
            "https://youtu.be/123?si=x" to BookmarkSource.YOUTUBE,
            "https://m.youtube.com/shorts/123" to BookmarkSource.YOUTUBE,
            "HTTPS://WWW.YOUTUBE.COM/watch?v=x" to BookmarkSource.YOUTUBE,
            "//www.youtube.com/live/123" to BookmarkSource.YOUTUBE
        ).forEach { (url, expected) -> assertEquals(url, expected, BookmarkSource.fromUrl(url)) }
    }
    @Test fun unrelatedMalformedAndLookalikeHostsAreOther() {
        listOf("", "not a URL", "https://example.com/?url=https://youtube.com",
            "https://youtube.com.evil.example", "https://notinstagram.com",
            "https://instagram.com@evil.example/", "file://youtube.com/video", "https://bit.ly/abc")
            .forEach { assertEquals(it, BookmarkSource.OTHER, BookmarkSource.fromUrl(it)) }
    }
    @Test fun sourceFolderAndSearchIntersectWithoutChangingRecords() {
        val items = listOf(
            Bookmark(id=1, url="https://youtu.be/a", title="영상", folderId=10, note="학습"),
            Bookmark(id=2, url="https://youtube.com/shorts/b", title="다른 영상", folderId=20),
            Bookmark(id=3, url="https://instagram.com/reel/c", title="영상", folderId=10))
        assertEquals(listOf(items[0]), BookmarkSearch.filter(items, 10, "학습", BookmarkSource.YOUTUBE))
        assertEquals(listOf(items[0], items[1]), BookmarkSearch.filter(items, -1, "", BookmarkSource.YOUTUBE))
        assertEquals(items, BookmarkSearch.filter(items, -1, ""))
        assertTrue(BookmarkSearch.filter(items, -1, "", BookmarkSource.THREADS).isEmpty())
    }
}
