package com.example.ioskeyboard

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import kotlin.concurrent.thread

class IosKeyboardService : InputMethodService(), KeyboardView.Host {

    private lateinit var kb: KeyboardView
    private lateinit var aiBar: View
    private lateinit var aiRow: LinearLayout
    private lateinit var undoChip: TextView
    private lateinit var sound: SoundFx

    private val main = Handler(Looper.getMainLooper())
    private var lastSpace = 0L
    private var busy = false
    private var lastResult: String? = null
    private var lastOriginal: String? = null

    override fun onCreate() {
        super.onCreate()
        sound = SoundFx(this)
    }

    override fun onDestroy() {
        sound.release()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        kb = KeyboardView(this, this)
        aiBar = buildAiBar()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1E1F21.toInt())
            setPadding(dp(3), dp(4), dp(3), dp(4))
            addView(aiBar, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(kb, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.updatePadding(bottom = dp(4) + nav)
            insets
        }
        return root
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val password =
            (cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
                    (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)

        // AI bar only for normal text fields, never for passwords
        aiBar.visibility = if (cls == InputType.TYPE_CLASS_TEXT && !password) View.VISIBLE else View.GONE
        lastResult = null
        undoChip.visibility = View.GONE

        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        kb.setReturnLabel(
            when (action) {
                EditorInfo.IME_ACTION_GO -> "go"
                EditorInfo.IME_ACTION_SEARCH -> "search"
                EditorInfo.IME_ACTION_SEND -> "send"
                EditorInfo.IME_ACTION_NEXT -> "next"
                EditorInfo.IME_ACTION_DONE -> "done"
                else -> "return"
            }
        )
        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE ||
                cls == InputType.TYPE_CLASS_DATETIME
        kb.setLayer(if (numeric) 1 else 0)
        refreshAutoCap()
    }

    // ---- KeyboardView.Host ----
    override fun onChar(text: String) {
        clearUndo()
        currentInputConnection?.commitText(text, 1)
        refreshAutoCap()
    }

    override fun onDelete() {
        clearUndo()
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        refreshAutoCap()
    }

    override fun onSpace() {
        clearUndo()
        val ic = currentInputConnection ?: return
        val now = System.currentTimeMillis()
        val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
        // iPhone-style: double space -> ". "
        if (now - lastSpace < 400 && before.length == 2 && before[1] == ' ' &&
            before[0].isLetterOrDigit()
        ) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            lastSpace = 0
        } else {
            ic.commitText(" ", 1)
            lastSpace = now
        }
        refreshAutoCap()
    }

    override fun onReturn() {
        clearUndo()
        val info = currentInputEditorInfo
        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noAction = (info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        if (info != null && !noAction && action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            currentInputConnection?.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        refreshAutoCap()
    }

    override fun onGlobe() {
        val imm = getSystemService(InputMethodManager::class.java)
        if (Build.VERSION.SDK_INT >= 28) {
            if (!switchToNextInputMethod(false)) imm.showInputMethodPicker()
        } else imm.showInputMethodPicker()
    }

    override fun onFeedback(kind: Int) {
        sound.play(kind)
        if (Prefs.haptic(this)) kb.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun refreshAutoCap() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo ?: return
        kb.autoShift(ic.getCursorCapsMode(ei.inputType) != 0)
    }

    // ---- AI rewrite ----
    private fun buildAiBar(): View {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        aiRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(2), dp(2), dp(2), dp(6))
        }
        undoChip = chip("↩ Undo", 0xFF8A5A00.toInt()) { undo() }.apply { visibility = View.GONE }
        aiRow.addView(undoChip)
        aiRow.addView(chip("Select all", 0xFF3A3A3C.toInt()) {
            currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
        })
        val modes = listOf(
            "Professional" to "professional", "Casual" to "casual", "Longer" to "longer",
            "Funnier" to "funnier", "Shorter" to "shorter", "Fix grammar" to "fix"
        )
        for ((label, mode) in modes) {
            aiRow.addView(chip("✨ $label", 0xFF3A3A3C.toInt()) { runAi(mode) })
        }
        scroll.addView(aiRow)
        return scroll
    }

    private fun chip(text: String, color: Int, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                setColor(color); cornerRadius = dp(18).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(3), 0, dp(3), 0) }
            setOnClickListener { onClick() }
        }

    private fun runAi(mode: String) {
        if (busy) return
        val ic = currentInputConnection ?: return
        val key = Prefs.apiKey(this)
        if (key.isBlank()) {
            toast("Free on-device AI: tap Select all, then choose \"Rewrite (iKeyboard AI)\" in the text menu (⋮ if hidden)."); return
        }
        val selected = ic.getSelectedText(0)?.toString()
        val useSelection = !selected.isNullOrEmpty()
        val before = if (useSelection) "" else ic.getTextBeforeCursor(5000, 0)?.toString() ?: ""
        val after = if (useSelection) "" else ic.getTextAfterCursor(5000, 0)?.toString() ?: ""
        val original = if (useSelection) selected!! else before + after
        if (original.isBlank()) { toast("Type something first"); return }

        busy = true
        aiRow.alpha = 0.45f
        val model = Prefs.model(this)
        thread {
            val result = AiClient.rewrite(key, model, original, mode)
            main.post {
                busy = false
                aiRow.alpha = 1f
                result.onSuccess { out ->
                    val c = currentInputConnection ?: return@post
                    c.beginBatchEdit()
                    if (!useSelection) c.deleteSurroundingText(before.length, after.length)
                    c.commitText(out, 1)
                    c.endBatchEdit()
                    lastOriginal = original
                    lastResult = out
                    undoChip.visibility = View.VISIBLE
                    refreshAutoCap()
                }.onFailure { toast("AI error: ${it.message}") }
            }
        }
    }

    private fun undo() {
        val ic = currentInputConnection ?: return
        val res = lastResult ?: return
        val orig = lastOriginal ?: return
        ic.beginBatchEdit()
        ic.deleteSurroundingText(res.length, 0)
        ic.commitText(orig, 1)
        ic.endBatchEdit()
        clearUndo()
        refreshAutoCap()
    }

    private fun clearUndo() {
        lastResult = null
        if (::undoChip.isInitialized) undoChip.visibility = View.GONE
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
