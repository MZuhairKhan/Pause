package com.lifelineventures.pause

import android.content.Context

/** User preferences for the overlay. */
object SettingsStore {
    private const val PREFS = "pause_settings"
    private const val KEY_SHOW_COUNTDOWN = "show_countdown"

    /** When true the active bubble shows a live countdown; when false it keeps the static glyph. */
    fun showCountdown(context: Context): Boolean =
        context.prefs().getBoolean(KEY_SHOW_COUNTDOWN, true)

    fun setShowCountdown(context: Context, enabled: Boolean) {
        context.prefs().edit().putBoolean(KEY_SHOW_COUNTDOWN, enabled).apply()
    }

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
