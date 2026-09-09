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
 * [PauseActionReceiver] never touches [OverlayService] -- it only has to get [PauseState] and
 * the [PauseAlarm] right, which is enough for the timer to work headlessly. These assert on
 * exactly that boundary, the same way [OverlayServiceTest] asserts on the service's.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PauseActionReceiverTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val alarmManager get() = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun receiver() = PauseActionReceiver()

    @Test
    fun `a quick-start chip arms a timer for the requested minutes`() {
        val intent = Intent(PauseActionReceiver.ACTION_QUICK_START).apply {
            putExtra(PauseActionReceiver.EXTRA_MINUTES, 10)
        }
        receiver().onReceive(app, intent)

        val snapshot = PauseState.snapshot(app)
        val expectedEnd = System.currentTimeMillis() + 10 * 60_000L
        assertTrue(
            "the persisted deadline must be about 10 minutes out",
            kotlin.math.abs(snapshot.timerEndMillis - expectedEnd) < 2_000L
        )
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Test
    fun `each quick-start replaces the previous duration rather than stacking`() {
        // The receiver itself always reschedules the single REQ_ALARM identity, by design --
        // that's correct here, since a new chip tap means "start over with a new duration", not
        // "queue a second timer". The chips' own PendingIntent distinctness (request code 111/
        // 112/113 plus a distinct data Uri per chip) lives in PauseWidgetRenderer instead: three
        // chips sharing one identity would let FLAG_UPDATE_CURRENT silently rewrite which
        // duration a chip's *own* PendingIntent carries, which this test cannot observe from
        // here -- there is no public API to read back what a built RemoteViews action carries.
        val r = receiver()
        r.onReceive(app, Intent(PauseActionReceiver.ACTION_QUICK_START).putExtra(PauseActionReceiver.EXTRA_MINUTES, 5))
        val after5 = PauseState.snapshot(app).timerEndMillis
        r.onReceive(app, Intent(PauseActionReceiver.ACTION_QUICK_START).putExtra(PauseActionReceiver.EXTRA_MINUTES, 15))
        val after15 = PauseState.snapshot(app).timerEndMillis

        assertTrue("15 minutes must persist a later deadline than 5 minutes did", after15 > after5)
        assertEquals(
            "two chip taps must leave exactly one alarm armed, not stack a second one",
            1,
            shadowOf(alarmManager).scheduledAlarms.size
        )
    }

    @Test
    fun `cancel clears the persisted timer and the armed alarm`() {
        PauseAlarm.schedule(app, System.currentTimeMillis() + 30 * 60_000L)
        PauseState.setTimer(app, System.currentTimeMillis(), System.currentTimeMillis() + 30 * 60_000L)
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)

        receiver().onReceive(app, Intent(PauseActionReceiver.ACTION_CANCEL))

        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
        assertEquals(0L, PauseState.snapshot(app).timerEndMillis)
    }

    @Test
    fun `a missing minutes extra is ignored rather than arming a garbage timer`() {
        receiver().onReceive(app, Intent(PauseActionReceiver.ACTION_QUICK_START))

        assertEquals(0L, PauseState.snapshot(app).timerEndMillis)
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }
}
