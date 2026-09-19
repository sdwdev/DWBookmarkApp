package com.gameitstudio.dwbookmarkapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 북마크 분류용 폴더 (Room Entity)
 *
 * 폴더에 속하지 않은 북마크는 Bookmark.folderId 가 null 이며 '미분류'로 취급한다.
 * 미분류를 별도 레코드로 만들지 않는 이유: 폴더를 지웠을 때 소속 북마크를
 * 자연스럽게 미분류로 되돌리기 위해서다.
 *
 * @param id        자동 생성 고유 ID
 * @param name      폴더 이름
 * @param sortOrder 칩 표시 순서
 * @param createdAt 생성 시각 (Unix 밀리초)
 */
@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
