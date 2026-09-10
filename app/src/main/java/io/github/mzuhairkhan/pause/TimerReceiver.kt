package io.github.mzuhairkhan.pause

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires when a timer's alarm goes off and hands off to the overlay service for the
 * breathing wind-down. No reminder notification — the wind-down is the nudge.
 *
 * What it will *not* do is take the broadcast's word for it. [PauseState] is what says a timer
 * is running, so a broadcast that disagrees with it is dropped: see [TimerFire].
 */
class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val end = PauseState.snapshot(context).timerEndMillis
        when (TimerFire.decide(end, System.currentTimeMillis())) {
            TimerFire.Decision.FIRE -> OverlayService.timerFired(context)
            // Cancel as well as ignore: something has to break the cycle, and the cancel that
            // should already have removed this alarm evidently didn't.
            TimerFire.Decision.ORPHAN -> PauseAlarm.cancel(context)
            TimerFire.Decision.TOO_EARLY -> PauseAlarm.schedule(context, end)
        }
    }

    companion object {
        const val ACTION_FIRE = "io.github.mzuhairkhan.pause.action.TIMER_FIRE"
    }
}
