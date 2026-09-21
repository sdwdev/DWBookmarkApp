package com.gameitstudio.dwbookmarkapp.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gameitstudio.dwbookmarkapp.R
import com.gameitstudio.dwbookmarkapp.data.database.BookmarkDatabase
import com.gameitstudio.dwbookmarkapp.data.model.Bookmark
import com.gameitstudio.dwbookmarkapp.databinding.ActivityLinkListBinding
import com.gameitstudio.dwbookmarkapp.databinding.ItemLinkRowBinding
import com.gameitstudio.dwbookmarkapp.repository.BookmarkRepository
import com.gameitstudio.dwbookmarkapp.util.UrlDisplay

/**
 * 지금까지 저장한 링크를 한눈에 훑어보는 목록 화면
 *
 * 메인 화면은 카드·썸네일 중심이라 한 화면에 몇 개 안 들어온다.
 * 여기서는 제목과 짧게 줄인 주소만 한 줄씩 보여줘 전체를 빠르게 확인할 수 있게 한다.
 * 줄이는 것은 표시용일 뿐 저장된 주소는 그대로이며, 누르면 원본 주소로 열린다.
 */
class LinkListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLinkListBinding

    private val repository: BookmarkRepository by lazy {
        val db = BookmarkDatabase.getDatabase(applicationContext)
        BookmarkRepository(db.bookmarkDao(), db.folderDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))

        binding = ActivityLinkListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setTitle(R.string.link_list_title)
            setDisplayHomeAsUpEnabled(true)
        }

        val adapter = LinkAdapter(::openInBrowser)
        binding.linkRecycler.layoutManager = LinearLayoutManager(this)
        binding.linkRecycler.adapter = adapter

        // 저장 순서 그대로 보여준다 (메인 목록과 같은 정렬).
        repository.allBookmarks.observe(this) { bookmarks ->
            adapter.submit(bookmarks)
            binding.linkEmpty.isVisible = bookmarks.isEmpty()
            supportActionBar?.subtitle = getString(R.string.link_list_count, bookmarks.size)
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { root, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.statusBarScrim.updateLayoutParams { height = bars.top }
            root.setPadding(bars.left, 0, bars.right, 0)
            binding.linkRecycler.updatePadding(bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun openInBrowser(bookmark: Bookmark) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(bookmark.url)))
        } catch (e: Exception) {
            Toast.makeText(this, "브라우저를 열 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    private class LinkAdapter(
        private val onClick: (Bookmark) -> Unit
    ) : RecyclerView.Adapter<LinkAdapter.LinkViewHolder>() {

        private var items: List<Bookmark> = emptyList()

        fun submit(newItems: List<Bookmark>) {
            items = newItems
            notifyDataSetChanged()
        }

        class LinkViewHolder(val binding: ItemLinkRowBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = LinkViewHolder(
            ItemLinkRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun onBindViewHolder(holder: LinkViewHolder, position: Int) {
            val bookmark = items[position]
            holder.binding.tvLinkTitle.text = bookmark.title
            holder.binding.tvLinkUrl.text = UrlDisplay.short(bookmark.url)
            holder.binding.root.setOnClickListener { onClick(bookmark) }
        }

        override fun getItemCount() = items.size
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, LinkListActivity::class.java)
    }
}
