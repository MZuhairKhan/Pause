package io.github.mzuhairkhan.pause

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/** Light tap feedback for touches on the bubble, picker chips, and buttons. */
object Haptics {

    /** Sends the constant to the view. Swapped in tests, which can't feel a vibration. */
    internal var performer: (View, Int) -> Unit = { view, constant ->
        view.performHapticFeedback(constant)
    }

    /** A light confirmation tick. Uses the view's own feedback so it respects system settings. */
    fun tap(view: View) {
        performer(view, HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** A firmer tick for an event that matters more than navigation: a timer being set. */
    fun confirm(view: View) {
        performer(view, confirmConstant(Build.VERSION.SDK_INT))
    }

    /**
     * One click when the wind-down arrives. This is not a touch, so it cannot go through a view:
     * `performHapticFeedback` is for touches and Android drops it when nobody is touching, which a
     * phone showed (nothing reached the vibrator at the arrival). It is played as an alarm because
     * the person set the timer, so a silenced ringer should not swallow it, and because Android
     * only lets a non-foreground app vibrate with a ringtone, notification or alarm usage.
     */
    fun arrive(context: Context) {
        val vibrator = vibratorOf(context)
        if (vibrator == null || !vibrator.hasVibrator()) return
        // EFFECT_CLICK is API 29; before that, the shortest plain buzz.
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        } else {
            VibrationEffect.createOneShot(30L, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            )
        }
    }

    private fun vibratorOf(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** `CONFIRM` is API 30+; older devices get the closest key-press feel. */
    internal fun confirmConstant(sdkInt: Int): Int =
        if (sdkInt >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.VIRTUAL_KEY
}
