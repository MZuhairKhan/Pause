package io.github.mzuhairkhan.pause

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * [PauseState] is the SharedPreferences file [OverlayService] reads on a null-intent restart
 * (see [SessionRestore]), so what actually round-trips through it matters more than the pure
 * decision logic already covered by [SessionRestoreTest].
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PauseStateTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun freshInstallSnapshotIsAllZero() {
        val snapshot = PauseState.snapshot(app)
        assertEquals(0L, snapshot.timerStartMillis)
        assertEquals(0L, snapshot.timerEndMillis)
        assertEquals(0L, snapshot.breakUntilMillis)
    }

    @Test
    fun timerRoundTripsThroughSetAndClear() {
        PauseState.setTimer(app, 1_000L, 2_000L)
        val armed = PauseState.snapshot(app)
        assertEquals(1_000L, armed.timerStartMillis)
        assertEquals(2_000L, armed.timerEndMillis)

        PauseState.clearTimer(app)
        val cleared = PauseState.snapshot(app)
        assertEquals(0L, cleared.timerStartMillis)
        assertEquals(0L, cleared.timerEndMillis)
    }

    @Test
    fun breakRoundTripsThroughSetAndClear() {
        val packages = setOf("com.instagram.android", "com.zhiliaoapp.musically")
        PauseState.setBreak(app, 5_000L, packages)
        assertEquals(5_000L, PauseState.snapshot(app).breakUntilMillis)
        assertEquals(packages, PauseState.breakPackages(app))

        PauseState.clearBreak(app)
        assertEquals(0L, PauseState.snapshot(app).breakUntilMillis)
        assertTrue(PauseState.breakPackages(app).isEmpty())
    }

    @Test
    fun timerAndBreakStateAreIndependent() {
        // A break running alongside a snoozed timer must not clobber either's persisted state --
        // both scheduleTimer() and startBreakIfConfigured() write to this file independently.
        PauseState.setTimer(app, 10L, 20L)
        PauseState.setBreak(app, 30L, setOf("com.example.app"))

        val snapshot = PauseState.snapshot(app)
        assertEquals(10L, snapshot.timerStartMillis)
        assertEquals(20L, snapshot.timerEndMillis)
        assertEquals(30L, snapshot.breakUntilMillis)

        PauseState.clearTimer(app)
        val afterClearingTimer = PauseState.snapshot(app)
        assertEquals(0L, afterClearingTimer.timerEndMillis)
        assertEquals(30L, afterClearingTimer.breakUntilMillis)
    }
}
