package io.github.mzuhairkhan.pause

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * The inhale/hold/exhale lengths used to be user settings. They are now fixed at 4-7-8, so a phone
 * that still has tuned values stored from an older version must ignore them rather than breathe
 * to a pattern the app no longer offers a way to change or even see.
 *
 * Asserted on the loop's total length rather than the phase on screen at some moment: Robolectric's
 * animator clock does not track the looper's, so a "what shows at 5 s" check proves nothing.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FixedBreathingPatternTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private var createdService: OverlayService? = null

    @After
    fun tearDown() {
        createdService?.onDestroy()
    }

    private fun seedLegacyTimings(inhale: Int, hold: Int, exhale: Int) {
        app.getSharedPreferences("pause_settings", Context.MODE_PRIVATE).edit()
            .putInt("breath_inhale", inhale)
            .putInt("breath_hold", hold)
            .putInt("breath_exhale", exhale)
            .commit()
    }

    /** Fires the timer so the wind-down starts, and returns how long its breathing loop is. */
    private fun breathingLoopMillis(): Long {
        ShadowSettings.setCanDrawOverlays(true)
        val service = Robolectric.buildService(OverlayService::class.java).create().get()
            .also { createdService = it }
        service.onStartCommand(Intent(app, OverlayService::class.java).apply {
            action = OverlayService.ACTION_TIMER_FIRED
        }, 0, 1)
        shadowOf(Looper.getMainLooper()).idle()
        return service.breathingCycleMillis
    }

    @Test
    fun `stored custom timings from an older version are ignored`() {
        seedLegacyTimings(inhale = 10, hold = 1, exhale = 1)

        assertEquals(19_000L, breathingLoopMillis())
    }

    @Test
    fun `with nothing stored the loop is the same 4-7-8`() {
        assertEquals(19_000L, breathingLoopMillis())
    }
}
