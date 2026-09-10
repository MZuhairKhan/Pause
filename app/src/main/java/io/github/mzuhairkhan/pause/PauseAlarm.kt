package io.github.mzuhairkhan.pause

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Owns the single `AlarmManager` alarm that fires a Pause timer, so every caller that needs to
 * schedule or cancel it rebuilds the exact same `PendingIntent`. `PendingIntent` identity is
 * `(requestCode, Intent.filterEquals)` -- a second, slightly different copy of this construction
 * anywhere else would silently fail to cancel the first's alarm rather than throwing.
 */
object PauseAlarm {
    private const val REQ_ALARM = 100
    private const val REQ_SHOW = 101

    /**
     * Arms (or re-arms) the alarm for [endMillis]. `setAlarmClock()` is treated as exact without
     * needing `SCHEDULE_EXACT_ALARM`, and it briefly allowlists the app so the broadcast delivers
     * on time. Swallows `SecurityException` -- some OEMs restrict alarm scheduling; the service's
     * per-second ticker fallback fires instead.
     */
    fun schedule(context: Context, endMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(endMillis, showActivityIntent(context)),
                operation(context)
            )
        } catch (e: SecurityException) {
            // Handled by the caller's ticker fallback.
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(operation(context))
    }

    /** The alarm's `PendingIntent` identity -- what it is scheduled and cancelled under. */
    fun operation(context: Context): PendingIntent {
        val intent = Intent(context, TimerReceiver::class.java).apply {
            action = TimerReceiver.ACTION_FIRE
        }
        return PendingIntent.getBroadcast(
            context,
            REQ_ALARM,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun showActivityIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            REQ_SHOW,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
    }
}
