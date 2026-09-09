package io.github.mzuhairkhan.pause

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the widget's quick-start chips and Cancel action. Deliberately never *starts*
 * `OverlayService`: writing [PauseState] and scheduling/cancelling the [PauseAlarm] directly is
 * enough for the timer to work headlessly (the bubble picks it up next time it's started, and
 * `TimerReceiver` fires the wind-down whether or not the service is up when the alarm goes off),
 * and it can't be refused the way a background service start can. `exported=false` -- only a
 * `PendingIntent` this app created can reach it.
 *
 * A service that *is* already running is told to re-read that state via
 * [OverlayService.syncState], so a Cancel tapped here doesn't leave a live instance ticking
 * toward a deadline it has already dropped.
 */
class PauseActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_QUICK_START -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, -1)
                if (minutes <= 0) return
                val end = System.currentTimeMillis() + minutes * 60_000L
                PauseState.setTimer(context, System.currentTimeMillis(), end)
                PauseAlarm.schedule(context, end)
                PauseWidgetProvider.refresh(context)
                OverlayService.syncState(context)
            }
            ACTION_CANCEL -> {
                PauseAlarm.cancel(context)
                PauseState.clearTimer(context)
                PauseWidgetProvider.refresh(context)
                OverlayService.syncState(context)
            }
        }
    }

    companion object {
        const val ACTION_QUICK_START = "io.github.mzuhairkhan.pause.action.WIDGET_QUICK_START"
        const val ACTION_CANCEL = "io.github.mzuhairkhan.pause.action.WIDGET_CANCEL"
        const val EXTRA_MINUTES = "minutes"
    }
}
