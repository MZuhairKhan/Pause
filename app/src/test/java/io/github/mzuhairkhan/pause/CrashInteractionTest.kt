package io.github.mzuhairkhan.pause

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.acra.config.CoreConfigurationBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * After a crash, ACRA hands the saved report to this and waits. It must never let ACRA send
 * anything (Pause has no internet permission and the person decides), and it must only notify for
 * a crash that just happened: ACRA runs interactions again at the next app start for the newest
 * unapproved report, and a notification on every launch would be a nag.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CrashInteractionTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val config = CoreConfigurationBuilder().build()

    private fun reportFile(ageMillis: Long): File =
        File(app.cacheDir, "crash-${System.nanoTime()}.json").apply {
            writeText("{}")
            setLastModified(System.currentTimeMillis() - ageMillis)
        }

    private val notifications get() = shadowOf(app.getSystemService(NotificationManager::class.java))

    @Test
    fun `a fresh crash posts a notification that opens the report screen`() {
        val file = reportFile(ageMillis = 2_000)

        CrashInteraction().performInteraction(app, config, file)

        val notification = notifications.allNotifications.single()
        val pending = notification.contentIntent
        assertNotNull(pending)
        assertTrue(shadowOf(pending).isActivityIntent)
        val intent = shadowOf(pending).savedIntent
        assertEquals(CrashReportActivity::class.java.name, intent.component?.className)
        assertEquals(file.path, intent.getStringExtra(CrashReportActivity.EXTRA_REPORT_PATH))
    }

    @Test
    fun `it never asks ACRA to send the report`() {
        assertFalse(CrashInteraction().performInteraction(app, config, reportFile(ageMillis = 2_000)))
        assertFalse(CrashInteraction().performInteraction(app, config, reportFile(ageMillis = 10 * 60_000)))
    }

    @Test
    fun `a report from earlier is not announced again at the next start`() {
        CrashInteraction().performInteraction(app, config, reportFile(ageMillis = 5 * 60_000))

        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test
    fun `the notification has a channel of its own`() {
        CrashInteraction().performInteraction(app, config, reportFile(ageMillis = 1_000))

        assertNotNull(app.getSystemService(NotificationManager::class.java).getNotificationChannel(CrashNotification.CHANNEL_ID))
    }
}
