package com.example.ioskeyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlin.random.Random

/** Soft iPhone-like key sounds. Volume also follows the phone's system volume. */
class SoundFx(private val ctx: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()

    private val click = pool.load(ctx, R.raw.key_click, 1)
    private val delete = pool.load(ctx, R.raw.key_delete, 1)
    private val modifier = pool.load(ctx, R.raw.key_modifier, 1)

    fun play(kind: Int) {
        if (!Prefs.sound(ctx)) return
        val v = Prefs.volume(ctx) / 100f
        if (v <= 0f) return
        val id = when (kind) {
            KeyboardView.FB_DELETE -> delete
            KeyboardView.FB_MOD -> modifier
            else -> click
        }
        pool.play(id, v, v, 1, 0, 0.97f + Random.nextFloat() * 0.06f)
    }

    fun release() = pool.release()
}
