package io.github.mzuhairkhan.pause

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A reboot clears every `AlarmManager` alarm but not [PauseState], so the two disagree until
 * something reconciles them. Without that, a timer running when the device rebooted stays on
 * disk forever: every reader (the widget, a later session restore) shows it as running while
 * nothing is left to fire it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BootReceiverTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val alarmManager get() = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun boot() = BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))

    @Test
    fun `boot re-arms a timer whose deadline is still ahead`() {
        val end = System.currentTimeMillis() + 30 * 60_000L
        PauseState.setTimer(app, System.currentTimeMillis(), end)
        assertTrue("precondition: a reboot left no alarms", shadowOf(alarmManager).scheduledAlarms.isEmpty())

        boot()

        assertEquals(
            "the user's timer must survive a reboot, not just look like it did",
            1,
            shadowOf(alarmManager).scheduledAlarms.size
        )
        assertEquals(end, shadowOf(alarmManager).scheduledAlarms[0].triggerAtMs)
        assertEquals(end, PauseState.snapshot(app).timerEndMillis)
    }

    @Test
    fun `boot drops a timer whose deadline passed while the device was off`() {
        val end = System.currentTimeMillis() - 60_000L
        PauseState.setTimer(app, end - 60_000L, end)

        boot()

        assertTrue(
            "the moment to wind down is gone; don't arm an alarm in the past",
            shadowOf(alarmManager).scheduledAlarms.isEmpty()
        )
        assertEquals(0L, PauseState.snapshot(app).timerEndMillis)
    }

    @Test
    fun `boot drops a break, which nothing enforces across a reboot`() {
        PauseState.setBreak(app, System.currentTimeMillis() + 10 * 60_000L, setOf("com.example.blocked"))

        boot()

        assertEquals(0L, PauseState.snapshot(app).breakUntilMillis)
        assertTrue(PauseState.breakPackages(app).isEmpty())
    }

    @Test
    fun `boot with nothing persisted arms nothing`() {
        PauseState.clearTimer(app)
        PauseState.clearBreak(app)

        boot()

        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Test
    fun `an unrelated broadcast does not touch persisted state`() {
        val end = System.currentTimeMillis() + 30 * 60_000L
        PauseState.setTimer(app, System.currentTimeMillis(), end)

        BootReceiver().onReceive(app, Intent(Intent.ACTION_SCREEN_ON))

        assertEquals(end, PauseState.snapshot(app).timerEndMillis)
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }
}
