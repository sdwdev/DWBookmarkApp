package com.gameitstudio.dwbookmarkapp.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.gameitstudio.dwbookmarkapp.R
import com.gameitstudio.dwbookmarkapp.data.backup.BookmarkBackup
import com.gameitstudio.dwbookmarkapp.data.database.BookmarkDatabase
import com.gameitstudio.dwbookmarkapp.databinding.ActivitySettingsBinding
import com.gameitstudio.dwbookmarkapp.repository.BookmarkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 설정 화면
 *
 * 지금은 데이터 백업 항목만 있지만, 이후 설정이 늘어날 자리를 만들어 둔다.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    /** 내보내기/가져오기용. 화면 전용이라 여기서 직접 만든다. */
    private val repository: BookmarkRepository by lazy {
        val db = BookmarkDatabase.getDatabase(applicationContext)
        BookmarkRepository(db.bookmarkDao(), db.folderDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupToolbar()

        binding.rowExport.setOnClickListener { startExport() }
        binding.rowImport.setOnClickListener {
            // Drive 등 제공자에 따라 JSON 의 MIME 타입이 제각각이라 필터를 넓게 잡는다.
            importLauncher.launch(arrayOf("*/*"))
        }
    }

    // ------------------------------------------------------ 내보내기 / 가져오기

    /** 저장 위치를 사용자가 고른다. Google Drive 도 이 선택기에서 바로 선택된다. */
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { writeBackupTo(it) } }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { readBackupFrom(it) } }

    private fun startExport() {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.KOREA).format(Date())
        exportLauncher.launch("dwbookmark_backup_$stamp.json")
    }

    private fun writeBackupTo(uri: Uri) {
        toast(getString(R.string.backup_exporting))
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val (folders, bookmarks) = repository.exportSnapshot()
                    if (bookmarks.isEmpty() && folders.isEmpty()) return@withContext -1
                    val json = BookmarkBackup.toJson(folders, bookmarks)
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("파일을 열 수 없습니다")
                    bookmarks.size
                }
            }

            result.onSuccess { count ->
                when (count) {
                    -1 -> toast(getString(R.string.backup_export_empty))
                    else -> toast(getString(R.string.backup_export_done, count))
                }
            }.onFailure { e ->
                toast(getString(R.string.backup_export_failed, e.message ?: ""))
            }
        }
    }

    private fun readBackupFrom(uri: Uri) {
        toast(getString(R.string.backup_importing))
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val text = contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: throw IllegalStateException("파일을 열 수 없습니다")

                    val snapshot = BookmarkBackup.fromJson(text)
                    repository.importSnapshot(snapshot.folders, snapshot.bookmarks)
                }
            }

            result.onSuccess { (added, skipped) ->
                toast(
                    if (skipped > 0) {
                        getString(R.string.backup_import_done_skipped, added, skipped)
                    } else {
                        getString(R.string.backup_import_done, added)
                    }
                )
            }.onFailure { e ->
                toast(getString(R.string.backup_import_failed, e.message ?: ""))
            }
        }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { root, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.statusBarScrim.updateLayoutParams { height = bars.top }
            root.setPadding(bars.left, 0, bars.right, 0)
            binding.settingsScroll.updatePadding(bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setTitle(R.string.settings_title)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, SettingsActivity::class.java)
    }
}
