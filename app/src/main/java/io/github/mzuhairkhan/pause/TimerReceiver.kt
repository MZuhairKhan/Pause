package io.github.mzuhairkhan.pause

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires when a timer's alarm goes off and hands off to the overlay service for the
 * breathing wind-down. No reminder notification — the wind-down is the nudge.
 */
class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        OverlayService.timerFired(context)
    }

    companion object {
        const val ACTION_FIRE = "io.github.mzuhairkhan.pause.action.TIMER_FIRE"
    }
}
