package com.gameitstudio.dwbookmarkapp

import android.graphics.Bitmap
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.gameitstudio.dwbookmarkapp.data.database.BookmarkDatabase
import com.gameitstudio.dwbookmarkapp.data.model.Bookmark
import com.gameitstudio.dwbookmarkapp.data.model.Folder
import com.gameitstudio.dwbookmarkapp.ui.MainActivity
import com.gameitstudio.dwbookmarkapp.viewmodel.BookmarkViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class BookmarkSourceUiTest {
    @Test fun sourceFiltersExistingAndNewLinksWithSearchFoldersAndSelection() = runBlocking {
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        assumeTrue(context.packageName.endsWith(".searchtest"))
        val db = BookmarkDatabase.getDatabase(context)
        val dao = db.bookmarkDao()
        val folder = db.folderDao().insertFolder(Folder(name="SNS 학습"))
        val items = mutableListOf<Bookmark>()
        suspend fun insert(url: String, title: String, folderId: Long? = null) {
            val item = Bookmark(url=url, title=title, note="보존할 메모", folderId=folderId)
            items += item.copy(id=dao.insertBookmark(item))
        }
        insert("https://threads.net/@example/post/1", "스레드 기록")
        insert("https://instagram.com/reel/1", "인스타 기록")
        insert("https://youtu.be/one", "유튜브 학습", folder)
        insert("https://youtube.com/shorts/two", "유튜브 즐겨찾기")
        insert("https://example.com/article", "일반 웹사이트")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                fun expectCount(expected: Int) {
                    val deadline = System.currentTimeMillis() + 5000
                    var actual = -1
                    do {
                        inst.waitForIdleSync()
                        scenario.onActivity {
                            actual = it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerView).adapter!!.itemCount
                        }
                        if (actual == expected) break
                        Thread.sleep(50)
                    } while (System.currentTimeMillis() < deadline)
                    assertEquals(expected, actual)
                }
                fun source(label: String) {
                    onView(withContentDescription("사이트 분류: $label")).perform(scrollTo(), click())
                }
                expectCount(5)
                source("스레드"); expectCount(1)
                insert("https://threads.com/@example/post/2", "새 스레드 링크")
                expectCount(2)
                source("인스타그램"); expectCount(1)
                source("유튜브"); expectCount(2)
                scenario.recreate()
                expectCount(2)
                scenario.onActivity {
                    val vm = ViewModelProvider(it)[BookmarkViewModel::class.java]
                    assertEquals("YOUTUBE", vm.selectedSource.value)
                    vm.startSelection()
                    vm.selectAllVisible()
                    assertEquals(2, vm.selectedIds.value!!.size)
                }
                source("스레드"); expectCount(2)
                scenario.onActivity {
                    val vm = ViewModelProvider(it)[BookmarkViewModel::class.java]
                    assertFalse(vm.selecting.value!!)
                    assertEquals(0, vm.selectedIds.value!!.size)
                }
                source("유튜브")
                onView(withText("SNS 학습")).perform(click())
                expectCount(1)
                onView(withId(R.id.action_search)).perform(click())
                onView(withId(androidx.appcompat.R.id.search_src_text)).perform(replaceText("즐겨찾기"), closeSoftKeyboard())
                expectCount(0)
                onView(withId(R.id.emptyMessage)).check(matches(withText(R.string.source_empty)))
                onView(withId(androidx.appcompat.R.id.search_src_text)).perform(replaceText(""), closeSoftKeyboard())
                expectCount(1)
                scenario.onActivity {
                    it.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).menu.findItem(R.id.action_search).collapseActionView()
                }
                inst.waitForIdleSync()
                val image = inst.uiAutomation.takeScreenshot()
                File(context.getExternalFilesDir(null), "source-filter.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
                onView(withText("전체")).perform(click())
                source("기타 웹사이트"); expectCount(1)
                source("모든 사이트"); expectCount(6)
                assertEquals(items, dao.getAllBookmarksOnce())
            }
        } finally {
            items.forEach { dao.deleteBookmark(it) }
            db.folderDao().deleteFolder(folder)
        }
    }
}
