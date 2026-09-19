package com.gameitstudio.dwbookmarkapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 북마크 데이터 모델 (Room Entity)
 *
 * @param id          자동 생성 고유 ID (PrimaryKey)
 * @param url         저장된 웹사이트 전체 URL
 * @param title       웹사이트 제목 (og:title 또는 <title> 태그에서 추출)
 * @param imageUrl    대표 이미지 URL (og:image 우선, 없으면 Google Favicon API)
 * @param sortOrder   리스트 표시 순서 (드래그로 변경 가능)
 * @param createdAt   저장 시간 (Unix 밀리초)
 * @param folderId    소속 폴더 id. null 이면 '미분류'
 */
@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val title: String,
    val imageUrl: String = "",
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val folderId: Long? = null,
    @ColumnInfo(defaultValue = "''") val note: String = "",
    @ColumnInfo(defaultValue = "0") val titleEdited: Boolean = false
)