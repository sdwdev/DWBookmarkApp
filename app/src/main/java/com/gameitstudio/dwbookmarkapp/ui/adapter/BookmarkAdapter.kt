package com.gameitstudio.dwbookmarkapp.ui.adapter

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.gameitstudio.dwbookmarkapp.R
import com.gameitstudio.dwbookmarkapp.data.model.Bookmark
import com.gameitstudio.dwbookmarkapp.databinding.ItemBookmarkBinding

/**
 * 북마크 RecyclerView 어댑터
 *
 * ListAdapter 사용 이유:
 * - DiffUtil 자동 처리로 변경된 아이템만 갱신 (성능 최적화)
 * - 삽입/삭제/이동 애니메이션 자동 제공
 *
 * ItemTouchHelperAdapter 구현: 드래그 앤 드롭 순서 변경 지원
 */
class BookmarkAdapter(
    private val onItemClick: (Bookmark) -> Unit,
    private val onItemLongClick: (Bookmark) -> Unit,
    private val onDeleteClick: (Bookmark) -> Unit,
    private val onOrderChanged: (List<Bookmark>) -> Unit,
    private val canReorder: () -> Boolean = { true }
) : ListAdapter<Bookmark, BookmarkAdapter.BookmarkViewHolder>(BookmarkDiffCallback()),
    ItemTouchHelperAdapter {

    private var selectionMode = false
    private var selectedIds = emptySet<Long>()
    fun setSelection(active: Boolean, ids: Set<Long>) {
        if (selectionMode == active && selectedIds == ids) return
        selectionMode = active
        selectedIds = ids
        notifyItemRangeChanged(0, itemCount)
    }

    /**
     * ItemTouchHelper 외부 참조
     * ViewHolder에서 드래그 시작(startDrag) 호출에 필요
     */
    var itemTouchHelper: ItemTouchHelper? = null

    /**
     * DiffUtil 콜백 내부 클래스
     *
     * areItemsTheSame: DB id 비교 (같은 아이템인지 판단)
     * areContentsTheSame: 전체 데이터 비교 (내용 변경 여부 판단)
     */
    class BookmarkDiffCallback : DiffUtil.ItemCallback<Bookmark>() {
        override fun areItemsTheSame(oldItem: Bookmark, newItem: Bookmark): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Bookmark, newItem: Bookmark): Boolean {
            return oldItem == newItem
        }
    }

    /**
     * 북마크 아이템 ViewHolder
     * ViewBinding으로 뷰 참조 (타입 안전, null 안전)
     */
    inner class BookmarkViewHolder(
        private val binding: ItemBookmarkBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * 북마크 데이터를 뷰에 바인딩
         * @param bookmark 현재 위치의 북마크 데이터
         */
        fun bind(bookmark: Bookmark) {
            binding.selectionCheck.visibility = if (selectionMode) android.view.View.VISIBLE else android.view.View.GONE
            binding.selectionCheck.isChecked = bookmark.id in selectedIds
            binding.selectionCheck.contentDescription = "선택: " + bookmark.title
            binding.selectionCheck.setOnClickListener { onItemClick(bookmark) }
            binding.btnDelete.visibility = if (selectionMode) android.view.View.GONE else android.view.View.VISIBLE
            binding.ivDragHandle.visibility = if (selectionMode) android.view.View.INVISIBLE else android.view.View.VISIBLE
            binding.tvTitle.text = bookmark.title
            binding.tvUrl.text = bookmark.url
            binding.tvNote.text = bookmark.note
            binding.tvNote.visibility = if (bookmark.note.isBlank()) android.view.View.GONE else android.view.View.VISIBLE

            // 등록 날짜 포맷 및 표시
            val dateFormat = java.text.SimpleDateFormat(
                "yyyy.MM.dd HH:mm",
                java.util.Locale.KOREA
            )
            binding.tvDate.text = com.gameitstudio.dwbookmarkapp.util.BookmarkSource.fromUrl(bookmark.url).label + " · " + dateFormat.format(
                java.util.Date(bookmark.createdAt)
            )

            // Glide로 썸네일 이미지 로드
            Glide.with(binding.ivThumbnail.context)
                .load(bookmark.imageUrl)
                .placeholder(R.drawable.ic_bookmark_border)
                .error(R.drawable.ic_bookmark_border)
                .transition(DrawableTransitionOptions.withCrossFade())
                .centerCrop()
                .into(binding.ivThumbnail)

            // 아이템 전체 클릭 → 브라우저에서 URL 열기
            binding.root.setOnClickListener {
                onItemClick(bookmark)
            }

            // 길게 누르기 → 폴더 이동
            binding.root.setOnLongClickListener {
                onItemLongClick(bookmark)
                true
            }

            // 삭제 버튼 클릭
            binding.btnDelete.setOnClickListener {
                onDeleteClick(bookmark)
            }

            // 드래그 핸들 터치 → 드래그 시작
            binding.ivDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN && canReorder()) {
                    itemTouchHelper?.startDrag(this)
                }
                false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        val binding = ItemBookmarkBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return BookmarkViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookmarkViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * 드래그 중 아이템 위치 교체 (시각적 즉시 반응)
     */
    override fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (!canReorder() || fromPosition !in currentList.indices || toPosition !in currentList.indices) return
        val newList = currentList.toMutableList()
        val movedItem = newList.removeAt(fromPosition)
        newList.add(toPosition, movedItem)
        submitList(newList)
    }

    /**
     * 드래그 완료 → 현재 순서를 DB에 영구 저장
     */
    override fun onDragEnd() {
        if (canReorder()) onOrderChanged(currentList.toList())
    }
}