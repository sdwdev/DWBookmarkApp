package com.gameitstudio.dwbookmarkapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.SavedStateHandle
import com.gameitstudio.dwbookmarkapp.util.BookmarkSearch
import androidx.lifecycle.viewModelScope
import com.gameitstudio.dwbookmarkapp.data.database.BookmarkDatabase
import com.gameitstudio.dwbookmarkapp.data.model.Bookmark
import com.gameitstudio.dwbookmarkapp.data.model.Folder
import com.gameitstudio.dwbookmarkapp.repository.BookmarkRepository
import com.gameitstudio.dwbookmarkapp.util.UrlMetadataFetcher
import kotlinx.coroutines.launch

/**
 * 북마크 ViewModel
 *
 * AndroidViewModel 사용: Application Context가 필요하기 때문 (Room DB 접근)
 * 화면 회전 등 구성 변경(Configuration Change) 시에도 데이터 유지됨
 * viewModelScope: ViewModel이 소멸될 때 자동으로 코루틴 취소
 */
class BookmarkViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {

    private val repository: BookmarkRepository

    /** 전체 폴더 목록 (칩 표시용) */
    val folders: LiveData<List<Folder>>

    /** 현재 선택된 폴더. FOLDER_ALL / FOLDER_UNFILED / 실제 폴더 id */
    private val _selectedFolderId = savedState.getLiveData("selectedFolderId", FOLDER_ALL)
    val selectedFolderId: LiveData<Long> = _selectedFolderId

    private val _searchQuery = savedState.getLiveData("searchQuery", "")
    val searchQuery: LiveData<String> = _searchQuery

    private val _selectedSource = savedState.getLiveData("selectedSource", "")
    val selectedSource: LiveData<String> = _selectedSource
    fun selectSource(source: String) {
        if (bulkBusy.value == true || _selectedSource.value == source) return
        endSelection()
        _selectedSource.value = source
    }
    fun sourceFilter() = com.gameitstudio.dwbookmarkapp.util.BookmarkSource.entries
        .firstOrNull { it.name == _selectedSource.value }

    val selecting = savedState.getLiveData("selecting", false)
    val selectedIds = savedState.getLiveData("selectedIds", longArrayOf())
    val bulkBusy = MutableLiveData(false)

    fun startSelection() { selecting.value = true }
    fun endSelection() {
        if (bulkBusy.value == true) return
        selectedIds.value = longArrayOf()
        selecting.value = false
    }
    fun toggleSelection(id: Long) {
        if (bulkBusy.value == true) return
        val ids = (selectedIds.value ?: longArrayOf()).toMutableSet()
        if (!ids.add(id)) ids.remove(id)
        selectedIds.value = ids.toLongArray()
    }
    fun selectAllVisible() {
        if (bulkBusy.value == true) return
        val visible = bookmarks.value.orEmpty().map { it.id }.toSet()
        selectedIds.value = if (visible == (selectedIds.value ?: longArrayOf()).toSet()) longArrayOf()
            else visible.toLongArray()
    }
    fun selectedBookmarks(): List<Bookmark> {
        val ids = (selectedIds.value ?: longArrayOf()).toSet()
        return bookmarks.value.orEmpty().filter { it.id in ids }
    }
    fun applyBulk(ids: List<Long>, delete: Boolean, folderId: Long? = null) {
        if (bulkBusy.value == true || ids.isEmpty()) return
        bulkBusy.value = true
        viewModelScope.launch {
            try {
                repository.applyBulk(ids, delete, folderId)
                bulkBusy.value = false
                endSelection()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = "일괄 처리에 실패했습니다. 다시 시도해주세요."
            } finally { bulkBusy.value = false }
        }
    }

    fun setSearchQuery(query: String) {
        if (_searchQuery.value != query) { endSelection(); _searchQuery.value = query }
    }

    /** 선택된 폴더와 검색어에 맞춰 걸러진 북마크 목록 */
    val bookmarks: LiveData<List<Bookmark>>

    // 로딩 상태 (메타데이터 fetch 중 FAB 비활성화에 사용)
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // 에러/안내 메시지 (Toast 표시 후 null로 초기화)
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        // ViewModel 초기화 시 DB → Repository 설정
        val database = BookmarkDatabase.getDatabase(application)
        repository = BookmarkRepository(database.bookmarkDao(), database.folderDao())
        folders = repository.allFolders

        bookmarks = MediatorLiveData<List<Bookmark>>().apply {
            fun refresh() {
                value = BookmarkSearch.filter(repository.allBookmarks.value.orEmpty(),
                    _selectedFolderId.value ?: FOLDER_ALL, _searchQuery.value.orEmpty(), sourceFilter())
            }
            addSource(repository.allBookmarks) { refresh() }
            addSource(_selectedFolderId) { refresh() }
            addSource(_searchQuery) { refresh() }
            addSource(_selectedSource) { refresh() }
        }
    }

    suspend fun saveDetails(id: Long, title: String, note: String) {
        repository.updateDetails(id, title, note)
    }

    /**
     * 폴더 칩 누름. 이미 선택된 폴더를 다시 누르면 해제되어 전체 목록으로 돌아간다.
     * ('전체'·'미분류' 칩을 없앴기 때문에 이 토글이 전체 보기로 가는 유일한 길이다)
     */
    fun toggleFolder(folderId: Long) {
        selectFolder(if (_selectedFolderId.value == folderId) FOLDER_ALL else folderId)
    }

    /** 폴더 칩 선택 */
    fun selectFolder(folderId: Long) {
        if (bulkBusy.value == true) return
        if (_selectedFolderId.value != folderId) { endSelection(); _selectedFolderId.value = folderId }
    }

    /**
     * 새 북마크 추가
     *
     * 처리 순서:
     * 1. URL 유효성 검사 및 https:// 자동 보완
     * 2. IO 스레드에서 웹사이트 메타데이터(제목, 이미지) fetch
     * 3. Bookmark 객체 생성 후 Room DB에 저장
     *
     * 특정 폴더를 보고 있는 상태라면 그 폴더에 넣는다.
     *
     * @param url 저장할 웹사이트 URL (클립보드 또는 사용자 입력)
     */
    fun addBookmark(url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // http/https가 없는 경우 https:// 자동 추가
                // 공유 인텐트(ShareReceiverActivity)와 같은 규칙을 쓰도록 유틸로 통일
                val validUrl = UrlMetadataFetcher.normalize(url)

                // 웹사이트 제목 및 이미지 URL 비동기 추출 (IO 스레드)
                val metadata = UrlMetadataFetcher.fetch(validUrl)

                // '전체'나 '미분류'를 보고 있으면 폴더 없이 저장한다.
                val targetFolder = _selectedFolderId.value
                    ?.takeIf { it != FOLDER_ALL && it != FOLDER_UNFILED }

                repository.insertBookmark(
                    Bookmark(
                        url = validUrl,
                        title = metadata.title,
                        imageUrl = metadata.imageUrl,
                        folderId = targetFolder
                    )
                )

            } catch (e: Exception) {
                _errorMessage.value = "저장 중 오류가 발생했습니다: ${e.message}"
            } finally {
                // 성공/실패 모두 로딩 상태 해제
                _isLoading.value = false
            }
        }
    }

    /**
     * 북마크 삭제
     *
     * @param bookmark 삭제할 북마크 객체
     */
    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmark)
        }
    }

    /**
     * 드래그 앤 드롭 후 순서를 DB에 저장
     *
     * @param bookmarks 새 순서로 재배열된 (화면에 보이는) 북마크 리스트
     */
    fun updateBookmarksOrder(bookmarks: List<Bookmark>) {
        if (!_searchQuery.value.isNullOrBlank()) return
        viewModelScope.launch {
            repository.updateBookmarksOrder(bookmarks)
        }
    }

    /** 북마크를 다른 폴더로 이동 (folderId 가 null 이면 미분류) */
    fun moveBookmarkToFolder(bookmarkId: Long, folderId: Long?) {
        viewModelScope.launch {
            repository.moveToFolder(bookmarkId, folderId)
        }
    }

    /** 새 폴더 생성. 이름이 중복이면 안내 메시지를 남긴다. */
    fun createFolder(name: String) {
        viewModelScope.launch {
            val id = repository.createFolder(name.trim())
            if (id == null) {
                _errorMessage.value = "같은 이름의 폴더가 이미 있습니다"
            } else {
                _selectedFolderId.value = id
            }
        }
    }

    /** 폴더 이름 변경 */
    fun renameFolder(id: Long, name: String) {
        viewModelScope.launch {
            if (!repository.renameFolder(id, name.trim())) {
                _errorMessage.value = "같은 이름의 폴더가 이미 있습니다"
            }
        }
    }

    /**
     * 폴더 삭제
     * 소속 북마크는 삭제되지 않고 '미분류'로 이동한다.
     */
    fun deleteFolder(id: Long) {
        viewModelScope.launch {
            repository.deleteFolder(id)
            if (_selectedFolderId.value == id) _selectedFolderId.value = FOLDER_ALL
        }
    }

    /** 에러 메시지 Toast 표시 후 null로 초기화 (중복 표시 방지) */
    fun clearError() {
        _errorMessage.value = null
    }

    companion object {
        /** 모든 북마크 보기 */
        const val FOLDER_ALL = -1L

        /** 폴더에 속하지 않은 북마크만 보기 */
        const val FOLDER_UNFILED = -2L
    }
}
