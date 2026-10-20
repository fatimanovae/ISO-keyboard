package com.example.ioskeyboard

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.rewriting.Rewriter
import com.google.mlkit.genai.rewriting.RewriterOptions
import com.google.mlkit.genai.rewriting.Rewriting
import com.google.mlkit.genai.rewriting.RewritingRequest

/**
 * Free, on-device rewriting with Gemini Nano (ML Kit GenAI).
 * Shown from the text-selection menu in any app (PROCESS_TEXT). Gemini Nano only runs while
 * an app is in the foreground, which is why this is an activity and not part of the keyboard.
 */
class RewriteActivity : Activity() {

    private var rewriter: Rewriter? = null
    private lateinit var status: TextView
    private lateinit var styleRow: LinearLayout
    private var text = ""
    private var readOnly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
        if (text.isBlank()) { finish(); return }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        col.addView(label("Rewrite with Gemini Nano", 18f, true))
        col.addView(label(text.take(120) + if (text.length > 120) "…" else "", 13f, false, 0xFF9A9AA0.toInt()))

        styleRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val styles = listOf(
            "Professional" to RewriterOptions.OutputType.PROFESSIONAL,
            "Friendly / casual" to RewriterOptions.OutputType.FRIENDLY,
            "Longer" to RewriterOptions.OutputType.ELABORATE,
            "Shorter" to RewriterOptions.OutputType.SHORTEN,
            "Rephrase" to RewriterOptions.OutputType.REPHRASE,
            "Add emoji" to RewriterOptions.OutputType.EMOJIFY
        )
        for ((name, type) in styles) styleRow.addView(styleButton(name, type))
        col.addView(styleRow)

        status = label("", 13f, false, 0xFF9A9AA0.toInt())
        col.addView(status)
        setContentView(col)
    }

    private fun styleButton(name: String, type: Int) = TextView(this).apply {
        this.text = name
        setTextColor(Color.WHITE)
        textSize = 16f
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = GradientDrawable().apply {
            setColor(0xFF3A3A3C.toInt()); cornerRadius = dp(12).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }
        setOnClickListener { start(type) }
    }

    private fun start(outputType: Int) {
        if (text.length > 1000) {
            status.text = "Text is too long for on-device rewriting. Select a shorter part (about 1000 characters)."
            return
        }
        styleRow.alpha = 0.4f
        styleRow.isEnabled = false
        status.text = "Preparing Gemini Nano…"

        rewriter?.close()
        val options = RewriterOptions.builder(this)
            .setOutputType(outputType)
            .setLanguage(RewriterOptions.Language.ENGLISH)
            .build()
        val rw = Rewriting.getClient(options)
        rewriter = rw
        val main = ContextCompat.getMainExecutor(this)

        val check = rw.checkFeatureStatus()
        check.addListener({
            val st = try { check.get() } catch (e: Exception) { fail(e); return@addListener }
            when (st) {
                FeatureStatus.AVAILABLE, FeatureStatus.DOWNLOADING -> infer(rw)
                FeatureStatus.DOWNLOADABLE -> {
                    status.text = "Downloading Gemini Nano (first time only)…"
                    rw.downloadFeature(object : DownloadCallback {
                        override fun onDownloadStarted(bytesToDownload: Long) {}
                        override fun onDownloadProgress(totalBytesDownloaded: Long) {}
                        override fun onDownloadCompleted() { runOnUiThread { infer(rw) } }
                        override fun onDownloadFailed(e: GenAiException) { runOnUiThread { fail(e) } }
                    })
                }
                else -> fail(Exception(
                    "Gemini Nano rewriting isn't available right now. Update the \"AICore\" app in Play Store, " +
                            "restart the phone, and try again. (Not supported with an unlocked bootloader.)"
                ))
            }
        }, main)
    }

    private fun infer(rw: Rewriter) {
        status.text = "Rewriting…"
        val future = rw.runInference(RewritingRequest.builder(text).build())
        future.addListener({
            try {
                val out = future.get().results.firstOrNull()?.text
                if (out.isNullOrBlank()) fail(Exception("No result, try again")) else done(out)
            } catch (e: Exception) { fail(e) }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun done(out: String) {
        if (readOnly) {
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("rewrite", out))
            Toast.makeText(this, "Rewritten text copied", Toast.LENGTH_LONG).show()
        } else {
            setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, out))
        }
        finish()
    }

    private fun fail(e: Exception) {
        styleRow.alpha = 1f
        status.text = "Error: " + ((e.cause ?: e).message ?: "unknown")
    }

    override fun onDestroy() {
        rewriter?.close()
        super.onDestroy()
    }

    private fun label(t: String, size: Float, bold: Boolean, color: Int = Color.WHITE) =
        TextView(this).apply {
            text = t; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
