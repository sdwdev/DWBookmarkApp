package com.gameitstudio.dwbookmarkapp.dialog

import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import com.gameitstudio.dwbookmarkapp.databinding.DialogAddBookmarkBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 북마크 추가 다이얼로그 Fragment
 *
 * 주요 기능:
 * - 클립보드에 http/https URL이 있으면 자동으로 입력창에 채우기
 * - 입력값 유효성 검사
 * - Material Design 스타일 다이얼로그
 *
 * @param onConfirm URL 입력 확인 시 호출되는 콜백 (url: String)
 */
class AddBookmarkDialog(
    private val onConfirm: (String) -> Unit
) : DialogFragment() {

    // ViewBinding nullable: onDestroyView에서 null 처리로 메모리 누수 방지
    private var _binding: DialogAddBookmarkBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddBookmarkBinding.inflate(layoutInflater)

        // 클립보드 URL 자동 감지 및 입력창 채우기
        autoFillFromClipboard()

        // 저장 버튼 클릭 처리
        binding.btnConfirm.setOnClickListener {
            val url = binding.etUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                onConfirm(url)
                dismiss()
            } else {
                binding.tilUrl.error = "URL을 입력해주세요"
            }
        }

        // URL 입력 시 에러 메시지 자동 제거
        binding.etUrl.setOnFocusChangeListener { _, _ ->
            binding.tilUrl.error = null
        }

        // 취소 버튼
        binding.btnCancel.setOnClickListener { dismiss() }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
            .also { dialog ->
                // 다이얼로그 표시 시 키보드 자동 열기
                dialog.window?.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                )
            }
    }

    /**
     * 클립보드에서 URL 감지 후 자동 입력
     * http:// 또는 https://로 시작하는 텍스트만 자동 입력
     */
    private fun autoFillFromClipboard() {
        val clipboard = requireContext()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val clipText = clipboard.primaryClip
            ?.getItemAt(0)
            ?.text
            ?.toString()
            ?: return

        if (clipText.startsWith("http://") || clipText.startsWith("https://")) {
            binding.etUrl.setText(clipText)
            binding.etUrl.setSelection(clipText.length)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AddBookmarkDialog"
    }
}