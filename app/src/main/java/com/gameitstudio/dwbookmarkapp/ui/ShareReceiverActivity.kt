package com.gameitstudio.dwbookmarkapp.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gameitstudio.dwbookmarkapp.R
import com.gameitstudio.dwbookmarkapp.data.database.BookmarkDatabase
import com.gameitstudio.dwbookmarkapp.data.model.Bookmark
import com.gameitstudio.dwbookmarkapp.repository.BookmarkRepository
import com.gameitstudio.dwbookmarkapp.util.AppScope
import com.gameitstudio.dwbookmarkapp.util.UrlMetadataFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 다른 앱의 공유 시트에서 넘어온 링크를 저장하는 투명 액티비티
 *
 * 동작 방식:
 * - 화면을 띄우지 않고(투명 테마) 저장만 한 뒤 즉시 종료하므로
 *   사용자는 보던 브라우저에 그대로 머무른다.
 * - 저장은 두 단계로 나눈다.
 *   1) 도메인(또는 공유 제목)으로 즉시 DB에 넣는다  -> 네트워크가 느려도 유실되지 않음
 *   2) 메타데이터를 받아 제목과 썸네일을 채워 넣는다 -> 목록에서 자동으로 갱신됨
 * - 액티비티가 곧 사라지므로 코루틴은 AppScope 에서 돌린다.
 */
class ShareReceiverActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShareIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
        finish()
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") {
            toast(getString(R.string.share_failed))
            return
        }

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val url = extractUrl(sharedText)
        if (url == null) {
            toast(getString(R.string.share_no_url))
            return
        }

        // 공유 시트가 제목을 함께 넘겨주는 경우 임시 제목으로 활용한다.
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()?.takeIf { it.isNotEmpty() }

        saveBookmark(url, subject)
    }

    /**
     * 공유받은 텍스트에서 첫 번째 http/https 링크를 뽑아낸다.
     *
     * 공유 시트는 앱마다 형식이 달라 "제목 https://..." 또는 "제목\nhttps://..." 처럼
     * 링크 외 문자열이 섞여 오는 경우가 많다.
     *
     * @return 정규화된 URL. 링크를 찾지 못하면 null
     */
    private fun extractUrl(sharedText: String?): String? {
        val text = sharedText?.trim().orEmpty()
        if (text.isEmpty()) return null

        URL_REGEX.find(text)?.let { return it.value.trimEnd('.', ',', ')', ']', '"', '\'') }

        // 스킴 없이 도메인만 공유된 경우 (예: example.com/page)
        if (BARE_DOMAIN_REGEX.matches(text)) return UrlMetadataFetcher.normalize(text)

        return null
    }

    /**
     * 즉시 저장 후 메타데이터를 뒤이어 채워 넣는다.
     *
     * @param rawUrl         공유받은 주소
     * @param providedTitle  공유 시트가 넘겨준 제목(있을 때만)
     */
    private fun saveBookmark(rawUrl: String, providedTitle: String?) {
        val appContext = applicationContext
        val repository = BookmarkRepository(
            BookmarkDatabase.getDatabase(appContext).bookmarkDao()
        )
        val url = UrlMetadataFetcher.normalize(rawUrl)
        val domain = UrlMetadataFetcher.extractDomain(url)

        AppScope.io.launch {
            try {
                // 이미 저장된 주소면 중복으로 쌓지 않는다.
                if (repository.findByUrl(url) != null) {
                    showToast(appContext.getString(R.string.share_duplicate))
                    return@launch
                }

                // 1단계: 최소 정보로 먼저 저장 (네트워크 실패와 무관하게 보존)
                val id = repository.insertBookmark(
                    Bookmark(
                        url = url,
                        title = providedTitle ?: domain,
                        imageUrl = UrlMetadataFetcher.faviconUrl(domain)
                    )
                )
                if (id <= 0L) {
                    showToast(appContext.getString(R.string.share_failed))
                    return@launch
                }
                showToast(appContext.getString(R.string.share_saved))

                // 2단계: 제목과 썸네일 보완 (목록은 LiveData 라 자동 갱신된다)
                val metadata = UrlMetadataFetcher.fetch(url)
                repository.updateMetadata(id, metadata.title, metadata.imageUrl)

            } catch (e: Exception) {
                Log.e(TAG, "공유 링크 저장 실패: ${e.message}", e)
                showToast(appContext.getString(R.string.share_failed))
            }
        }
    }

    private suspend fun showToast(message: String) = withContext(Dispatchers.Main) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun toast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "ShareReceiver"

        /** 텍스트 안에 섞여 있는 첫 번째 http/https 링크 */
        private val URL_REGEX = Regex("""https?://\S+""")

        /** 스킴 없이 도메인만 온 경우 (example.com, example.com/path) */
        private val BARE_DOMAIN_REGEX =
            Regex("""^[\w-]+(\.[\w-]+)+(/\S*)?$""")
    }
}
