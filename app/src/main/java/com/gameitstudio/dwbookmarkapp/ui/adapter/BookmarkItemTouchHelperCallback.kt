package com.gameitstudio.dwbookmarkapp.ui.adapter

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * 드래그 앤 드롭 지원을 위한 어댑터 인터페이스
 * BookmarkAdapter와 ItemTouchHelperCallback 사이의 통신 정의
 */
interface ItemTouchHelperAdapter {
    /** 드래그로 fromPosition → toPosition 이동 시 호출 */
    fun onItemMove(fromPosition: Int, toPosition: Int)
    /** 드래그 손가락을 뗄 때 호출 (DB 저장 트리거) */
    fun onDragEnd()
}

/**
 * RecyclerView 드래그 앤 드롭 동작 처리 콜백
 *
 * ItemTouchHelper.Callback 구현:
 * - 상/하 드래그만 허용
 * - 좌/우 스와이프 비활성화
 * - 드래그 핸들(ivDragHandle) 터치로만 드래그 시작 (isLongPressDragEnabled = false)
 */
class BookmarkItemTouchHelperCallback(
    private val adapter: ItemTouchHelperAdapter
) : ItemTouchHelper.Callback() {

    /**
     * 이동 가능 방향 설정
     * dragFlags: UP, DOWN만 허용 (좌우 이동 불가)
     * swipeFlags: 0 (스와이프 완전 비활성화)
     */
    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        val swipeFlags = 0
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    /**
     * 드래그로 아이템이 이동될 때마다 호출
     * Adapter의 onItemMove를 통해 리스트 데이터 즉시 갱신 (시각적 반응)
     */
    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        adapter.onItemMove(
            viewHolder.bindingAdapterPosition,
            target.bindingAdapterPosition
        )
        return true
    }

    /** 스와이프 동작 없음 (구현 불필요) */
    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    /**
     * 드래그 완료(손가락 뗌) 시 호출
     * onDragEnd()를 통해 최종 순서를 DB에 저장
     */
    override fun clearView(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ) {
        super.clearView(recyclerView, viewHolder)
        adapter.onDragEnd()
    }

    /**
     * 길게 누르기로 드래그 비활성화
     * 대신 ivDragHandle 터치로만 드래그 시작 (BookmarkAdapter에서 처리)
     */
    override fun isLongPressDragEnabled(): Boolean = false
}