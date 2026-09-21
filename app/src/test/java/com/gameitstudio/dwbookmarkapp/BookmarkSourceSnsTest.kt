package com.gameitstudio.dwbookmarkapp

import com.gameitstudio.dwbookmarkapp.util.BookmarkSource
import com.gameitstudio.dwbookmarkapp.util.UrlDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 새로 추가한 SNS 분류와 표시용 주소 줄이기 검증 */
class BookmarkSourceSnsTest {

    @Test fun snsHostsIncludingShareShorteners() {
        mapOf(
            "https://www.facebook.com/share/p/abc/" to BookmarkSource.FACEBOOK,
            "https://fb.watch/xyz/" to BookmarkSource.FACEBOOK,
            "https://www.tiktok.com/@user/video/123" to BookmarkSource.TIKTOK,
            "https://vt.tiktok.com/ZS123/" to BookmarkSource.TIKTOK,
            "https://x.com/user/status/123" to BookmarkSource.X,
            "https://twitter.com/user/status/123" to BookmarkSource.X,
            "https://t.co/abcd" to BookmarkSource.X,
            "https://www.reddit.com/r/android/comments/abc/" to BookmarkSource.REDDIT,
            "https://redd.it/abc" to BookmarkSource.REDDIT,
            "https://www.pinterest.co.kr/pin/123/" to BookmarkSource.PINTEREST,
            "https://pin.it/abc" to BookmarkSource.PINTEREST,
            "https://www.linkedin.com/posts/abc" to BookmarkSource.LINKEDIN,
            "https://lnkd.in/abc" to BookmarkSource.LINKEDIN,
            "https://bsky.app/profile/a.bsky.social/post/1" to BookmarkSource.BLUESKY
        ).forEach { (url, expected) -> assertEquals(url, expected, BookmarkSource.fromUrl(url)) }
    }

    @Test fun lookalikeHostsStayOther() {
        listOf(
            "https://x.com.evil.example/a",
            "https://notreddit.com/r/a",
            "https://tiktok.evil.example",
            "https://example.com/?u=https://x.com"
        ).forEach { assertEquals(it, BookmarkSource.OTHER, BookmarkSource.fromUrl(it)) }
    }

    @Test fun chipOrderPutsOtherLast() {
        assertEquals(BookmarkSource.OTHER, BookmarkSource.entries.last())
        assertEquals(11, BookmarkSource.entries.size)   // SNS 10개 + 기타
    }

    @Test fun shortUrlDropsSchemeWwwAndQuery() {
        assertEquals("youtube.com/watch", UrlDisplay.short("https://www.youtube.com/watch?v=abc&t=10"))
        assertEquals("naver.com", UrlDisplay.short("https://naver.com/"))
        assertEquals("example.com/a/b", UrlDisplay.short("example.com/a/b"))
    }

    @Test fun shortUrlTruncatesLongPaths() {
        val long = "https://www.example.com/" + "a".repeat(80)
        val shortened = UrlDisplay.short(long)
        assertTrue(shortened.length <= 38)
        assertTrue(shortened.endsWith("…"))
    }
}
