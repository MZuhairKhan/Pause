package com.lifelineventures.pause

import android.annotation.SuppressLint
import android.content.Context
import com.lifelineventures.pause.ui.theme.Accents

/** User preferences for the overlay. */
object SettingsStore {
    private const val PREFS = "pause_settings"
    private const val KEY_SHOW_COUNTDOWN = "show_countdown"
    private const val KEY_POS_X = "bubble_pos_x"
    private const val KEY_POS_Y = "bubble_pos_y"
    private const val DEFAULT_POS_X = 1f
    // First-run vertical spot, as a window-TOP fraction of the draggable area. Tuned so the
    // bubble's center lands exactly one rail-slot above Instagram's heart, so the gap above the
    // heart matches the gaps between the native icons. Measured from a 1080×2340 Reels screenshot:
    // heart center ~0.335 of height, rail spacing ~0.086 → target center ~0.249; with the
    // Instagram-preset bubble (~132px) that is a top fraction of ~0.234. Only affects fresh
    // installs (a saved position wins).
    private const val DEFAULT_POS_Y = 0.234f
    private const val KEY_BUBBLE_PRESET = "bubble_preset"
    private const val KEY_BUBBLE_CUSTOM_SIZE = "bubble_custom_size"
    private const val KEY_BUBBLE_CUSTOM_EDGE = "bubble_custom_edge"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_ACCENT_COLOR = "accent_color"
    private const val KEY_INHALE = "breath_inhale"
    private const val KEY_HOLD = "breath_hold"
    private const val KEY_EXHALE = "breath_exhale"
    private const val KEY_LOCK = "breath_lock"
    private const val KEY_BREATHING = "breath_enabled"
    private const val KEY_SNOOZE_MINUTES = "snooze_minutes"
    private const val KEY_MUTED_VOLUME = "muted_music_volume"
    private const val KEY_BLOCKED_APPS = "blocked_apps"
    private const val KEY_BLOCK_MINUTES = "block_minutes"
    private const val DEFAULT_INHALE = 4
    private const val DEFAULT_HOLD = 7
    private const val DEFAULT_EXHALE = 8
    private const val DEFAULT_LOCK = 30
    private const val DEFAULT_BLOCK_MINUTES = 30
    private const val DEFAULT_SNOOZE_MINUTES = 5

    /** When true the active bubble shows a live countdown; when false it keeps the static glyph. */
    fun showCountdown(context: Context): Boolean =
        context.prefs().getBoolean(KEY_SHOW_COUNTDOWN, false)

    fun setShowCountdown(context: Context, enabled: Boolean) {
        context.prefs().edit().putBoolean(KEY_SHOW_COUNTDOWN, enabled).apply()
    }

    /** When true a finished timer runs the breathing exercise; when false it drops straight to
     *  the dismiss options over the full themed background. */
    fun breathingEnabled(context: Context): Boolean =
        context.prefs().getBoolean(KEY_BREATHING, true)

    fun setBreathingEnabled(context: Context, enabled: Boolean) {
        context.prefs().edit().putBoolean(KEY_BREATHING, enabled).apply()
    }

    /** Bubble position as a fraction (0..1) of the draggable area — the one thing kept across sessions. */
    fun bubbleFractionX(context: Context): Float =
        SettingsRanges.fraction(context.prefs().getFloat(KEY_POS_X, DEFAULT_POS_X))

    fun bubbleFractionY(context: Context): Float =
        SettingsRanges.fraction(context.prefs().getFloat(KEY_POS_Y, DEFAULT_POS_Y))

    fun saveBubbleFraction(context: Context, x: Float, y: Float) {
        context.prefs().edit().putFloat(KEY_POS_X, x).putFloat(KEY_POS_Y, y).apply()
    }

    /** Which app's action rail the bubble's size/offset matches (see [BubblePresets]). */
    fun bubblePreset(context: Context): Int =
        context.prefs().getInt(KEY_BUBBLE_PRESET, BubblePresets.INSTAGRAM)
            .coerceIn(BubblePresets.INSTAGRAM, BubblePresets.CUSTOM)

    fun setBubblePreset(context: Context, preset: Int) {
        context.prefs().edit().putInt(KEY_BUBBLE_PRESET, preset).apply()
    }

    /** Custom (slider) size/edge fractions, used when the preset is [BubblePresets.CUSTOM]. */
    fun customBubbleSize(context: Context): Float =
        context.prefs().getFloat(KEY_BUBBLE_CUSTOM_SIZE, BubblePresets.DEFAULT_CUSTOM.sizeFraction)

    fun setCustomBubbleSize(context: Context, value: Float) {
        context.prefs().edit().putFloat(KEY_BUBBLE_CUSTOM_SIZE, value).apply()
    }

    fun customBubbleEdge(context: Context): Float =
        context.prefs().getFloat(KEY_BUBBLE_CUSTOM_EDGE, BubblePresets.DEFAULT_CUSTOM.edgeFraction)

    fun setCustomBubbleEdge(context: Context, value: Float) {
        context.prefs().edit().putFloat(KEY_BUBBLE_CUSTOM_EDGE, value).apply()
    }

    /** The resolved bubble size/edge fractions for the current preset (and custom values). */
    fun bubbleMetrics(context: Context): BubbleMetrics =
        BubblePresets.metrics(bubblePreset(context), customBubbleSize(context), customBubbleEdge(context))

    /** 0 = follow system, 1 = light, 2 = dark. */
    fun themeMode(context: Context): Int =
        SettingsRanges.themeMode(context.prefs().getInt(KEY_THEME_MODE, 0))

    fun setThemeMode(context: Context, mode: Int) {
        context.prefs().edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    /** The chosen accent as an ARGB color int (a preset from [Accents] or a custom pick). */
    fun accentColor(context: Context): Int =
        context.prefs().getInt(KEY_ACCENT_COLOR, Accents.colors[Accents.DEFAULT])

    fun setAccentColor(context: Context, color: Int) {
        context.prefs().edit().putInt(KEY_ACCENT_COLOR, color).apply()
    }

    /** Breathing wind-down phase durations in seconds (default 4-7-8: in 4, hold 7, out 8). */
    fun inhaleSeconds(context: Context): Int =
        SettingsRanges.breathSeconds(context.prefs().getInt(KEY_INHALE, DEFAULT_INHALE))
    fun holdSeconds(context: Context): Int =
        SettingsRanges.breathSeconds(context.prefs().getInt(KEY_HOLD, DEFAULT_HOLD))
    fun exhaleSeconds(context: Context): Int =
        SettingsRanges.breathSeconds(context.prefs().getInt(KEY_EXHALE, DEFAULT_EXHALE))

    fun setInhaleSeconds(context: Context, value: Int) {
        context.prefs().edit().putInt(KEY_INHALE, value).apply()
    }

    fun setHoldSeconds(context: Context, value: Int) {
        context.prefs().edit().putInt(KEY_HOLD, value).apply()
    }

    fun setExhaleSeconds(context: Context, value: Int) {
        context.prefs().edit().putInt(KEY_EXHALE, value).apply()
    }

    /** Seconds the breathing wind-down stays non-skippable before the action buttons appear. */
    fun lockSeconds(context: Context): Int =
        SettingsRanges.lockSeconds(context.prefs().getInt(KEY_LOCK, DEFAULT_LOCK))

    fun setLockSeconds(context: Context, value: Int) {
        context.prefs().edit().putInt(KEY_LOCK, value).apply()
    }

    /** Minutes the wind-down's "Snooze" action re-arms the timer for. */
    fun snoozeMinutes(context: Context): Int =
        SettingsRanges.snoozeMinutes(context.prefs().getInt(KEY_SNOOZE_MINUTES, DEFAULT_SNOOZE_MINUTES))

    fun setSnoozeMinutes(context: Context, value: Int) {
        context.prefs().edit().putInt(KEY_SNOOZE_MINUTES, value).apply()
    }

    /** Package names the "Stop for now" break should cover when they're opened. */
    fun blockedApps(context: Context): Set<String> =
        context.prefs().getStringSet(KEY_BLOCKED_APPS, emptySet())?.toSet() ?: emptySet()

    fun setBlockedApps(context: Context, packages: Set<String>) {
        // Store a fresh copy; the Set returned by getStringSet must not be mutated in place.
        context.prefs().edit().putStringSet(KEY_BLOCKED_APPS, HashSet(packages)).apply()
    }

    /** How many minutes a "Stop for now" break keeps the chosen apps covered. */
    fun blockMinutes(context: Context): Int =
        SettingsRanges.blockMinutes(context.prefs().getInt(KEY_BLOCK_MINUTES, DEFAULT_BLOCK_MINUTES))

    fun setBlockMinutes(context: Context, value: Int) {
        context.prefs().edit().putInt(KEY_BLOCK_MINUTES, value).apply()
    }

    /**
     * The media-stream volume saved when a pause muted it, or -1 when nothing is muted.
     * Persisted so that if the process is killed mid-pause (force-stop, low memory) the
     * next launch can restore the volume instead of leaving the user stranded at zero.
     */
    fun mutedVolume(context: Context): Int = context.prefs().getInt(KEY_MUTED_VOLUME, -1)

    @SuppressLint("ApplySharedPref") // committed synchronously so it survives an imminent kill
    fun setMutedVolume(context: Context, volume: Int) {
        context.prefs().edit().putInt(KEY_MUTED_VOLUME, volume).commit()
    }

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
