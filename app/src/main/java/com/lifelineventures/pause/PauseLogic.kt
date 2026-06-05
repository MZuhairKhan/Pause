package com.lifelineventures.pause

import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure helpers extracted from the overlay so the fiddly bits — time formatting, the
 * hourglass fill remap, bubble placement, and settings clamping — can be unit-tested
 * without an Android runtime. No class here touches a [android.content.Context] or any
 * framework type, so the tests run on a plain JVM.
 */

/** Formats a remaining duration as `m:ss`, or `h:mm:ss` once it reaches an hour. */
object TimeFormat {
    fun remainingLong(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        } else {
            String.format(Locale.US, "%d:%02d", m, sec)
        }
    }
}

/**
 * The draining-hourglass fill remap. [progress] is the fraction of time remaining
 * (1 = just started, 0 = finished). The visible fill is squeezed into
 * [[END_FILL], [START_FILL]] so the glyph never reads fully full or empty, and the
 * sand surface tracks the square root of the volume for the conical taper.
 */
object HourglassMath {
    const val START_FILL = 0.80f
    const val END_FILL = 0.06f

    fun fill(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return END_FILL + (START_FILL - END_FILL) * p
    }

    fun surface(progress: Float): Float = sqrt(fill(progress))
}

/**
 * Converts between the bubble's stored fractional position (0..1 of the draggable area)
 * and on-screen pixels. Storing a fraction rather than absolute pixels keeps the bubble
 * at the same relative spot across orientations.
 */
object BubblePosition {
    fun toPixels(fraction: Float, max: Int): Int =
        (fraction.coerceIn(0f, 1f) * max).roundToInt().coerceIn(0, max.coerceAtLeast(0))

    fun toFraction(pixel: Int, max: Int): Float =
        (pixel.toFloat() / max.coerceAtLeast(1)).coerceIn(0f, 1f)
}

/**
 * Valid ranges for the user-tunable settings, used both to clamp values read back from
 * storage (so a corrupt or restored pref can't feed a negative/huge value into an
 * animation or alarm) and to bound the setup-screen steppers.
 */
object SettingsRanges {
    const val BREATH_MIN_SECONDS = 1
    const val BREATH_MAX_SECONDS = 20
    const val LOCK_MIN_SECONDS = 0
    const val LOCK_MAX_SECONDS = 60
    const val BLOCK_MIN_MINUTES = 1
    const val BLOCK_MAX_MINUTES = 120
    const val SNOOZE_MIN_MINUTES = 1
    const val SNOOZE_MAX_MINUTES = 60
    const val THEME_MIN = 0
    const val THEME_MAX = 2

    fun breathSeconds(value: Int): Int = value.coerceIn(BREATH_MIN_SECONDS, BREATH_MAX_SECONDS)
    fun lockSeconds(value: Int): Int = value.coerceIn(LOCK_MIN_SECONDS, LOCK_MAX_SECONDS)
    fun blockMinutes(value: Int): Int = value.coerceIn(BLOCK_MIN_MINUTES, BLOCK_MAX_MINUTES)
    fun snoozeMinutes(value: Int): Int = value.coerceIn(SNOOZE_MIN_MINUTES, SNOOZE_MAX_MINUTES)
    fun themeMode(value: Int): Int = value.coerceIn(THEME_MIN, THEME_MAX)
    fun fraction(value: Float): Float = if (value.isNaN()) 0f else value.coerceIn(0f, 1f)
}
