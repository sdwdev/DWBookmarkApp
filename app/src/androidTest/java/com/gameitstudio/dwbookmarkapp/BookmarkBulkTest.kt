package com.gameitstudio.dwbookmarkapp

import android.graphics.Bitmap
import androidx.room.Room
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.platform.app.InstrumentationRegistry
import com.gameitstudio.dwbookmarkapp.data.database.BookmarkDatabase
import com.gameitstudio.dwbookmarkapp.data.model.Bookmark
import com.gameitstudio.dwbookmarkapp.data.model.Folder
import com.gameitstudio.dwbookmarkapp.ui.MainActivity
import com.gameitstudio.dwbookmarkapp.viewmodel.BookmarkViewModel
import com.gameitstudio.dwbookmarkapp.util.BookmarkShare
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class BookmarkBulkTest {
    @Test fun bulkIsAtomicAndPreservesOtherFields() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, BookmarkDatabase::class.java).build()
        try {
            val dao = db.bookmarkDao()
            val folder = db.folderDao().insertFolder(Folder(name = "목적지"))
            val ids = (0..1001).map { dao.insertBookmark(Bookmark(url = "https://example.com/$it", title = "제목 $it", note = "비공개 메모", titleEdited = true)) }
            dao.applyBulk(ids.take(1001), false, folder)
            val all = dao.getAllBookmarksOnce()
            assertEquals(1001, all.count { it.folderId == folder })
            assertNull(all.last().folderId)
            assertTrue(all.all { it.note == "비공개 메모" && it.titleEdited })
            try { dao.applyBulk(ids, false, -123); fail("missing folder must fail") } catch (_: IllegalStateException) {}
            assertNull(dao.getAllBookmarksOnce().last().folderId)
            val text = BookmarkShare.text(all.take(2))
            assertEquals("제목 0\nhttps://example.com/0\n\n제목 1\nhttps://example.com/1", text)
            assertFalse(text.contains("비공개"))
            dao.applyBulk(ids.take(1001), true, null)
            assertEquals(listOf(ids.last()), dao.getAllBookmarksOnce().map { it.id })
        } finally { db.close() }
    }

    @Test fun selectionRecreateMoveCancelAndDelete() = runBlocking {
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        assumeTrue(context.packageName.endsWith(".searchtest"))
        val db = BookmarkDatabase.getDatabase(context)
        val dao = db.bookmarkDao()
        val folder = db.folderDao().insertFolder(Folder(name = "일괄 목적지"))
        val items = (1..3).map { n ->
            val item = Bookmark(url = "https://example.com/bulk$n", title = "일괄 링크 $n", note = "유지할 메모")
            item.copy(id = dao.insertBookmark(item))
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                fun action(id: Int) { scenario.onActivity { it.onOptionsItemSelected(it.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).menu.findItem(id)) } }
                suspend fun waitUntil(check: suspend () -> Boolean) {
                    val deadline = System.currentTimeMillis() + 5000
                    while (!check() && System.currentTimeMillis() < deadline) Thread.sleep(50)
                    assertTrue(check())
                }
                waitUntil {
                    var ready = false
                    scenario.onActivity { ready = ViewModelProvider(it)[BookmarkViewModel::class.java].bookmarks.value?.size == 3 }
                    ready
                }
                action(R.id.action_select)
                onView(withText("일괄 링크 1")).perform(click())
                onView(withText("일괄 링크 2")).perform(click())
                scenario.recreate()
                scenario.onActivity {
                    val vm = ViewModelProvider(it)[BookmarkViewModel::class.java]
                    assertEquals(2, vm.selectedIds.value!!.size)
                    assertTrue(vm.selecting.value!!)
                }
                inst.waitForIdleSync()
                val bitmap = inst.uiAutomation.takeScreenshot()
                File(context.getExternalFilesDir(null), "bulk-selection.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                action(R.id.action_bulk_move)
                onView(withText("일괄 목적지")).inRoot(isDialog()).perform(click())
                waitUntil { dao.getAllBookmarksOnce().count { it.folderId == folder } == 2 }
                assertNull(dao.findByUrl(items.last().url)!!.folderId)
                scenario.onActivity { ViewModelProvider(it)[BookmarkViewModel::class.java].selectFolder(folder) }
                waitUntil {
                    var size = 0
                    scenario.onActivity { size = ViewModelProvider(it)[BookmarkViewModel::class.java].bookmarks.value.orEmpty().size }
                    size == 2
                }
                action(R.id.action_select)
                action(R.id.action_select_all)
                scenario.onActivity {
                    val vm = ViewModelProvider(it)[BookmarkViewModel::class.java]
                    assertEquals(2, vm.selectedIds.value!!.size)
                    vm.selectFolder(BookmarkViewModel.FOLDER_ALL)
                    assertFalse(vm.selecting.value!!)
                    assertEquals(0, vm.selectedIds.value!!.size)
                }
                waitUntil {
                    var size = 0
                    scenario.onActivity { size = ViewModelProvider(it)[BookmarkViewModel::class.java].bookmarks.value.orEmpty().size }
                    size == 3
                }
                action(R.id.action_select)
                action(R.id.action_select_all)
                action(R.id.action_bulk_delete)
                onView(withText(R.string.btn_cancel)).inRoot(isDialog()).perform(click())
                assertEquals(3, dao.getAllBookmarksOnce().size)
                action(R.id.action_bulk_delete)
                onView(withText(R.string.btn_delete)).inRoot(isDialog()).perform(click())
                waitUntil { dao.getAllBookmarksOnce().isEmpty() }
            }
        } finally {
            items.forEach { dao.deleteBookmark(it) }
            db.folderDao().deleteFolder(folder)
        }
    }
}
