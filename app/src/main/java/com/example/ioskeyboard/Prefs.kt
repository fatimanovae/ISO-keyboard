package com.example.ioskeyboard

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("ikeyboard", Context.MODE_PRIVATE)

    fun sound(c: Context) = sp(c).getBoolean("sound", true)
    fun setSound(c: Context, v: Boolean) = sp(c).edit().putBoolean("sound", v).apply()

    fun haptic(c: Context) = sp(c).getBoolean("haptic", true)
    fun setHaptic(c: Context, v: Boolean) = sp(c).edit().putBoolean("haptic", v).apply()

    /** 0..100 */
    fun volume(c: Context) = sp(c).getInt("volume", 40)
    fun setVolume(c: Context, v: Int) = sp(c).edit().putInt("volume", v).apply()

    fun apiKey(c: Context) = sp(c).getString("api_key", "") ?: ""
    fun setApiKey(c: Context, v: String) = sp(c).edit().putString("api_key", v.trim()).apply()

    fun model(c: Context) = sp(c).getString("model", "claude-haiku-5-5") ?: "claude-haiku-5-5"
    fun setModel(c: Context, v: String) =
        sp(c).edit().putString("model", v.trim().ifEmpty { "claude-haiku-5-5" }).apply()
}
