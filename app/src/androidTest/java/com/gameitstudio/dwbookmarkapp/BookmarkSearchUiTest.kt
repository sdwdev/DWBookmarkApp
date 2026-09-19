package com.gameitstudio.dwbookmarkapp

import android.graphics.Bitmap
import androidx.recyclerview.widget.RecyclerView
import com.gameitstudio.dwbookmarkapp.data.database.BookmarkDatabase
import com.gameitstudio.dwbookmarkapp.data.model.Bookmark
import com.gameitstudio.dwbookmarkapp.data.model.Folder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gameitstudio.dwbookmarkapp.ui.MainActivity
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookmarkSearchUiTest {
    /** Uses only the isolated test package; never changes the installed production app. */
    @Test fun searchMatchesAndFolderIntersection() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".searchtest"))
        val db = BookmarkDatabase.getDatabase(context)
        val folderId = db.folderDao().insertFolder(Folder(name = "검색검증폴더"))
        val fixtures = listOf(
            Bookmark(title = "한글 검색검증", url = "https://example.com/AndroidTest", folderId = folderId),
            Bookmark(title = "다른 폴더 항목", url = "https://example.org/androidtest")
        ).map { it.copy(id = db.bookmarkDao().insertBookmark(it)) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                fun expectCount(expected: Int) {
                    val deadline = System.currentTimeMillis() + 5000
                    var actual = -1
                    do {
                        instrumentation.waitForIdleSync()
                        scenario.onActivity { activity ->
                            actual = activity.findViewById<RecyclerView>(R.id.recyclerView).adapter!!.itemCount
                        }
                        if (actual == expected) break
                        Thread.sleep(50)
                    } while (System.currentTimeMillis() < deadline)
                    assertEquals(expected, actual)
                }
                fun query(value: String) {
                    onView(withId(androidx.appcompat.R.id.search_src_text))
                        .perform(replaceText(value), closeSoftKeyboard())
                }
                expectCount(2)
                onView(withId(R.id.action_search)).perform(click())
                query("검색검증")
                expectCount(1)
                onView(withText("한글 검색검증")).check(matches(isDisplayed()))
                query("ANDROIDTEST")
                expectCount(2)
                onView(withText("검색검증폴더")).perform(click())
                expectCount(1)
                query("example.org")
                expectCount(0)
                onView(withId(R.id.emptyMessage)).check(matches(withText(R.string.search_empty)))
                onView(withText(R.string.folder_all)).perform(click())
                expectCount(1)
                query("")
                expectCount(2)
                // Save a real device image with one matching bookmark.
                query("검색검증")
                expectCount(1)
                val file = File(context.getExternalFilesDir(null), "search-match.png")
                instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
        } finally {
            fixtures.forEach { db.bookmarkDao().deleteBookmark(it) }
            db.folderDao().deleteFolder(folderId)
        }
    }

    @Test fun searchClearCollapseAndRecreate() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.action_search)).perform(click())
            onView(withId(androidx.appcompat.R.id.search_src_text))
                .perform(replaceText("dwbookmark_no_match_84729"), closeSoftKeyboard())
            onView(withId(R.id.emptyMessage)).check(matches(withText(R.string.search_empty)))
            scenario.recreate()
            onView(withId(androidx.appcompat.R.id.search_src_text))
                .check(matches(withText("dwbookmark_no_match_84729")))
            onView(withId(R.id.emptyMessage)).check(matches(withText(R.string.search_empty)))
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val file = File(instrumentation.targetContext.getExternalFilesDir(null), "search-ui.png")
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            onView(withId(androidx.appcompat.R.id.search_src_text)).perform(replaceText(""), closeSoftKeyboard())
            scenario.onActivity { activity ->
                activity.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).collapseActionView()
            }
            onView(withId(R.id.action_search)).check(matches(isDisplayed()))
        }
    }
}
