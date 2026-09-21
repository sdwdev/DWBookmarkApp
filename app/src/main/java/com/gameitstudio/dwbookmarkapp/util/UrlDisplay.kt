package com.gameitstudio.dwbookmarkapp.util

import java.net.URI
import java.util.Locale

/** 목록에서 링크를 짧게 보여주기 위한 표시용 변환. 저장된 원본 URL은 건드리지 않는다. */
object UrlDisplay {

    /**
     * 화면 표시용으로 줄인 주소.
     *
     * 예) https://www.youtube.com/watch?v=abcdefghijk&t=10  ->  youtube.com/watch…
     *
     * scheme(http/https), www., 쿼리스트링을 떼고 남은 경로가 길면 뒤를 줄임표로 자른다.
     * 파싱에 실패하면(사용자가 직접 넣은 이상한 문자열 등) 원본을 길이만 잘라서 돌려준다.
     */
    fun short(url: String, maxLength: Int = 38): String {
        val text = url.trim()
        val display = try {
            val normalized = when {
                text.startsWith("//") -> "https:$text"
                text.contains("://") -> text
                else -> "https://$text"
            }
            val uri = URI(normalized)
            val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.")
            if (host.isNullOrEmpty()) text
            else host + (uri.path?.takeIf { it.isNotEmpty() && it != "/" } ?: "")
        } catch (_: Exception) {
            text
        }
        return if (display.length <= maxLength) display else display.take(maxLength - 1) + "…"
    }
}
