package com.gameitstudio.dwbookmarkapp.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URL

/**
 * URL에서 웹사이트 메타데이터를 추출하는 유틸리티 (싱글톤 object)
 *
 * 이미지 우선순위:
 * 1순위: og:image (Open Graph 이미지 - 가장 풍부한 썸네일)
 * 2순위: Google Favicon API (항상 제공되는 안정적인 폴백)
 *
 * 제목 우선순위:
 * 1순위: og:title (소셜 공유용 제목)
 * 2순위: <title> 태그
 * 3순위: 도메인 이름
 */
object UrlMetadataFetcher {

    /**
     * 메타데이터 결과 데이터 클래스
     * @param title    웹사이트 제목
     * @param imageUrl 대표 이미지 URL
     */
    data class WebMetadata(
        val title: String,
        val imageUrl: String
    )

    /**
     * URL에서 메타데이터 비동기 추출
     * withContext(Dispatchers.IO): 네트워크 IO를 백그라운드 스레드에서 실행
     *
     * @param url 메타데이터를 가져올 웹사이트 URL
     * @return WebMetadata (제목, 이미지 URL)
     */
    suspend fun fetch(url: String): WebMetadata = withContext(Dispatchers.IO) {
        try {
            // Jsoup으로 HTML 문서 파싱
            // userAgent: 일부 사이트의 봇 차단 우회
            // timeout: 8초 초과 시 예외 발생
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                .timeout(8000)
                .get()

            // --- 제목 추출 ---
            // og:title 우선 → <title> 태그 → 도메인 이름 순서로 fallback
            val ogTitle = doc.select("meta[property=og:title]").attr("content")
            val htmlTitle = doc.title()
            val title = when {
                ogTitle.isNotBlank() -> ogTitle
                htmlTitle.isNotBlank() -> htmlTitle
                else -> extractDomain(url)
            }

            // --- 이미지 URL 추출 ---
            val ogImage = doc.select("meta[property=og:image]").attr("content")
            val domain = extractDomain(url)

            // Google Favicon API: 도메인에 맞는 고화질 파비콘 자동 제공
            val faviconUrl = faviconUrl(domain)

            val imageUrl = when {
                // og:image가 절대 경로(http/https)인 경우 바로 사용
                ogImage.isNotBlank() && ogImage.startsWith("http") -> ogImage
                // og:image가 상대 경로인 경우 절대 경로로 변환
                ogImage.isNotBlank() && ogImage.startsWith("/") -> {
                    val baseUrl = URL(url)
                    "${baseUrl.protocol}://${baseUrl.host}$ogImage"
                }
                // og:image 없으면 Google Favicon API 사용
                else -> faviconUrl
            }

            WebMetadata(title = title.trim(), imageUrl = imageUrl)

        } catch (e: Exception) {
            // 네트워크 오류, 타임아웃 등 예외 발생 시 기본값 반환
            val domain = extractDomain(url)
            WebMetadata(
                title = domain,
                imageUrl = faviconUrl(domain)
            )
        }
    }

    /**
     * 스킴이 없는 주소에 https:// 를 보완한다.
     * 입력 다이얼로그와 공유 인텐트가 같은 규칙을 쓰도록 여기에 둔다.
     *
     * @param url 사용자 입력 또는 공유받은 주소
     * @return http/https 스킴이 보장된 URL
     */
    fun normalize(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    /**
     * 도메인에 대응하는 Google 파비콘 주소
     * og:image 가 없을 때의 대표 이미지 폴백으로 사용한다.
     *
     * @param domain extractDomain() 으로 얻은 도메인명
     */
    fun faviconUrl(domain: String): String =
        "https://www.google.com/s2/favicons?domain=$domain&sz=128"

    /**
     * URL에서 도메인명 추출
     * 예: "https://www.google.com/search?q=hi" → "google.com"
     *
     * @param url 전체 URL 문자열
     * @return 도메인명 (파싱 실패 시 원본 URL 반환)
     */
    fun extractDomain(url: String): String {
        return try {
            val host = URL(url).host
            host.removePrefix("www.")
        } catch (e: Exception) {
            url
        }
    }
}