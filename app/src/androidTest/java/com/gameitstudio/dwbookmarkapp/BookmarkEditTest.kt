package com.gameitstudio.dwbookmarkapp

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.platform.app.InstrumentationRegistry
import com.gameitstudio.dwbookmarkapp.data.database.BookmarkDatabase
import com.gameitstudio.dwbookmarkapp.data.model.Bookmark
import com.gameitstudio.dwbookmarkapp.data.backup.BookmarkBackup
import com.gameitstudio.dwbookmarkapp.util.BookmarkSearch
import com.gameitstudio.dwbookmarkapp.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class BookmarkEditTest {
    @Test fun migrationBackupAndMetadata() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (version in 1..2) {
            val name = "edit-migration-$version-test"
            context.deleteDatabase(name)
            val file = context.getDatabasePath(name)
            file.parentFile!!.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
                old.execSQL("CREATE TABLE bookmarks (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL, title TEXT NOT NULL, imageUrl TEXT NOT NULL, sortOrder INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                old.execSQL("INSERT INTO bookmarks VALUES (1, 'https://example.com', '기존 제목', '', 7, 123)")
                if (version == 2) {
                    old.execSQL("CREATE TABLE folders (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, sortOrder INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                    old.execSQL("ALTER TABLE bookmarks ADD COLUMN folderId INTEGER DEFAULT NULL")
                    old.execSQL("INSERT INTO folders VALUES (3, '기존 폴더', 0, 123)")
                    old.execSQL("UPDATE bookmarks SET folderId = 3")
                }
                old.version = version
            }
            val db = Room.databaseBuilder(context, BookmarkDatabase::class.java, name)
                .addMigrations(BookmarkDatabase.MIGRATION_1_2, BookmarkDatabase.MIGRATION_2_3).build()
            try {
                val dao = db.bookmarkDao()
                val original = dao.getAllBookmarksOnce().single()
                assertEquals("기존 제목", original.title)
                assertEquals("", original.note)
                assertEquals(if (version == 2) 3L else null, original.folderId)
                dao.updateDetails(1, "수정 제목", "다음 주 읽기\n중요한 자료")
                dao.updateMetadata(1, "자동 제목", "image")
                dao.updatePositions(listOf(original.copy(sortOrder = 9)))
                val edited = dao.getAllBookmarksOnce().single()
                assertEquals("수정 제목", edited.title)
                assertEquals("다음 주 읽기\n중요한 자료", edited.note)
                assertEquals(9, edited.sortOrder)
                assertEquals(1, BookmarkSearch.filter(listOf(edited), -1, "중요한").size)
                val restored = BookmarkBackup.fromJson(BookmarkBackup.toJson(emptyList(), listOf(edited))).bookmarks.single()
                assertEquals(edited.copy(id = 0), restored)
                val legacy = BookmarkBackup.fromJson("""{"version":1,"bookmarks":[{"url":"https://old.example","title":"old"}]}""").bookmarks.single()
                assertEquals("", legacy.note)
                assertFalse(legacy.titleEdited)
                dao.updateDetails(1, "수정 제목", "")
                assertEquals("", dao.getAllBookmarksOnce().single().note)
            } finally { db.close(); context.deleteDatabase(name) }
        }
    }

    @Test fun editSaveCancelAndRecreate() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".searchtest"))
        val dao = BookmarkDatabase.getDatabase(context).bookmarkDao()
        val item = Bookmark(url = "https://example.com/edit-fixture", title = "편집 검증 원본")
        val id = dao.insertBookmark(item)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                fun openEditor() {
                    onView(withId(R.id.tvTitle)).perform(longClick())
                    onView(withText(R.string.bookmark_edit)).perform(click())
                }
                // Wait for the asynchronous Room list before interacting.
                val deadline = System.currentTimeMillis() + 5000
                while (System.currentTimeMillis() < deadline) {
                    var count = 0
                    scenario.onActivity { count = it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerView).adapter!!.itemCount }
                    if (count > 0) break
                    Thread.sleep(50)
                }
                openEditor()
                onView(withId(R.id.editBookmarkTitle)).inRoot(isDialog()).perform(replaceText(""), closeSoftKeyboard())
                onView(withText(R.string.btn_save)).inRoot(isDialog()).perform(click())
                onView(withId(R.id.editBookmarkTitle)).inRoot(isDialog()).check(matches(hasErrorText(context.getString(R.string.bookmark_title_required))))
                onView(withId(R.id.editBookmarkTitle)).inRoot(isDialog()).perform(replaceText("읽고 싶은 자료"), closeSoftKeyboard())
                onView(withId(R.id.editBookmarkNote)).inRoot(isDialog()).perform(replaceText("주말에 읽기\n검색용 메모"), closeSoftKeyboard())
                scenario.recreate()
                val focusDeadline = System.currentTimeMillis() + 5000
                while (System.currentTimeMillis() < focusDeadline) {
                    var ready = false
                    scenario.onActivity {
                        val fragment = it.supportFragmentManager.findFragmentByTag("editBookmark") as? androidx.fragment.app.DialogFragment
                        ready = fragment?.dialog?.window?.decorView?.hasWindowFocus() == true
                    }
                    if (ready) break
                    Thread.sleep(50)
                }
                onView(withId(R.id.editBookmarkTitle)).inRoot(isDialog()).check(matches(withText("읽고 싶은 자료")))
                onView(withId(R.id.editBookmarkNote)).inRoot(isDialog()).check(matches(withText("주말에 읽기\n검색용 메모")))
                onView(withText(R.string.btn_save)).inRoot(isDialog()).perform(click())
                val savedDeadline = System.currentTimeMillis() + 5000
                while (dao.findByUrl(item.url)!!.title != "읽고 싶은 자료" && System.currentTimeMillis() < savedDeadline) Thread.sleep(50)
                assertEquals("읽고 싶은 자료", dao.findByUrl(item.url)!!.title)
                assertEquals("주말에 읽기\n검색용 메모", dao.findByUrl(item.url)!!.note)
                instrumentation.waitForIdleSync()
                onView(withId(R.id.tvNote)).check(matches(withText("주말에 읽기\n검색용 메모")))
                openEditor()
                onView(withId(R.id.editBookmarkTitle)).inRoot(isDialog()).perform(replaceText("취소할 제목"), closeSoftKeyboard())
                onView(withText(R.string.btn_cancel)).inRoot(isDialog()).perform(click())
                assertEquals("읽고 싶은 자료", dao.findByUrl(item.url)!!.title)
                onView(withId(R.id.action_search)).perform(click())
                onView(withId(androidx.appcompat.R.id.search_src_text)).perform(replaceText("검색용"), closeSoftKeyboard())
                onView(withId(R.id.tvNote)).check(matches(isDisplayed()))
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                File(context.getExternalFilesDir(null), "edit-note.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        } finally { dao.deleteBookmark(item.copy(id = id)) }
    }
}
