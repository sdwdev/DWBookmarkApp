package com.gameitstudio.dwbookmarkapp.util

import java.net.URI
import java.util.Locale

/**
 * Derived from the saved URL: existing bookmarks and imported backups work without migration.
 *
 * 전 세계 이용자 수 상위 SNS 중 '링크를 공유해 저장할 일이 많은' 곳을 고른다.
 * 왓츠앱·위챗·텔레그램 같은 메신저는 이용자 수는 많지만 저장할 공개 링크가 드물어 제외했다.
 * 공유 시 쓰이는 단축 도메인(youtu.be, t.co 등)도 같은 분류로 묶는다.
 */
enum class BookmarkSource(val label: String, private vararg val domains: String) {
    YOUTUBE("유튜브", "youtube.com", "youtu.be", "youtube-nocookie.com"),
    INSTAGRAM("인스타그램", "instagram.com", "instagr.am"),
    FACEBOOK("페이스북", "facebook.com", "fb.com", "fb.watch", "fb.me"),
    TIKTOK("틱톡", "tiktok.com", "douyin.com"),
    X("X (트위터)", "x.com", "twitter.com", "t.co"),
    THREADS("스레드", "threads.net", "threads.com"),
    REDDIT("레딧", "reddit.com", "redd.it"),
    PINTEREST("핀터레스트", "pinterest.com", "pinterest.co.kr", "pin.it"),
    LINKEDIN("링크드인", "linkedin.com", "lnkd.in"),
    BLUESKY("블루스카이", "bsky.app", "bsky.social"),
    OTHER("기타 웹사이트");

    companion object {
        fun fromUrl(url: String): BookmarkSource {
            val text = url.trim()
            val host = try {
                val normalized = when {
                    text.startsWith("//") -> "https:$text"
                    text.contains("://") -> text
                    else -> "https://$text"
                }
                val uri = URI(normalized)
                if (uri.scheme.lowercase(Locale.ROOT) !in setOf("http", "https")) return OTHER
                uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return OTHER
            } catch (_: Exception) { return OTHER }
            return entries.firstOrNull { source ->
                source.domains.any { domain -> host == domain || host.endsWith(".$domain") }
            } ?: OTHER
        }
    }
}
