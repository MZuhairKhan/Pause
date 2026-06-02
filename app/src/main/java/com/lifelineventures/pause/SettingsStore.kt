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

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
