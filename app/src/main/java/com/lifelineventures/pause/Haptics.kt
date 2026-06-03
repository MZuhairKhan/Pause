package com.lifelineventures.pause

import android.view.HapticFeedbackConstants
import android.view.View

/** Light tap feedback for touches on the bubble, picker chips, and buttons. */
object Haptics {

    /** A light confirmation tick. Uses the view's own feedback so it respects system settings. */
    fun tap(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
}
