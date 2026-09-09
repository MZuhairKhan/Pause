package io.github.mzuhairkhan.pause

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * The widget's actions change [PauseState] and the [PauseAlarm] without going through
 * [OverlayService], so a service that is already running keeps its own in-memory deadline
 * unless something tells it to re-read. Without that, a Cancel tapped on the widget cleared
 * the alarm and disk state while the live service carried on ticking and still fired the
 * wind-down at the original deadline.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WidgetServiceSyncTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val alarmManager get() = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager get() =
        app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var createdService: OverlayService? = null

    private fun newService(): OverlayService =
        Robolectric.buildService(OverlayService::class.java).create().get().also { createdService = it }

    private fun postedTitle(): String? =
        shadowOf(notificationManager).getNotification(1)?.extras?.getString("android.title")

    @After
    fun tearDown() {
        createdService?.onDestroy()
        createdService = null
    }

    @Test
    fun `cancelling from the widget stops a live service's timer, not just the alarm`() {
        ShadowSettings.setCanDrawOverlays(true)
        val end = System.currentTimeMillis() + 30 * 60_000L
        PauseState.setTimer(app, System.currentTimeMillis(), end)
        PauseAlarm.schedule(app, end)

        newService().onStartCommand(null, 0, 1)
        assertEquals(
            "precondition: the service shows a running timer",
            app.getString(R.string.overlay_notification_title_running),
            postedTitle()
        )

        PauseActionReceiver().onReceive(app, Intent(PauseActionReceiver.ACTION_CANCEL))
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("the alarm is gone", shadowOf(alarmManager).scheduledAlarms.isEmpty())
        assertEquals(0L, PauseState.snapshot(app).timerEndMillis)
        assertEquals(
            "the live service must drop the timer too, or its ticker still fires the wind-down",
            app.getString(R.string.overlay_notification_title),
            postedTitle()
        )
    }

    @Test
    fun `a quick-start from the widget reaches a live service`() {
        ShadowSettings.setCanDrawOverlays(true)
        newService().onStartCommand(Intent(app, OverlayService::class.java), 0, 1)
        assertEquals(
            "precondition: the service is idle",
            app.getString(R.string.overlay_notification_title),
            postedTitle()
        )

        PauseActionReceiver().onReceive(
            app,
            Intent(PauseActionReceiver.ACTION_QUICK_START)
                .putExtra(PauseActionReceiver.EXTRA_MINUTES, 10)
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            "the bubble must pick up a timer started from the widget",
            app.getString(R.string.overlay_notification_title_running),
            postedTitle()
        )
    }

    @Test
    fun `the small widget shows the draining glyph even with the countdown setting on`() {
        // Small has no text at all, so falling back to the idle glyph while a timer runs makes
        // it pixel-identical to idle. It must always draw the draining hourglass.
        SettingsStore.setShowCountdown(app, true)
        val now = System.currentTimeMillis()
        val rv = PauseWidgetRenderer.build(
            app,
            WidgetSize.SMALL,
            PauseSnapshot(now, now + 90_000L, 0L)
        )
        val view = rv.apply(app, FrameLayout(app))

        assertTrue(
            "a running timer on the small widget must rasterize the hourglass, not show the idle icon",
            view.findViewById<ImageView>(R.id.widget_glyph).drawable is BitmapDrawable
        )
    }
}
