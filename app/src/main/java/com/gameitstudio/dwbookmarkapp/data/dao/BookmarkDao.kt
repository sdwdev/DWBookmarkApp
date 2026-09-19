package com.gameitstudio.dwbookmarkapp.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.gameitstudio.dwbookmarkapp.data.model.Bookmark

/**
 * 북마크 데이터 접근 객체 (DAO)
 * Room이 이 인터페이스를 기반으로 SQL 구현을 자동 생성함
 */
@Dao
interface BookmarkDao {

    /**
     * 정렬 순서(sortOrder) 기준으로 모든 북마크 조회
     * LiveData 반환: DB 변경 시 UI가 자동으로 갱신됨
     */
    @Query("SELECT * FROM bookmarks ORDER BY sortOrder ASC")
    fun getAllBookmarks(): LiveData<List<Bookmark>>

    /**
     * 새 북마크 삽입
     * IGNORE 전략: 동일한 id 충돌 시 무시
     * @return 삽입된 row ID (-1 이면 실패)
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookmark(bookmark: Bookmark): Long

    /**
     * 북마크 삭제 (id 기준으로 매핑)
     */
    @Delete
    suspend fun deleteBookmark(bookmark: Bookmark)

    /**
     * 단일 북마크 업데이트
     */
    @Update
    suspend fun updateBookmark(bookmark: Bookmark)

    /**
     * 여러 북마크 일괄 업데이트 (드래그 후 순서 저장 시 사용)
     */
    @Update
    suspend fun updateBookmarks(bookmarks: List<Bookmark>)

    /**
     * 내보내기용 전체 조회 (LiveData 가 아닌 단발성 조회)
     */
    @Query("SELECT * FROM bookmarks ORDER BY sortOrder ASC")
    suspend fun getAllBookmarksOnce(): List<Bookmark>

    /**
     * 특정 폴더에 속한 북마크만 조회
     * folderId 가 null 인 항목은 '미분류'로 별도 조회한다.
     */
    @Query("SELECT * FROM bookmarks WHERE folderId = :folderId ORDER BY sortOrder ASC")
    fun getBookmarksInFolder(folderId: Long): LiveData<List<Bookmark>>

    /** 폴더에 속하지 않은(미분류) 북마크 조회 */
    @Query("SELECT * FROM bookmarks WHERE folderId IS NULL ORDER BY sortOrder ASC")
    fun getUnfiledBookmarks(): LiveData<List<Bookmark>>

    /** 북마크의 소속 폴더 변경. null 이면 미분류로 되돌린다. */
    @Query("UPDATE bookmarks SET folderId = :folderId WHERE id = :id")
    suspend fun updateFolder(id: Long, folderId: Long?)

    /** 폴더 삭제 시 소속 북마크를 미분류로 되돌린다 (북마크는 지우지 않는다) */
    @Query("UPDATE bookmarks SET folderId = NULL WHERE folderId = :folderId")
    suspend fun detachFromFolder(folderId: Long)

    /**
     * 제목과 대표 이미지만 갱신
     * 공유 인텐트로 먼저 저장한 뒤 메타데이터를 뒤늦게 채워 넣을 때 사용
     */
    @Query("UPDATE bookmarks SET title = CASE WHEN titleEdited = 0 THEN :title ELSE title END, imageUrl = :imageUrl WHERE id = :id")
    suspend fun updateMetadata(id: Long, title: String, imageUrl: String)

    @Query("UPDATE bookmarks SET title = :title, note = :note, titleEdited = 1 WHERE id = :id")
    suspend fun updateDetails(id: Long, title: String, note: String): Int

    @Query("UPDATE bookmarks SET sortOrder = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Transaction
    suspend fun updatePositions(bookmarks: List<Bookmark>) {
        bookmarks.forEach { updatePosition(it.id, it.sortOrder) }
    }

    @Query("DELETE FROM bookmarks WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Query("UPDATE bookmarks SET folderId = :folderId WHERE id IN (:ids)")
    suspend fun moveIds(ids: List<Long>, folderId: Long?)

    @Query("SELECT COUNT(*) FROM folders WHERE id = :id")
    suspend fun folderExists(id: Long): Int

    @Transaction
    suspend fun applyBulk(ids: List<Long>, delete: Boolean, folderId: Long?) {
        if (!delete && folderId != null) check(folderExists(folderId) == 1) { "폴더가 삭제되었습니다" }
        ids.distinct().chunked(500).forEach {
            if (delete) deleteIds(it) else moveIds(it, folderId)
        }
    }

    /**
     * 같은 URL의 북마크가 이미 있는지 조회 (중복 저장 방지)
     */
    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): Bookmark?

    /**
     * 현재 최대 sortOrder 값 조회
     * 새 북마크를 맨 뒤에 배치하기 위해 사용
     * COALESCE: 북마크가 하나도 없을 때 -1 반환
     */
    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM bookmarks")
    suspend fun getMaxSortOrder(): Int
}