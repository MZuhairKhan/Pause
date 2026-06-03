package com.lifelineventures.pause

import android.content.Context

/** User preferences for the overlay. */
object SettingsStore {
    private const val PREFS = "pause_settings"
    private const val KEY_SHOW_COUNTDOWN = "show_countdown"
    private const val KEY_POS_X = "bubble_pos_x"
    private const val KEY_POS_Y = "bubble_pos_y"
    private const val DEFAULT_POS_X = 1f
    private const val DEFAULT_POS_Y = 0.33f
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_ACCENT = "accent_index"

    /** When true the active bubble shows a live countdown; when false it keeps the static glyph. */
    fun showCountdown(context: Context): Boolean =
        context.prefs().getBoolean(KEY_SHOW_COUNTDOWN, true)

    fun setShowCountdown(context: Context, enabled: Boolean) {
        context.prefs().edit().putBoolean(KEY_SHOW_COUNTDOWN, enabled).apply()
    }

    /** Bubble position as a fraction (0..1) of the draggable area — the one thing kept across sessions. */
    fun bubbleFractionX(context: Context): Float = context.prefs().getFloat(KEY_POS_X, DEFAULT_POS_X)

    fun bubbleFractionY(context: Context): Float = context.prefs().getFloat(KEY_POS_Y, DEFAULT_POS_Y)

    fun saveBubbleFraction(context: Context, x: Float, y: Float) {
        context.prefs().edit().putFloat(KEY_POS_X, x).putFloat(KEY_POS_Y, y).apply()
    }

    /** 0 = follow system, 1 = light, 2 = dark. */
    fun themeMode(context: Context): Int = context.prefs().getInt(KEY_THEME_MODE, 0)

    fun setThemeMode(context: Context, mode: Int) {
        context.prefs().edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    /** Index into the accent palette ([com.lifelineventures.pause.ui.theme.Accents]). */
    fun accentIndex(context: Context): Int = context.prefs().getInt(KEY_ACCENT, 0)

    fun setAccentIndex(context: Context, index: Int) {
        context.prefs().edit().putInt(KEY_ACCENT, index).apply()
    }

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
