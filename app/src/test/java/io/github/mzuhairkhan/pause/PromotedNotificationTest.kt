package io.github.mzuhairkhan.pause

import android.app.Application
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Android 16 only pins ("promotes") a notification that qualifies, and the gate is
 * [NotificationCompat.hasPromotableCharacteristics]. Requesting promotion is easy; *qualifying*
 * is the part that silently fails, so these assert the shape rather than trusting it.
 *
 * This mirrors how [OverlayService.buildNotification] configures the running notification. It is
 * a shape check, not a guarantee: promotion is a request the system may still decline, and OEM
 * skins are free to differ. Only a device confirms the real behaviour.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class PromotedNotificationTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun runningNotification(totalSeconds: Int, elapsedSeconds: Int) =
        NotificationCompat.Builder(app, "test")
            .setContentTitle("Pause")
            .setContentText("Alarm in 25m")
            .setSmallIcon(R.drawable.ic_hourglass)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setColorized(true)
            .setColor(0xFF2196F3.toInt())
            .setShortCriticalText("25m")
            .setStyle(
                NotificationCompat.ProgressStyle()
                    .addProgressSegment(NotificationCompat.ProgressStyle.Segment(totalSeconds))
                    .setProgress(elapsedSeconds)
            )
            .build()

    @Test
    fun runningNotificationQualifiesForPromotion() {
        val n = runningNotification(totalSeconds = 1500, elapsedSeconds = 300)
        assertTrue(
            "A running timer must qualify, or the pin is silently dropped",
            NotificationCompat.hasPromotableCharacteristics(n)
        )
        assertTrue(NotificationCompat.isRequestPromotedOngoing(n))
        assertEquals("25m", NotificationCompat.getShortCriticalText(n))
    }

    /** An idle notification deliberately does not ask to be promoted: it is not a live update. */
    @Test
    fun idleNotificationDoesNotRequestPromotion() {
        val n = NotificationCompat.Builder(app, "test")
            .setContentTitle("Pause is ready")
            .setContentText("Tap the bubble to set a timer.")
            .setSmallIcon(R.drawable.ic_hourglass)
            .setOngoing(true)
            .setRequestPromotedOngoing(false)
            .build()
        assertFalse(NotificationCompat.isRequestPromotedOngoing(n))
    }
}
