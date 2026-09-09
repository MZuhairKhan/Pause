package io.github.mzuhairkhan.pause

import android.annotation.SuppressLint
import android.content.Context

/**
 * The overlay service's live timer/break state, persisted so it survives the process being
 * killed and -- per [SessionRestore] -- the OS-initiated `START_STICKY` restart that follows.
 * Deliberately a separate SharedPreferences file from [SettingsStore]: that object holds user
 * *preferences*; this one holds runtime *state* that changes on every timer/break start and
 * stop. Every write commits synchronously so it survives an imminent kill, the same reasoning
 * as [SettingsStore.setMutedVolume].
 */
object PauseState {
    private const val PREFS = "pause_state"
    private const val KEY_TIMER_START = "timer_start"
    private const val KEY_TIMER_END = "timer_end"
    private const val KEY_BREAK_UNTIL = "break_until"
    private const val KEY_BREAK_PACKAGES = "break_packages"

    fun snapshot(context: Context): PauseSnapshot {
        val prefs = context.prefs()
        return PauseSnapshot(
            timerStartMillis = prefs.getLong(KEY_TIMER_START, 0L),
            timerEndMillis = prefs.getLong(KEY_TIMER_END, 0L),
            breakUntilMillis = prefs.getLong(KEY_BREAK_UNTIL, 0L)
        )
    }

    @SuppressLint("ApplySharedPref") // committed synchronously so a timer survives an imminent kill
    fun setTimer(context: Context, startMillis: Long, endMillis: Long) {
        context.prefs().edit()
            .putLong(KEY_TIMER_START, startMillis)
            .putLong(KEY_TIMER_END, endMillis)
            .commit()
    }

    fun clearTimer(context: Context) = setTimer(context, 0L, 0L)

    @SuppressLint("ApplySharedPref")
    fun setBreak(context: Context, untilMillis: Long, packages: Set<String>) {
        // Store a fresh copy; the Set returned by getStringSet must not be mutated in place.
        context.prefs().edit()
            .putLong(KEY_BREAK_UNTIL, untilMillis)
            .putStringSet(KEY_BREAK_PACKAGES, HashSet(packages))
            .commit()
    }

    @SuppressLint("ApplySharedPref")
    fun clearBreak(context: Context) {
        context.prefs().edit()
            .putLong(KEY_BREAK_UNTIL, 0L)
            .remove(KEY_BREAK_PACKAGES)
            .commit()
    }

    fun breakPackages(context: Context): Set<String> =
        context.prefs().getStringSet(KEY_BREAK_PACKAGES, emptySet())?.toSet() ?: emptySet()

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
