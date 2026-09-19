package com.gameitstudio.dwbookmarkapp.repository

import androidx.lifecycle.LiveData
import com.gameitstudio.dwbookmarkapp.data.dao.BookmarkDao
import com.gameitstudio.dwbookmarkapp.data.dao.FolderDao
import com.gameitstudio.dwbookmarkapp.data.model.Bookmark
import com.gameitstudio.dwbookmarkapp.data.model.Folder

/**
 * 북마크 레포지토리
 *
 * ViewModel과 데이터 소스(Room DB) 사이의 추상화 계층
 * ViewModel이 데이터 출처(로컬 DB / 네트워크 등)를 알 필요 없도록 분리
 * 향후 서버 API 추가 시 이 클래스만 수정하면 됨
 *
 * folderDao 는 공유 인텐트처럼 폴더가 필요 없는 경로에서 생략할 수 있도록 nullable 이다.
 */
class BookmarkRepository(
    private val bookmarkDao: BookmarkDao,
    private val folderDao: FolderDao? = null
) {

    /**
     * 모든 북마크 LiveData
     * DAO의 LiveData를 그대로 노출 (ViewModel에서 observe)
     */
    val allBookmarks: LiveData<List<Bookmark>> = bookmarkDao.getAllBookmarks()

    /** 전체 폴더 LiveData */
    val allFolders: LiveData<List<Folder>>
        get() = requireFolderDao().getAllFolders()

    /** 특정 폴더의 북마크 */
    fun bookmarksInFolder(folderId: Long): LiveData<List<Bookmark>> =
        bookmarkDao.getBookmarksInFolder(folderId)

    /** 폴더에 속하지 않은 북마크 */
    fun unfiledBookmarks(): LiveData<List<Bookmark>> = bookmarkDao.getUnfiledBookmarks()

    /**
     * 새 북마크 저장
     * 현재 최대 sortOrder + 1을 설정해 항상 맨 뒤에 추가
     *
     * @param bookmark 저장할 북마크 (sortOrder는 여기서 자동 설정)
     * @return 삽입된 row ID (-1이면 실패)
     */
    suspend fun insertBookmark(bookmark: Bookmark): Long {
        val maxOrder = bookmarkDao.getMaxSortOrder()
        val newBookmark = bookmark.copy(sortOrder = maxOrder + 1)
        return bookmarkDao.insertBookmark(newBookmark)
    }

    /**
     * 제목과 대표 이미지만 갱신
     * 공유 인텐트에서 최소 정보로 먼저 저장한 뒤 메타데이터를 채울 때 사용
     */
    suspend fun updateDetails(id: Long, title: String, note: String) {
        require(title.isNotBlank()) { "제목을 입력해주세요" }
        check(bookmarkDao.updateDetails(id, title.trim(), note) == 1) { "북마크가 삭제되었습니다" }
    }

    suspend fun updateMetadata(id: Long, title: String, imageUrl: String) {
        bookmarkDao.updateMetadata(id, title, imageUrl)
    }

    /** 같은 URL이 이미 저장돼 있는지 확인. 없으면 null */
    suspend fun findByUrl(url: String): Bookmark? = bookmarkDao.findByUrl(url)

    /** 북마크를 다른 폴더로 이동. folderId 가 null 이면 미분류로 되돌린다. */
    suspend fun moveToFolder(bookmarkId: Long, folderId: Long?) {
        bookmarkDao.updateFolder(bookmarkId, folderId)
    }

    /**
     * 북마크 삭제
     *
     * @param bookmark 삭제할 북마크 객체
     */
    suspend fun deleteBookmark(bookmark: Bookmark) {
        bookmarkDao.deleteBookmark(bookmark)
    }

    /**
     * 드래그 앤 드롭 후 순서 저장
     *
     * 폴더 필터가 걸린 상태에서도 다른 폴더의 순서를 망가뜨리지 않도록,
     * 지금 보이는 항목들이 원래 차지하던 sortOrder 값들만 모아 새 순서대로 재분배한다.
     * (인덱스 0..n 을 그대로 쓰면 필터 밖 북마크와 값이 겹친다)
     *
     * @param bookmarks 새 순서로 정렬된 (화면에 보이는) 북마크 리스트
     */
    suspend fun updateBookmarksOrder(bookmarks: List<Bookmark>) {
        val slots = bookmarks.map { it.sortOrder }.sorted()
        val updatedList = bookmarks.mapIndexed { index, bookmark ->
            bookmark.copy(sortOrder = slots[index])
        }
        bookmarkDao.updatePositions(updatedList)
    }

    // ------------------------------------------------------------------ 폴더

    /**
     * 새 폴더 생성
     *
     * @return 생성된 폴더 id. 같은 이름이 이미 있으면 null
     */
    suspend fun createFolder(name: String): Long? {
        val dao = requireFolderDao()
        if (dao.countByName(name) > 0) return null
        val order = dao.getMaxSortOrder() + 1
        val id = dao.insertFolder(Folder(name = name, sortOrder = order))
        return if (id > 0) id else null
    }

    /** 폴더 이름 변경. 같은 이름이 이미 있으면 false */
    suspend fun renameFolder(id: Long, name: String): Boolean {
        val dao = requireFolderDao()
        if (dao.countByName(name) > 0) return false
        dao.renameFolder(id, name)
        return true
    }

    /**
     * 폴더 삭제
     * 소속 북마크는 지우지 않고 미분류로 되돌린다.
     */
    suspend fun deleteFolder(id: Long) {
        bookmarkDao.detachFromFolder(id)
        requireFolderDao().deleteFolder(id)
    }

    // -------------------------------------------------------- 내보내기 / 가져오기

    /** 내보내기용 현재 전체 데이터 */
    suspend fun exportSnapshot(): Pair<List<Folder>, List<Bookmark>> =
        requireFolderDao().getAllFoldersOnce() to bookmarkDao.getAllBookmarksOnce()

    /**
     * 백업 파일 내용을 현재 데이터에 병합한다.
     *
     * 기존 데이터를 지우지 않는다. 같은 URL 은 건너뛰고, 폴더는 이름이 같으면 재사용한다.
     * 백업 파일의 id 는 기기마다 달라질 수 있어 신뢰하지 않고 이름으로 다시 연결한다.
     *
     * @return 추가된 북마크 수 to 중복으로 건너뛴 수
     */
    suspend fun importSnapshot(
        folders: List<Folder>,
        bookmarks: List<Bookmark>
    ): Pair<Int, Int> {
        val dao = requireFolderDao()

        // 백업 파일의 폴더 id -> 이 기기의 폴더 id 매핑
        val folderIdMap = mutableMapOf<Long, Long>()
        folders.forEach { folder ->
            val existing = dao.findByName(folder.name)
            val newId = if (existing != null) {
                existing.id
            } else {
                val order = dao.getMaxSortOrder() + 1
                dao.insertFolder(Folder(name = folder.name, sortOrder = order))
            }
            if (newId > 0) folderIdMap[folder.id] = newId
        }

        var added = 0
        var skipped = 0
        bookmarks.forEach { bookmark ->
            if (bookmarkDao.findByUrl(bookmark.url) != null) {
                skipped++
                return@forEach
            }
            val mappedFolderId = bookmark.folderId?.let { folderIdMap[it] }
            val id = insertBookmark(
                bookmark.copy(id = 0, folderId = mappedFolderId)
            )
            if (id > 0) added++ else skipped++
        }
        return added to skipped
    }

    suspend fun applyBulk(ids: List<Long>, delete: Boolean, folderId: Long?) =
        bookmarkDao.applyBulk(ids, delete, folderId)

    private fun requireFolderDao(): FolderDao =
        folderDao ?: error("이 Repository 는 폴더 기능 없이 생성되었습니다")
}
