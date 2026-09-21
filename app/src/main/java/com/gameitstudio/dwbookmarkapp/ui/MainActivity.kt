package com.gameitstudio.dwbookmarkapp.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.gameitstudio.dwbookmarkapp.R
import com.gameitstudio.dwbookmarkapp.data.model.Bookmark
import com.gameitstudio.dwbookmarkapp.data.model.Folder
import com.gameitstudio.dwbookmarkapp.databinding.ActivityMainBinding
import com.gameitstudio.dwbookmarkapp.dialog.AddBookmarkDialog
import com.gameitstudio.dwbookmarkapp.ui.adapter.BookmarkAdapter
import com.gameitstudio.dwbookmarkapp.ui.adapter.BookmarkItemTouchHelperCallback
import com.gameitstudio.dwbookmarkapp.viewmodel.BookmarkViewModel
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 앱 메인 화면 Activity
 *
 * 담당 역할:
 * - 북마크 목록 RecyclerView 표시
 * - FAB로 북마크 추가 다이얼로그 호출
 * - 아이템 클릭 시 브라우저 실행
 * - 드래그 앤 드롭으로 순서 변경
 * - AdMob 적응형 배너 광고 표시 및 생명주기 관리 (상단 광고 전용)
 * - Edge-to-edge 환경에서 시스템 바(상태바/내비게이션 바) 인셋 처리
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: BookmarkViewModel by viewModels()
    private lateinit var bookmarkAdapter: BookmarkAdapter

    /** 코드에서 생성하는 적응형 배너. 레이아웃 확정 전에는 null 이다. */
    private var adView: AdView? = null

    /** 현재 화면에 그려진 폴더 칩 목록. 중복 생성을 막기 위해 보관한다. */
    private var searchExpanded = false

    private var renderedFolders: List<Folder> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // targetSdk 35+ 에서는 시스템이 edge-to-edge 를 강제한다.
        // 상태바 뒤 배경(primary_dark)이 어두우므로 아이콘은 밝은 색으로 고정한다.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        searchExpanded = savedInstanceState?.getBoolean("searchExpanded") ?: false
        setupWindowInsets()
        setupToolbar()
        setupAdMob()
        setupRecyclerView()
        setupFab()
        observeViewModel()
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(false) {
            init { viewModel.selecting.observe(this@MainActivity) { isEnabled = it } }
            override fun handleOnBackPressed() { viewModel.endSelection() }
        })
        viewModel.selecting.observe(this) { refreshSelection() }
        viewModel.selectedIds.observe(this) { refreshSelection() }
        viewModel.bulkBusy.observe(this) { refreshSelection() }
    }

    // ---------------------------------------------------------------- 폴더 칩

    /**
     * 폴더 칩 줄을 다시 그린다.
     * 항상 '전체'와 '미분류'가 앞에 오고, 그 뒤로 사용자 폴더가 붙는다.
     */
    private fun renderSourceChips() {
        binding.sourceChips.removeAllViews()
        val options = listOf("" to "모든 사이트") +
            com.gameitstudio.dwbookmarkapp.util.BookmarkSource.entries.map { it.name to it.label }
        options.forEach { (key, label) ->
            binding.sourceChips.addView(Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = key == viewModel.selectedSource.value
                contentDescription = "사이트 분류: $label"
                setOnClickListener { viewModel.selectSource(key) }
            })
        }
    }

    private fun renderFolderChips(folders: List<Folder>, selectedId: Long) {
        renderedFolders = folders
        binding.folderChips.removeAllViews()

        // 폴더가 하나도 없으면 빈 줄만 남으므로 통째로 숨긴다.
        binding.folderScroll.isVisible = folders.isNotEmpty()
        folders.forEach { folder ->
            addFolderChip(folder.name, folder.id, selectedId, folder)
        }
    }

    /**
     * 칩 하나 추가
     *
     * @param folder 사용자 폴더일 때만 전달. 길게 눌러 이름 변경/삭제할 수 있다.
     */
    private fun addFolderChip(label: String, id: Long, selectedId: Long, folder: Folder?) {
        val chip = Chip(this).apply {
            text = label
            isCheckable = true
            isChecked = id == selectedId
            // 선택된 칩을 다시 누르면 해제되어 전체 목록으로 돌아간다.
            setOnClickListener { viewModel.toggleFolder(id) }
            if (folder != null) {
                setOnLongClickListener {
                    showFolderManageDialog(folder)
                    true
                }
            }
        }
        binding.folderChips.addView(chip)
    }

    /** 새 폴더 이름 입력 */
    private fun showCreateFolderDialog() {
        showFolderNameDialog(R.string.folder_create_title, "") { name ->
            viewModel.createFolder(name)
        }
    }

    /** 폴더 칩 길게 누르기 -> 이름 변경 / 삭제 */
    private fun showFolderManageDialog(folder: Folder) {
        val actions = arrayOf(
            getString(R.string.folder_action_rename),
            getString(R.string.folder_action_delete)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.folder_manage_title, folder.name))
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> showFolderNameDialog(R.string.folder_rename_title, folder.name) { name ->
                        viewModel.renameFolder(folder.id, name)
                    }
                    1 -> confirmDeleteFolder(folder)
                }
            }
            .show()
    }

    private fun confirmDeleteFolder(folder: Folder) {
        AlertDialog.Builder(this)
            .setTitle(R.string.folder_delete_title)
            .setMessage(getString(R.string.folder_delete_message, folder.name))
            .setPositiveButton(R.string.btn_delete) { _, _ -> viewModel.deleteFolder(folder.id) }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    /** 폴더 이름 입력 다이얼로그 (생성/변경 공용) */
    private fun showFolderNameDialog(titleRes: Int, initial: String, onConfirm: (String) -> Unit) {
        val padding = dpToPx(20)
        val input = EditText(this).apply {
            setText(initial)
            setSelection(initial.length)
            hint = getString(R.string.folder_name_hint)
            setSingleLine()
        }
        val container = FrameLayout(this).apply {
            setPadding(padding, dpToPx(8), padding, 0)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setView(container)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.folder_empty_name, Toast.LENGTH_SHORT).show()
                } else {
                    onConfirm(name)
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    /** 북마크를 길게 눌렀을 때 이동할 폴더를 고른다. */
    private fun showBookmarkActions(bookmark: Bookmark) {
        AlertDialog.Builder(this)
            .setTitle(bookmark.title)
            .setItems(arrayOf(getString(R.string.bookmark_edit), getString(R.string.bookmark_move_title))) { _, which ->
                if (which == 0) {
                    com.gameitstudio.dwbookmarkapp.dialog.EditBookmarkDialog.newInstance(bookmark)
                        .show(supportFragmentManager, "editBookmark")
                } else showMoveBookmarkDialog(bookmark)
            }.show()
    }

    private fun showMoveBookmarkDialog(bookmark: Bookmark) {
        val names = mutableListOf(getString(R.string.folder_unfiled))
        val ids = mutableListOf<Long?>(null)
        renderedFolders.forEach {
            names.add(it.name)
            ids.add(it.id)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.bookmark_move_title)
            .setItems(names.toTypedArray()) { _, which ->
                viewModel.moveBookmarkToFolder(bookmark.id, ids[which])
                Toast.makeText(this, R.string.bookmark_moved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    /**
     * 시스템 바 인셋 처리
     *
     * edge-to-edge 상태에서는 앱이 상태바/내비게이션 바 아래까지 그려지므로,
     * 인셋만큼 여백을 직접 넣지 않으면 광고·목록·FAB 가 시스템 바에 가려진다.
     */
    private fun setupWindowInsets() {
        val listPadding = dpToPx(LIST_PADDING_DP)
        val fabMargin = dpToPx(FAB_MARGIN_DP)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { root, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val bottomInset = maxOf(bars.bottom, imeBottom)

            // 상태바 영역을 배경색으로 채워 광고가 상태바와 겹치지 않게 한다.
            binding.statusBarScrim.updateLayoutParams { height = bars.top }

            // 가로 모드 / 디스플레이 컷아웃 대응
            root.setPadding(bars.left, 0, bars.right, 0)

            // 목록 마지막 항목이 내비게이션 바에 가려지지 않도록 하단 패딩 확보
            // (clipToPadding=false 이므로 스크롤은 화면 끝까지 이어진다)
            binding.recyclerView.setPadding(
                listPadding, listPadding, listPadding, listPadding + bottomInset
            )
            binding.emptyView.setPadding(0, 0, 0, bottomInset)

            // FAB 이 내비게이션 바에 가려지지 않도록 하단 마진 보정
            binding.fabAdd.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = fabMargin + bottomInset
            }

            insets
        }
        binding.root.doOnAttach { ViewCompat.requestApplyInsets(it) }
    }

    /**
     * 툴바 설정
     */
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val item = menu.findItem(R.id.action_search)
        val search = item.actionView as SearchView
        search.queryHint = getString(R.string.search_hint)
        search.maxWidth = Int.MAX_VALUE
        val query = viewModel.searchQuery.value.orEmpty()
        if (viewModel.selecting.value != true && (searchExpanded || query.isNotEmpty())) {
            item.expandActionView()
            search.setQuery(query, false)
            search.clearFocus()
        }
        search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                if (viewModel.selecting.value != true) viewModel.setSearchQuery(newText)
                return true
            }
            override fun onQueryTextSubmit(query: String): Boolean {
                search.clearFocus()
                return true
            }
        })
        item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                searchExpanded = true
                val retainedQuery = viewModel.searchQuery.value.orEmpty()
                search.post { search.setQuery(retainedQuery, false) }
                return true
            }
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                searchExpanded = false
                search.setQuery("", false)
                if (viewModel.selecting.value != true) viewModel.setSearchQuery("")
                search.clearFocus()
                return true
            }
        })
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("searchExpanded", searchExpanded)
        super.onSaveInstanceState(outState)
    }

    private fun refreshSelection() {
        val active = viewModel.selecting.value == true
        bookmarkAdapter.setSelection(active, (viewModel.selectedIds.value ?: longArrayOf()).toSet())
        supportActionBar?.title = if (active) "${(viewModel.selectedIds.value ?: longArrayOf()).size}개 선택" else getString(R.string.app_name)
        binding.fabAdd.isVisible = !active
        binding.folderChips.isEnabled = viewModel.bulkBusy.value != true
        if (binding.toolbar.menu.size() > 0) onPrepareOptionsMenu(binding.toolbar.menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val active = viewModel.selecting.value == true
        val ready = viewModel.bulkBusy.value != true
        listOf(R.id.action_search, R.id.action_new_folder, R.id.action_settings, R.id.action_select).forEach {
            menu.findItem(it).isVisible = !active
        }
        listOf(R.id.action_select_all, R.id.action_bulk_move, R.id.action_bulk_delete,
            R.id.action_bulk_share, R.id.action_select_end).forEach {
            menu.findItem(it).isVisible = active
            menu.findItem(it).isEnabled = ready
        }
        listOf(R.id.action_bulk_move, R.id.action_bulk_delete, R.id.action_bulk_share).forEach {
            menu.findItem(it).isEnabled = ready && viewModel.selectedBookmarks().isNotEmpty()
        }
        return super.onPrepareOptionsMenu(menu)
    }

    private fun bulkMove() {
        val ids = viewModel.selectedBookmarks().map { it.id }
        val folders = renderedFolders.toList()
        AlertDialog.Builder(this).setTitle("${ids.size}개 북마크 이동")
            .setItems((listOf(getString(R.string.folder_unfiled)) + folders.map { it.name }).toTypedArray()) { _, index ->
                viewModel.applyBulk(ids, false, if (index == 0) null else folders[index - 1].id)
            }.setNegativeButton(R.string.btn_cancel, null).show()
    }

    private fun bulkDelete() {
        val items = viewModel.selectedBookmarks()
        val ids = items.map { it.id }
        AlertDialog.Builder(this).setTitle("${ids.size}개 북마크 삭제")
            .setMessage("선택한 링크를 삭제할까요? 삭제 후 되돌릴 수 없습니다.")
            .setPositiveButton(R.string.btn_delete) { _, _ -> viewModel.applyBulk(ids, true) }
            .setNegativeButton(R.string.btn_cancel, null).show()
    }

    private fun bulkShare() {
        val items = viewModel.selectedBookmarks()
        if (items.isEmpty()) return
        val text = com.gameitstudio.dwbookmarkapp.util.BookmarkShare.text(items)
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }, "선택한 링크 공유"))
        } catch (e: Exception) {
            Toast.makeText(this, "공유 앱을 열 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_select -> { viewModel.startSelection(); binding.toolbar.menu.findItem(R.id.action_search).collapseActionView(); true }
        R.id.action_select_all -> { viewModel.selectAllVisible(); true }
        R.id.action_select_end -> { viewModel.endSelection(); true }
        R.id.action_bulk_move -> { bulkMove(); true }
        R.id.action_bulk_delete -> { bulkDelete(); true }
        R.id.action_bulk_share -> { bulkShare(); true }
        R.id.action_new_folder -> {
            showCreateFolderDialog()
            true
        }
        R.id.action_link_list -> {
            startActivity(LinkListActivity.intent(this))
            true
        }
        R.id.action_settings -> {
            startActivity(SettingsActivity.intent(this))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    /**
     * AdMob 초기화 후 상단 적응형 배너 광고 로드
     *
     * MobileAds.initialize() 는 디스크/네트워크 I/O 를 유발하므로 백그라운드에서 수행한다.
     */
    private fun setupAdMob() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { MobileAds.initialize(this@MainActivity) {} }
            loadBannerAd()
        }
    }

    /**
     * 컨테이너 폭에 맞춘 적응형(anchored adaptive) 배너를 생성해 로드한다.
     * 고정 BANNER(320x50)보다 채움률이 높은 Google 권장 방식이다.
     */
    private fun loadBannerAd() {
        val container = binding.adContainer

        // 광고 크기는 컨테이너 폭이 확정된 뒤에 계산해야 한다.
        container.doOnLayout {
            if (adView != null) return@doOnLayout

            val newAdView = AdView(this).apply {
                adUnitId = getString(R.string.admob_banner_unit_id)
                setAdSize(adaptiveAdSize(container.width))
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        Log.d(TAG, "배너 광고 로드 성공")
                        container.isVisible = true
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        // code 1 = INVALID_REQUEST(광고 단위 ID 오류), code 3 = NO_FILL(재고 없음)
                        Log.e(
                            TAG,
                            "배너 광고 로드 실패: code=" + error.code +
                                ", domain=" + error.domain +
                                ", message=" + error.message
                        )
                        container.isVisible = false
                    }
                }
            }

            adView = newAdView
            container.removeAllViews()
            container.addView(newAdView)
            newAdView.loadAd(AdRequest.Builder().build())
        }
    }

    /**
     * 현재 화면 방향 기준 적응형 배너 크기 계산
     *
     * getCurrentOrientationAnchoredAdaptiveBannerAdSize() 는 SDK 25 에서 deprecated 되었고,
     * getLargeAnchoredAdaptiveBannerAdSize() 가 현재 권장 API 다.
     *
     * @param containerWidthPx 광고 컨테이너 실제 폭(px). 0이면 화면 폭으로 대체한다.
     */
    private fun adaptiveAdSize(containerWidthPx: Int): AdSize {
        val density = resources.displayMetrics.density
        val widthPx =
            if (containerWidthPx > 0) containerWidthPx else resources.displayMetrics.widthPixels
        val widthDp = (widthPx / density).toInt()
        return AdSize.getLargeAnchoredAdaptiveBannerAdSize(this, widthDp)
    }

    /**
     * RecyclerView 설정
     */
    private fun setupRecyclerView() {
        bookmarkAdapter = BookmarkAdapter(
            onItemClick = { bookmark -> if (viewModel.selecting.value == true) viewModel.toggleSelection(bookmark.id) else openInBrowser(bookmark.url) },
            onItemLongClick = { bookmark -> if (viewModel.selecting.value == true) viewModel.toggleSelection(bookmark.id) else showBookmarkActions(bookmark) },
            onDeleteClick = { bookmark -> showDeleteConfirmDialog(bookmark) },
            onOrderChanged = { bookmarks -> viewModel.updateBookmarksOrder(bookmarks) },
            canReorder = { viewModel.selecting.value != true && viewModel.searchQuery.value.isNullOrBlank() }
        )

        val callback = BookmarkItemTouchHelperCallback(bookmarkAdapter)
        val itemTouchHelper = ItemTouchHelper(callback)
        bookmarkAdapter.itemTouchHelper = itemTouchHelper

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = bookmarkAdapter
            itemTouchHelper.attachToRecyclerView(this)
        }
    }

    /**
     * FAB 설정 - 북마크 추가 다이얼로그 표시
     */
    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            AddBookmarkDialog { url ->
                viewModel.addBookmark(url)
            }.show(supportFragmentManager, AddBookmarkDialog.TAG)
        }
    }

    /**
     * ViewModel LiveData 관찰 설정
     */
    private fun observeViewModel() {
        viewModel.bookmarks.observe(this) { bookmarks ->
            val retained = (viewModel.selectedIds.value ?: longArrayOf()).filter { id -> bookmarks.any { it.id == id } }.toLongArray()
            if (!retained.contentEquals((viewModel.selectedIds.value ?: longArrayOf()))) viewModel.selectedIds.value = retained
            bookmarkAdapter.submitList(bookmarks)

            applyListVisibility(bookmarks.isEmpty())
        }

        // 폴더 목록이나 선택 상태가 바뀌면 칩 줄을 다시 그린다.
        viewModel.folders.observe(this) { folders ->
            renderFolderChips(folders, viewModel.selectedFolderId.value ?: BookmarkViewModel.FOLDER_ALL)
        }

        viewModel.selectedSource.observe(this) { renderSourceChips() }

        viewModel.selectedFolderId.observe(this) { selectedId ->
            renderFolderChips(renderedFolders, selectedId)
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.fabAdd.isEnabled = !isLoading
            binding.fabAdd.alpha = if (isLoading) 0.5f else 1.0f
        }

        viewModel.errorMessage.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    /**
     * 기본 브라우저로 URL 열기
     */
    private fun openInBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "브라우저를 열 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 북마크 삭제 확인 다이얼로그
     */
    private fun showDeleteConfirmDialog(bookmark: Bookmark) {
        AlertDialog.Builder(this)
            .setTitle("북마크 삭제")
            .setMessage("'${bookmark.title}'을(를) 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                viewModel.deleteBookmark(bookmark)
                Toast.makeText(this, "삭제되었습니다", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /** 북마크 유무에 따라 목록과 빈 화면 안내 중 하나만 보여준다. */
    private fun applyListVisibility(isEmpty: Boolean) {
        binding.emptyMessage.setText(
            if (viewModel.sourceFilter() != null) R.string.source_empty
            else if (viewModel.searchQuery.value.isNullOrBlank()) R.string.empty_message
            else R.string.search_empty
        )
        binding.emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    // AdMob 생명주기 관리 (상단 광고만 사용)
    override fun onPause() {
        adView?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        adView?.resume()
    }

    override fun onDestroy() {
        adView?.destroy()
        adView = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val LIST_PADDING_DP = 8
        private const val FAB_MARGIN_DP = 16
    }
}
