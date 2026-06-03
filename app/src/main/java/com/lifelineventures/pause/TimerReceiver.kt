package com.lifelineventures.pause

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires when a pause timer's AlarmManager alarm goes off and hands off to the overlay
 * service to open the breathing wind-down. No reminder notification — the wind-down is
 * the nudge.
 */
class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        OverlayService.timerFired(context)
    }

    companion object {
        const val ACTION_FIRE = "com.lifelineventures.pause.action.TIMER_FIRE"
    }
}
