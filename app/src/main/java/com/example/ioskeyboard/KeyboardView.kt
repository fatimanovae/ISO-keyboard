package com.example.ioskeyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

/** iPhone-style dark keyboard drawn entirely in code. */
class KeyboardView(context: Context, private val host: Host) : LinearLayout(context) {

    interface Host {
        fun onChar(text: String)
        fun onDelete()
        fun onSpace()
        fun onReturn()
        fun onGlobe()
        fun onFeedback(kind: Int)
    }

    companion object {
        const val FB_KEY = 0
        const val FB_DELETE = 1
        const val FB_MOD = 2

        private const val T_CHAR = 0
        private const val T_SHIFT = 1
        private const val T_DEL = 2
        private const val T_LAYER = 3
        private const val T_GLOBE = 4
        private const val T_SPACE = 5
        private const val T_RETURN = 6
        private const val T_GAP = 7

        private val C_LETTER = 0xFF636366.toInt()
        private val C_LETTER_PRESSED = 0xFF8E8E93.toInt()
        private val C_SPECIAL = 0xFF424244.toInt()
        private val C_SHIFT_ON = 0xFFD6D8DD.toInt()
        private val C_PREVIEW = 0xFF7A7A7E.toInt()
    }

    private class Key(val label: String, val type: Int, val weight: Float = 1f, val target: Int = 0)

    private val handler = Handler(Looper.getMainLooper())
    private var layer = 0            // 0 letters, 1 numbers, 2 symbols
    private var shift = 0            // 0 off, 1 on, 2 caps lock
    private var lastShiftTap = 0L
    private var returnLabel = "return"

    private val letterViews = mutableListOf<Pair<TextView, String>>()
    private var shiftFace: FrameLayout? = null
    private var shiftIcon: ImageView? = null

    private val previewTv = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        textSize = 34f
        background = roundBg(C_PREVIEW, 10)
    }
    private val popup = PopupWindow(previewTv, dp(54), dp(66)).apply {
        isTouchable = false
        isClippingEnabled = false
    }

    private val repeatDelete = object : Runnable {
        override fun run() {
            host.onFeedback(FB_DELETE)
            host.onDelete()
            handler.postDelayed(this, 55)
        }
    }

    init {
        orientation = VERTICAL
        rebuild()
    }

    // ---- public API ----
    fun setReturnLabel(label: String) { returnLabel = label; rebuild() }
    fun setLayer(l: Int) { layer = l; rebuild() }
    fun autoShift(on: Boolean) {
        if (layer == 0 && shift != 2) setShift(if (on) 1 else 0)
    }

    // ---- layouts ----
    private fun chars(s: String, w: Float = 1f) = s.map { Key(it.toString(), T_CHAR, w) }

    private fun bottomRow(): List<Key> = listOf(
        Key(if (layer == 0) "123" else "ABC", T_LAYER, 1.3f, if (layer == 0) 1 else 0),
        Key("\uD83C\uDF10", T_GLOBE, 1.3f),
        Key("space", T_SPACE, 5.4f),
        Key(returnLabel, T_RETURN, 2f)
    )

    private fun layout(): List<List<Key>> = when (layer) {
        0 -> listOf(
            chars("qwertyuiop"),
            listOf(Key("", T_GAP, 0.5f)) + chars("asdfghjkl") + Key("", T_GAP, 0.5f),
            listOf(Key("shift", T_SHIFT, 1.5f)) + chars("zxcvbnm") + Key("del", T_DEL, 1.5f),
            bottomRow()
        )
        1 -> listOf(
            chars("1234567890"),
            chars("-/:;()$&@\""),
            listOf(Key("#+=", T_LAYER, 1.5f, 2)) + chars(".,?!'", 1.4f) + Key("del", T_DEL, 1.5f),
            bottomRow()
        )
        else -> listOf(
            chars("[]{}#%^*+="),
            chars("_\\|~<>€£¥•"),
            listOf(Key("123", T_LAYER, 1.5f, 1)) + chars(".,?!'", 1.4f) + Key("del", T_DEL, 1.5f),
            bottomRow()
        )
    }

    private fun rebuild() {
        removeAllViews()
        letterViews.clear()
        shiftFace = null
        shiftIcon = null
        for (row in layout()) {
            val r = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
            }
            for (k in row) r.addView(makeKey(k))
            addView(r)
        }
        updateShiftUi()
    }

    private fun makeKey(k: Key): View {
        val cell = FrameLayout(context)
        cell.layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, k.weight)
        if (k.type == T_GAP) return cell
        cell.setPadding(dp(3), dp(5), dp(3), dp(5))

        val special = k.type != T_CHAR && k.type != T_SPACE
        val face = FrameLayout(context)
        face.background = roundBg(if (special) C_SPECIAL else C_LETTER, 5)
        cell.addView(face, FrameLayout.LayoutParams(-1, -1))

        val tv = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        face.addView(tv, FrameLayout.LayoutParams(-1, -1))

        when (k.type) {
            T_CHAR -> {
                tv.text = k.label
                tv.textSize = 23f
                letterViews.add(tv to k.label)
            }
            T_SHIFT -> {
                val icon = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER }
                face.addView(icon, FrameLayout.LayoutParams(-1, -1))
                shiftFace = face; shiftIcon = icon
            }
            T_DEL -> {
                val icon = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER
                    setImageResource(R.drawable.ic_backspace)
                }
                face.addView(icon, FrameLayout.LayoutParams(-1, -1))
            }
            T_GLOBE -> { tv.text = k.label; tv.textSize = 20f }
            T_SPACE -> { tv.text = k.label; tv.textSize = 16f }
            else -> { tv.text = k.label; tv.textSize = 16f }
        }

        cell.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { onDown(k, face, tv); true }
                MotionEvent.ACTION_UP -> {
                    val inside = e.x >= 0 && e.x <= cell.width && e.y >= 0 && e.y <= cell.height
                    onUp(k, face, inside); true
                }
                MotionEvent.ACTION_CANCEL -> { onUp(k, face, false); true }
                else -> true
            }
        }
        return cell
    }

    // ---- touch handling ----
    private fun onDown(k: Key, face: FrameLayout, tv: TextView) {
        val special = k.type != T_CHAR && k.type != T_SPACE
        if (!(k.type == T_SHIFT && shift != 0)) {
            face.background = roundBg(if (special) C_LETTER else C_LETTER_PRESSED, 5)
        }
        host.onFeedback(
            when (k.type) {
                T_DEL -> FB_DELETE
                T_SHIFT, T_LAYER, T_GLOBE, T_RETURN -> FB_MOD
                else -> FB_KEY
            }
        )
        if (k.type == T_CHAR) showPreview(face, tv.text.toString())
        if (k.type == T_DEL) {
            host.onDelete()
            handler.postDelayed(repeatDelete, 400)
        }
    }

    private fun onUp(k: Key, face: FrameLayout, inside: Boolean) {
        val special = k.type != T_CHAR && k.type != T_SPACE
        hidePreview()
        handler.removeCallbacks(repeatDelete)
        if (k.type == T_SHIFT) updateShiftUi()
        else face.background = roundBg(if (special) C_SPECIAL else C_LETTER, 5)
        if (!inside) return

        when (k.type) {
            T_CHAR -> {
                val out = if (layer == 0 && shift != 0) k.label.uppercase() else k.label
                if (layer == 0 && shift == 1) setShift(0)
                host.onChar(out)
            }
            T_SPACE -> host.onSpace()
            T_RETURN -> host.onReturn()
            T_GLOBE -> host.onGlobe()
            T_LAYER -> setLayer(k.target)
            T_SHIFT -> {
                val now = SystemClock.uptimeMillis()
                when {
                    shift == 2 -> setShift(0)
                    shift == 1 && now - lastShiftTap < 350 -> setShift(2)
                    else -> setShift(if (shift == 0) 1 else 0)
                }
                lastShiftTap = now
            }
        }
    }

    private fun setShift(s: Int) { shift = s; updateShiftUi() }

    private fun updateShiftUi() {
        for ((tv, label) in letterViews) {
            tv.text = if (layer == 0 && shift != 0) label.uppercase() else label
        }
        shiftFace?.background = roundBg(if (shift != 0) C_SHIFT_ON else C_SPECIAL, 5)
        shiftIcon?.apply {
            setImageResource(if (shift == 0) R.drawable.ic_shift else R.drawable.ic_shift_filled)
            setColorFilter(if (shift != 0) Color.BLACK else Color.WHITE)
        }
    }

    // ---- key preview bubble ----
    private fun showPreview(anchor: View, text: String) {
        previewTv.text = text
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val x = loc[0] + anchor.width / 2 - dp(27)
        val y = loc[1] - dp(66) - dp(2)
        try {
            if (popup.isShowing) popup.update(x, y, dp(54), dp(66))
            else popup.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
        } catch (_: Exception) { }
    }

    private fun hidePreview() {
        try { if (popup.isShowing) popup.dismiss() } catch (_: Exception) { }
    }

    override fun onDetachedFromWindow() {
        hidePreview()
        handler.removeCallbacks(repeatDelete)
        super.onDetachedFromWindow()
    }

    // ---- helpers ----
    private fun roundBg(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
