package io.github.mzuhairkhan.pause

import android.os.Build
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

    /** A firmer tick for events that matter more than navigation: a timer set, a wind-down arriving. */
    fun confirm(view: View) {
        performer(view, confirmConstant(Build.VERSION.SDK_INT))
    }

    /** `CONFIRM` is API 30+; older devices get the closest key-press feel. */
    internal fun confirmConstant(sdkInt: Int): Int =
        if (sdkInt >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.VIRTUAL_KEY
}
