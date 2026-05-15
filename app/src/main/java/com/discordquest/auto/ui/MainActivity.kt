package com.discordquest.auto.ui

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.discordquest.auto.R
import com.discordquest.auto.databinding.ActivityMainBinding
import com.discordquest.auto.model.*
import com.discordquest.auto.viewmodel.QuestViewModel
import com.discordquest.auto.viewmodel.UiState

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: QuestViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.btnRun.setOnClickListener {
            val token = binding.etToken.text?.toString()?.trim() ?: ""
            if (token.length < 50) {
                binding.tilToken.error = "Token quá ngắn, hãy kiểm tra lại"
                return@setOnClickListener
            }
            binding.tilToken.error = null
            hideKeyboard()
            vm.startQuests(token)
        }

        binding.btnPaste.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            if (text.isNotEmpty()) {
                binding.etToken.setText(text)
                Toast.makeText(this, "Đã dán token", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Clipboard trống", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnReset.setOnClickListener {
            vm.reset()
            binding.etToken.setText("")
            binding.tilToken.error = null
        }

        binding.btnClearToken.setOnClickListener {
            binding.etToken.setText("")
            binding.tilToken.error = null
        }
    }

    private fun observeViewModel() {
        vm.uiState.observe(this) { state ->
            when (state) {
                is UiState.Idle -> showIdle()
                is UiState.Verifying -> showVerifying()
                is UiState.Running -> showRunning(state.update)
                is UiState.Done -> showDone(state.username, state.results)
                is UiState.Error -> showError(state.message)
            }
        }
    }

    private fun showIdle() {
        binding.cardInput.visibility = View.VISIBLE
        binding.cardStatus.visibility = View.GONE
        binding.cardResults.visibility = View.GONE
        binding.btnRun.isEnabled = true
        binding.btnRun.text = "Chạy Quest"
        binding.progressBar.visibility = View.GONE
    }

    private fun showVerifying() {
        binding.cardInput.visibility = View.VISIBLE
        binding.cardStatus.visibility = View.VISIBLE
        binding.cardResults.visibility = View.GONE
        binding.btnRun.isEnabled = false
        binding.btnRun.text = "Đang xử lý..."
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatusTitle.text = "⏳ Đang xác minh token..."
        binding.tvStatusBody.text = ""
        binding.tvUsername.visibility = View.GONE
    }

    private fun showRunning(update: ProgressUpdate) {
        binding.cardInput.visibility = View.VISIBLE
        binding.cardStatus.visibility = View.VISIBLE
        binding.cardResults.visibility = View.GONE
        binding.btnRun.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        val done = update.done.size
        val total = update.allNames.size
        binding.tvUsername.visibility = View.VISIBLE
        binding.tvUsername.text = "@${vm.username.value}"
        binding.tvStatusTitle.text = "⚙️ Đang xử lý quest... $done/$total"

        val sb = StringBuilder()
        update.allNames.forEachIndexed { i, name ->
            val doneItem = update.done.firstOrNull { it.name == name }
            val icon = when {
                doneItem != null -> when (doneItem.status) {
                    QuestStatus.COMPLETED -> "✅"
                    QuestStatus.SKIPPED, QuestStatus.WAITING -> "➡️"
                    QuestStatus.ERROR -> "❌"
                    QuestStatus.RUNNING -> "⏳"
                }
                i == update.currentIdx -> "⏳"
                else -> "◻️"
            }
            sb.append("$icon $name")
            if (i == update.currentIdx) sb.append("\n     ➤ ${update.action}")
            if (i < update.allNames.size - 1) sb.append("\n")
        }
        binding.tvStatusBody.text = sb.toString()
    }

    private fun showDone(username: String, results: List<QuestResult>) {
        binding.cardInput.visibility = View.GONE
        binding.cardStatus.visibility = View.GONE
        binding.cardResults.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
        binding.btnReset.visibility = View.VISIBLE

        val completed = results.count { it.status == QuestStatus.COMPLETED }
        val skipped = results.count { it.status in listOf(QuestStatus.SKIPPED, QuestStatus.WAITING) }
        val errors = results.count { it.status == QuestStatus.ERROR }

        binding.tvResultTitle.text = "📋 Kết quả — @$username"
        binding.tvCompleted.text = "✅ Hoàn thành: $completed"
        binding.tvSkipped.text = "➡️ Bỏ qua: $skipped"
        binding.tvErrors.text = "❌ Lỗi: $errors"

        if (results.isEmpty()) {
            binding.tvResultBody.text = "Không có quest nào cần hoàn thành."
            return
        }

        val sb = StringBuilder()
        results.forEachIndexed { i, r ->
            val icon = when (r.status) {
                QuestStatus.COMPLETED -> "✅"
                QuestStatus.SKIPPED, QuestStatus.WAITING -> "➡️"
                QuestStatus.ERROR -> "❌"
                QuestStatus.RUNNING -> "⏳"
            }
            val label = TASK_LABELS[r.taskType] ?: r.taskType
            val detail = when (r.status) {
                QuestStatus.COMPLETED -> "Hoàn thành"
                QuestStatus.SKIPPED, QuestStatus.WAITING -> r.message.ifEmpty { "Bỏ qua" }
                QuestStatus.ERROR -> r.message.ifEmpty { "Lỗi" }
                QuestStatus.RUNNING -> "Đang chạy"
            }
            sb.append("${i + 1}. $icon ${r.name}\n   $label — $detail")
            if (i < results.size - 1) sb.append("\n\n")
        }
        binding.tvResultBody.text = sb.toString()
    }

    private fun showError(message: String) {
        binding.cardInput.visibility = View.VISIBLE
        binding.cardStatus.visibility = View.VISIBLE
        binding.cardResults.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        binding.btnRun.isEnabled = true
        binding.btnRun.text = "Chạy Quest"
        binding.tvUsername.visibility = View.GONE
        binding.tvStatusTitle.text = "⚠️ Lỗi"
        binding.tvStatusBody.text = message
        binding.tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.error))
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }
}
