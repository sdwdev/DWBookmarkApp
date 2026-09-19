package com.gameitstudio.dwbookmarkapp.dialog

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.gameitstudio.dwbookmarkapp.R
import com.gameitstudio.dwbookmarkapp.data.model.Bookmark
import com.gameitstudio.dwbookmarkapp.viewmodel.BookmarkViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Arguments and saved drafts survive recreation without retaining an Activity. */
class EditBookmarkDialog : DialogFragment() {
    private val viewModel: BookmarkViewModel
        get() = ViewModelProvider(requireActivity())[BookmarkViewModel::class.java]
    private lateinit var titleInput: EditText
    private lateinit var noteInput: EditText

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val content = layoutInflater.inflate(R.layout.dialog_edit_bookmark, null)
        titleInput = content.findViewById(R.id.editBookmarkTitle)
        noteInput = content.findViewById(R.id.editBookmarkNote)
        val values = savedInstanceState ?: requireArguments()
        titleInput.setText(values.getString("title", ""))
        noteInput.setText(values.getString("note", ""))
        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.bookmark_edit)
            .setView(content)
            .setPositiveButton(R.string.btn_save, null)
            .setNegativeButton(R.string.btn_cancel, null)
            .create()
    }

    override fun onStart() {
        super.onStart()
        val alert = requireDialog() as AlertDialog
        alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title = titleInput.text.toString().trim()
            if (title.isBlank()) {
                titleInput.error = getString(R.string.bookmark_title_required)
                titleInput.requestFocus()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                alert.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                try {
                    viewModel.saveDetails(requireArguments().getLong("id"), title, noteInput.text.toString())
                    dismiss()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("EditBookmarkDialog", "Save failed", e)
                    titleInput.error = getString(R.string.bookmark_save_failed)
                } finally {
                    alert.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("title", titleInput.text.toString())
        outState.putString("note", noteInput.text.toString())
    }

    companion object {
        fun newInstance(bookmark: Bookmark) = EditBookmarkDialog().apply {
            arguments = Bundle().apply {
                putLong("id", bookmark.id)
                putString("title", bookmark.title)
                putString("note", bookmark.note)
            }
        }
    }
}
