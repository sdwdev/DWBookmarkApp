package com.gameitstudio.dwbookmarkapp.data.backup

import com.gameitstudio.dwbookmarkapp.data.model.Bookmark
import com.gameitstudio.dwbookmarkapp.data.model.Folder
import org.json.JSONArray
import org.json.JSONObject

/**
 * 북마크/폴더를 JSON 한 파일로 주고받기 위한 직렬화 유틸
 *
 * Google Drive API 를 쓰지 않고, 사용자가 안드로이드 파일 선택기에서
 * 저장 위치(드라이브 포함)를 직접 고르는 방식이라 로그인이 필요 없다.
 *
 * id 는 내보내되 가져올 때 그대로 쓰지 않는다.
 * 기기마다 자동 증가 id 가 다르므로 폴더는 이름으로 다시 연결한다.
 */
object BookmarkBackup {

    /** 형식이 바뀌면 올린다. 읽을 때 상위 버전 파일을 거부하는 데 쓴다. */
    const val FORMAT_VERSION = 2

    private const val KEY_VERSION = "version"
    private const val KEY_EXPORTED_AT = "exportedAt"
    private const val KEY_APP = "app"
    private const val KEY_FOLDERS = "folders"
    private const val KEY_BOOKMARKS = "bookmarks"

    /** 파일에서 읽어 들인 백업 내용 */
    data class Snapshot(
        val folders: List<Folder>,
        val bookmarks: List<Bookmark>
    )

    /**
     * 백업 JSON 문자열 생성
     *
     * @param folders   내보낼 폴더 전체
     * @param bookmarks 내보낼 북마크 전체
     */
    fun toJson(folders: List<Folder>, bookmarks: List<Bookmark>): String {
        val folderArray = JSONArray()
        folders.forEach { folder ->
            folderArray.put(
                JSONObject().apply {
                    put("id", folder.id)
                    put("name", folder.name)
                    put("sortOrder", folder.sortOrder)
                    put("createdAt", folder.createdAt)
                }
            )
        }

        val bookmarkArray = JSONArray()
        bookmarks.forEach { bookmark ->
            bookmarkArray.put(
                JSONObject().apply {
                    put("url", bookmark.url)
                    put("title", bookmark.title)
                    put("note", bookmark.note)
                    put("titleEdited", bookmark.titleEdited)
                    put("imageUrl", bookmark.imageUrl)
                    put("sortOrder", bookmark.sortOrder)
                    put("createdAt", bookmark.createdAt)
                    // folderId 가 null 이면 키를 넣지 않는다 (미분류)
                    bookmark.folderId?.let { put("folderId", it) }
                }
            )
        }

        return JSONObject().apply {
            put(KEY_VERSION, FORMAT_VERSION)
            put(KEY_EXPORTED_AT, System.currentTimeMillis())
            put(KEY_APP, "com.gameitstudio.dwbookmarkapp")
            put(KEY_FOLDERS, folderArray)
            put(KEY_BOOKMARKS, bookmarkArray)
        }.toString(2)
    }

    /**
     * 백업 JSON 파싱
     *
     * @throws IllegalArgumentException 형식이 맞지 않거나 지원하지 않는 버전일 때
     */
    fun fromJson(text: String): Snapshot {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("백업 파일 형식이 아닙니다")
        }

        val version = root.optInt(KEY_VERSION, 0)
        if (version <= 0) throw IllegalArgumentException("백업 파일 형식이 아닙니다")
        if (version > FORMAT_VERSION) {
            throw IllegalArgumentException("더 새로운 버전의 백업 파일입니다. 앱을 업데이트해주세요")
        }

        val folders = mutableListOf<Folder>()
        root.optJSONArray(KEY_FOLDERS)?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val name = item.optString("name").trim()
                if (name.isEmpty()) continue
                folders.add(
                    Folder(
                        id = item.optLong("id", 0L),
                        name = name,
                        sortOrder = item.optInt("sortOrder", i),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val bookmarks = mutableListOf<Bookmark>()
        root.optJSONArray(KEY_BOOKMARKS)?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val url = item.optString("url").trim()
                if (url.isEmpty()) continue
                bookmarks.add(
                    Bookmark(
                        url = url,
                        title = item.optString("title").ifBlank { url },
                        note = item.optString("note", ""),
                        titleEdited = item.optBoolean("titleEdited", false),
                        imageUrl = item.optString("imageUrl"),
                        sortOrder = item.optInt("sortOrder", i),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                        // 원본 폴더 id. 가져오기 단계에서 이름 기준으로 다시 매핑한다.
                        folderId = if (item.has("folderId")) item.optLong("folderId") else null
                    )
                )
            }
        }

        if (folders.isEmpty() && bookmarks.isEmpty()) {
            throw IllegalArgumentException("백업 파일에 내용이 없습니다")
        }
        return Snapshot(folders, bookmarks)
    }
}
