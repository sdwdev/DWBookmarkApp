package com.gameitstudio.dwbookmarkapp.ui.adapter

import com.gameitstudio.dwbookmarkapp.data.model.Bookmark
import com.google.android.gms.ads.nativead.NativeAd

/**
 * RecyclerView 에 표시되는 행의 종류
 *
 * 북마크 목록과 네이티브 광고를 한 리스트에서 다루기 위한 래퍼.
 * 광고는 목록 맨 끝에 한 개만 붙으므로 드래그 정렬 동작에 영향을 주지 않는다.
 */
sealed class BookmarkListItem {

    /** 일반 북마크 행 */
    data class BookmarkRow(val bookmark: Bookmark) : BookmarkListItem()

    /** 네이티브 광고 행 */
    data class NativeAdRow(val nativeAd: NativeAd) : BookmarkListItem()
}
