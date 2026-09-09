package io.github.mzuhairkhan.pause

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-posts the persistent "Start Pause" notification after boot so the overlay can be
 * launched from the shade, and reconciles [PauseState] with the alarms a reboot wiped.
 * Does not start the service itself.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            restorePersistedState(context)
            OverlayService.showStartNotification(context)
        }
    }

    /**
     * A reboot clears every `AlarmManager` alarm but leaves [PauseState] untouched, so the two
     * disagree until something reconciles them -- and nothing did: a timer running when the
     * device rebooted stayed on disk with nothing left to fire it, showing as running on the
     * widget and to any later session restore. Re-arm it if its deadline is still ahead; drop
     * it if it passed while the device was off, because the moment to wind down has gone.
     *
     * A break is always dropped. Unlike the timer it has no alarm backing it at all -- the
     * cover and its poll live entirely inside a running service -- so after a reboot there is
     * nothing enforcing one and no honest way to resume it.
     */
    private fun restorePersistedState(context: Context) {
        PauseState.clearBreak(context)
        val snapshot = PauseState.snapshot(context)
        val decision = SessionRestore.decide(
            snapshot.timerStartMillis, snapshot.timerEndMillis, System.currentTimeMillis()
        )
        when (decision) {
            is SessionRestore.Decision.ResumeTimer -> PauseAlarm.schedule(context, decision.endMillis)
            SessionRestore.Decision.TimerExpiredWhileDead -> PauseState.clearTimer(context)
            SessionRestore.Decision.Idle -> Unit
        }
    }
}
