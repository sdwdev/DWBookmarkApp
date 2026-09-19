package com.gameitstudio.dwbookmarkapp.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gameitstudio.dwbookmarkapp.data.model.Folder

/**
 * 폴더 데이터 접근 객체
 */
@Dao
interface FolderDao {

    /** 표시 순서대로 전체 폴더 조회 */
    @Query("SELECT * FROM folders ORDER BY sortOrder ASC, id ASC")
    fun getAllFolders(): LiveData<List<Folder>>

    /** 내보내기용 전체 조회 (LiveData 가 아닌 단발성 조회) */
    @Query("SELECT * FROM folders ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllFoldersOnce(): List<Folder>

    /** 이름으로 폴더 찾기 (가져오기 시 같은 이름 폴더 재사용) */
    @Query("SELECT * FROM folders WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Folder?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFolder(folder: Folder): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun renameFolder(id: Long, name: String)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    /** 같은 이름의 폴더가 이미 있는지 확인 (중복 생성 방지) */
    @Query("SELECT COUNT(*) FROM folders WHERE name = :name")
    suspend fun countByName(name: String): Int

    /** 새 폴더를 맨 뒤에 붙이기 위한 현재 최대 순서값 */
    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM folders")
    suspend fun getMaxSortOrder(): Int
}
