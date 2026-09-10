package io.github.mzuhairkhan.pause

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [PauseState], not `AlarmManager`, decides whether a wind-down is due.
 *
 * An F-Droid reviewer on GrapheneOS hit a timer that kept firing after it had been stopped: the
 * bubble was idle, nothing on screen said a timer existed, and the full-screen wind-down still
 * arrived -- over maps, mid-drive, in their example. Why that alarm outlived
 * `AlarmManager.cancel()` on that ROM is not something this machine can reproduce, so the guard
 * here doesn't depend on knowing: a broadcast with no persisted timer behind it is an orphan and
 * is dropped (and cleaned up) rather than trusted.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TimerReceiverTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val alarmManager get() = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @Before
    fun clearState() {
        PauseState.clearTimer(app)
        PauseState.clearBreak(app)
        shadowOf(app).clearStartedServices()
    }

    private fun fire() =
        TimerReceiver().onReceive(app, Intent(TimerReceiver.ACTION_FIRE))

    @Test
    fun `a fire with no persisted timer starts nothing`() {
        // Exactly the reviewer's case: the timer was stopped, but an alarm survived anyway.
        PauseAlarm.schedule(app, System.currentTimeMillis() + 60_000L)

        fire()

        assertNull(
            "an orphan alarm must not raise the wind-down over whatever the user is doing",
            shadowOf(app).nextStartedService
        )
        assertTrue(
            "and it must be cleaned up, not left to fire again",
            shadowOf(alarmManager).scheduledAlarms.isEmpty()
        )
    }

    @Test
    fun `a fire with a due persisted timer hands off to the service`() {
        val end = System.currentTimeMillis() - 1_000L
        PauseState.setTimer(app, end - 60_000L, end)

        fire()

        val started = shadowOf(app).nextStartedService
        assertEquals(OverlayService::class.java.name, started?.component?.className)
        assertEquals(OverlayService.ACTION_TIMER_FIRED, started?.action)
    }

    @Test
    fun `a fire well before its persisted deadline is re-armed, not honoured`() {
        val end = System.currentTimeMillis() + 30 * 60_000L
        PauseState.setTimer(app, System.currentTimeMillis(), end)

        fire()

        assertNull(
            "a wind-down half an hour early is worse than a late one",
            shadowOf(app).nextStartedService
        )
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
        assertEquals(end, shadowOf(alarmManager).scheduledAlarms[0].triggerAtMs)
        assertEquals("the timer itself survives", end, PauseState.snapshot(app).timerEndMillis)
    }

    @Test
    fun `an unrelated broadcast is ignored`() {
        val end = System.currentTimeMillis() - 1_000L
        PauseState.setTimer(app, end - 60_000L, end)

        TimerReceiver().onReceive(app, Intent(Intent.ACTION_SCREEN_ON))

        assertNull(shadowOf(app).nextStartedService)
        assertEquals(end, PauseState.snapshot(app).timerEndMillis)
    }
}
