package com.example.ioskeyboard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SettingsActivity : Activity() {

    private lateinit var keyField: EditText
    private lateinit var modelField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(40))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF111113.toInt())
            addView(col)
        }
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, b.top, 0, b.bottom)
            insets
        }

        col.addView(text("iKeyboard AI", 28f, true))
        col.addView(text("iPhone-style dark keyboard with AI rewriting.", 14f, false, 0xFF9A9AA0.toInt()))

        col.addView(section("1. Turn the keyboard on"))
        col.addView(button("Enable keyboard in Android settings") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        col.addView(section("2. Switch to it"))
        col.addView(button("Choose iKeyboard AI as current keyboard") {
            getSystemService(InputMethodManager::class.java).showInputMethodPicker()
        })

        col.addView(section("Feedback"))
        val sw = Switch(this).apply {
            text = "Key click sounds"; setTextColor(Color.WHITE)
            isChecked = Prefs.sound(this@SettingsActivity)
            setOnCheckedChangeListener { _, c -> Prefs.setSound(this@SettingsActivity, c) }
        }
        col.addView(sw)
        col.addView(text("Click volume", 14f, false, 0xFF9A9AA0.toInt()))
        col.addView(SeekBar(this).apply {
            max = 100; progress = Prefs.volume(this@SettingsActivity)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, u: Boolean) =
                    Prefs.setVolume(this@SettingsActivity, p)
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        })
        col.addView(Switch(this).apply {
            text = "Vibration"; setTextColor(Color.WHITE)
            isChecked = Prefs.haptic(this@SettingsActivity)
            setOnCheckedChangeListener { _, c -> Prefs.setHaptic(this@SettingsActivity, c) }
        })

        col.addView(section("Free AI rewrite (Gemini Nano on your Pixel)"))
        col.addView(text(
            "1) Type your message. 2) Tap \"Select all\" above the keyboard (or long-press and select " +
                    "the text). 3) In the menu that appears, tap \"Rewrite (iKeyboard AI)\" - it may be under " +
                    "the ⋮ menu. 4) Pick Professional, Friendly, Longer, Shorter, Rephrase or Emoji. " +
                    "Runs on-device, free, no API key. English works best; the first use downloads the model.",
            13f, false, 0xFF9A9AA0.toInt()
        ))

        col.addView(section("Optional: Claude API (extra AI buttons)"))
        col.addView(text(
            "Get an API key at console.anthropic.com. When you tap an AI button, only the text in " +
                    "the current field is sent to the API. AI buttons are hidden in password fields.",
            13f, false, 0xFF9A9AA0.toInt()
        ))
        keyField = field("API key (sk-ant-...)", Prefs.apiKey(this), true)
        modelField = field("Model", Prefs.model(this), false)
        col.addView(keyField)
        col.addView(modelField)

        col.addView(section("Try it"))
        col.addView(EditText(this).apply {
            hint = "Tap here and type to test the keyboard"
            setHintTextColor(0xFF6A6A70.toInt()); setTextColor(Color.WHITE)
            minLines = 3
        })
    }

    override fun onPause() {
        Prefs.setApiKey(this, keyField.text.toString())
        Prefs.setModel(this, modelField.text.toString())
        super.onPause()
    }

    private fun text(t: String, size: Float, bold: Boolean, color: Int = Color.WHITE) =
        TextView(this).apply {
            text = t; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun section(t: String) = text(t, 16f, true).apply { setPadding(0, dp(22), 0, dp(6)) }

    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun field(hintText: String, value: String, secret: Boolean) = EditText(this).apply {
        hint = hintText; setText(value)
        setHintTextColor(0xFF6A6A70.toInt()); setTextColor(Color.WHITE)
        setSingleLine()
        inputType = InputType.TYPE_CLASS_TEXT or
                (if (secret) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_URI)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
