package io.github.mzuhairkhan.pause

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import java.io.File
import org.acra.config.CoreConfiguration
import org.acra.interaction.ReportInteraction

/**
 * ACRA hands a saved crash to this and waits. It is registered in
 * `META-INF/services/org.acra.interaction.ReportInteraction`, so it must stay public with a
 * no-argument constructor.
 *
 * It returns false on purpose, always: ACRA then never sends the report. Pause has no internet
 * permission, and what happens to a crash is the person's decision on [CrashReportActivity].
 */
class CrashInteraction : ReportInteraction {
    override fun performInteraction(context: Context, config: CoreConfiguration, reportFile: File): Boolean {
        // At the next app start ACRA runs interactions again for the newest unapproved report. A
        // notification on every launch until the report is dealt with would be a nag, so only a
        // crash that just happened gets one; an older one is still offered from Feedback.
        if (System.currentTimeMillis() - reportFile.lastModified() <= FRESH_MILLIS) {
            CrashNotification.post(context, reportFile)
        }
        return false
    }

    private companion object {
        const val FRESH_MILLIS = 60_000L
    }
}

/** The "Pause stopped unexpectedly" notification, on a channel of its own. */
internal object CrashNotification {
    const val CHANNEL_ID = "pause_crash"
    private const val NOTIFICATION_ID = 2

    /**
     * Removes it once its report has been reported or discarded. Android's auto-cancel only covers
     * a tap on the notification itself, and on a phone it did not even do that when the tap came
     * through a grouped notification, so a handled crash kept offering "no longer available".
     */
    fun clear(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    fun post(context: Context, reportFile: File) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.crash_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.crash_channel_description) }
        )
        // A direct activity PendingIntent, not a broadcast or service that starts one: Android 12+
        // blocks that kind of notification trampoline.
        val open = PendingIntent.getActivity(
            context,
            0,
            CrashReportActivity.intent(context, reportFile),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hourglass)
            .setContentTitle(context.getString(R.string.crash_notification_title))
            .setContentText(context.getString(R.string.crash_notification_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
