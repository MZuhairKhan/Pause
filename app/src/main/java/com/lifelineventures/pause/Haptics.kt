package com.lifelineventures.pause

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Small haptics helper. [tap] is a light confirmation tick for ordinary touches
 * (bubble, chips, buttons); [timerFinished] is a distinct three-pulse buzz played
 * once when a timer ends, so the wind-down is felt even if the screen is off.
 */
object Haptics {

    /** A light confirmation tick. Uses the view's own feedback so it respects system settings. */
    fun tap(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** A distinct rising three-pulse pattern, played once when a timer finishes. */
    fun timerFinished(context: Context) {
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        // off, buzz, gap, buzz, gap, longer/stronger buzz — a deliberate "your time's up".
        val timings = longArrayOf(0, 180, 110, 180, 110, 340)
        val amplitudes = intArrayOf(0, 170, 0, 210, 0, 255)
        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
