package com.gameitstudio.dwbookmarkapp.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gameitstudio.dwbookmarkapp.data.dao.BookmarkDao
import com.gameitstudio.dwbookmarkapp.data.dao.FolderDao
import com.gameitstudio.dwbookmarkapp.data.model.Bookmark
import com.gameitstudio.dwbookmarkapp.data.model.Folder

/**
 * Room 데이터베이스 싱글톤 클래스
 *
 * @Database 어노테이션: 포함할 Entity 목록, DB 버전 명시
 * exportSchema = false: 스키마 export 파일 비생성 (불필요한 파일 방지)
 *
 * 버전 이력
 * - 1: bookmarks 단일 테이블
 * - 2: folders 테이블 추가, bookmarks.folderId 컬럼 추가
 * - 3: 메모와 사용자 제목 수정 여부 추가
 */
@Database(entities = [Bookmark::class, Folder::class], version = 3, exportSchema = false)
abstract class BookmarkDatabase : RoomDatabase() {

    /** Bookmark DAO 접근자 */
    abstract fun bookmarkDao(): BookmarkDao

    /** Folder DAO 접근자 */
    abstract fun folderDao(): FolderDao

    companion object {
        // @Volatile: 모든 스레드에서 최신 값을 읽도록 보장 (캐시 미사용)
        @Volatile
        private var INSTANCE: BookmarkDatabase? = null

        /**
         * 1 -> 2 마이그레이션
         *
         * 이미 배포된 버전(1.0.5)의 사용자가 저장해 둔 북마크를 그대로 유지해야 하므로
         * 파괴적 마이그레이션(fallbackToDestructiveMigration)을 쓰지 않는다.
         * 기존 북마크는 folderId 가 NULL 이 되어 '미분류'로 남는다.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS folders (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN folderId INTEGER DEFAULT NULL")
            }
        }

        /**
         * 데이터베이스 인스턴스 반환 (없으면 생성)
         *
         * synchronized 블록: 동시에 여러 스레드가 접근해도 인스턴스 1개만 생성 보장
         * @param context ApplicationContext 사용 (메모리 누수 방지)
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN titleEdited INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): BookmarkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BookmarkDatabase::class.java,
                    "bookmark_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
