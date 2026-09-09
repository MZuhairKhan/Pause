package io.github.mzuhairkhan.pause

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the widget's quick-start chips and Cancel action. Deliberately never touches
 * `OverlayService`: writing [PauseState] and scheduling/cancelling the [PauseAlarm] directly is
 * enough for the timer to work correctly and headlessly (the bubble picks it up next time it's
 * started, and `TimerReceiver` fires the wind-down regardless of whether the service is up when
 * the alarm goes off). `exported=false` -- only a `PendingIntent` this app created can reach it.
 *
 * Known gap: if the bubble is already showing when a quick-start or cancel fires, it does not
 * repaint until the service is next restarted. Fixing that means an in-process sync receiver
 * inside `OverlayService`; left out here as more than this needs to correctly show the timer.
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
            }
            ACTION_CANCEL -> {
                PauseAlarm.cancel(context)
                PauseState.clearTimer(context)
                PauseWidgetProvider.refresh(context)
            }
        }
    }

    companion object {
        const val ACTION_QUICK_START = "io.github.mzuhairkhan.pause.action.WIDGET_QUICK_START"
        const val ACTION_CANCEL = "io.github.mzuhairkhan.pause.action.WIDGET_CANCEL"
        const val EXTRA_MINUTES = "minutes"
    }
}
