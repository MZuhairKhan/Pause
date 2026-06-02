package com.lifelineventures.pause

import android.content.Context

/**
 * Persists the active timer's wall-clock end time so the bubble's countdown can be
 * restored if the service is killed and restarted (START_STICKY hands back a null intent).
 */
object TimerStore {
    private const val PREFS = "pause_timer"
    private const val KEY_END = "end_time_millis"

    fun save(context: Context, endTimeMillis: Long) {
        context.prefs().edit().putLong(KEY_END, endTimeMillis).apply()
    }

    /** Returns the stored end time in millis, or 0 if no timer is active. */
    fun endTime(context: Context): Long = context.prefs().getLong(KEY_END, 0L)

    fun clear(context: Context) {
        context.prefs().edit().remove(KEY_END).apply()
    }

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
