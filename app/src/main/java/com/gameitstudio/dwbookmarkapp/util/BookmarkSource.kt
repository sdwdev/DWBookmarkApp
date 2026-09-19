package com.gameitstudio.dwbookmarkapp.util

import java.net.URI
import java.util.Locale

/** Derived from the saved URL: existing bookmarks and imported backups work without migration. */
enum class BookmarkSource(val label: String, private vararg val domains: String) {
    THREADS("스레드", "threads.net", "threads.com"),
    INSTAGRAM("인스타그램", "instagram.com"),
    YOUTUBE("유튜브", "youtube.com", "youtu.be", "youtube-nocookie.com"),
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
