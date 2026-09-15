package io.github.mzuhairkhan.pause

import android.app.Application
import android.content.Intent
import android.view.WindowManager
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl

/**
 * Proves `ACTION_SHOW_PICKER` -- what the Quick Settings tile and the widget's launch
 * trampoline send to open the picker -- follows the same "don't disturb a running timer"
 * contract as `ACTION_REFRESH_BUBBLE`, rather than the default explicit-start behavior (which
 * deliberately resets to idle; see [OverlayServiceTest]).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ShowPickerActionTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private var createdService: OverlayService? = null

    private fun newService(): OverlayService =
        Robolectric.buildService(OverlayService::class.java).create().get().also { createdService = it }

    /** The bubble's glyph view, found among the overlays actually handed to the WindowManager. */
    private fun bubbleIcon(): ImageView? {
        val shadowWm = Shadow.extract<ShadowWindowManagerImpl>(
            app.getSystemService(WindowManager::class.java)
        )
        return shadowWm.views.firstNotNullOfOrNull { it.findViewById<ImageView>(R.id.bubble_icon) }
    }

    @After
    fun tearDown() {
        createdService?.onDestroy()
        createdService = null
    }

    @Test
    fun `ACTION_SHOW_PICKER does not reset a running timer`() {
        ShadowSettings.setCanDrawOverlays(true)
        val end = System.currentTimeMillis() + 30 * 60_000L
        // Get a timer into the live instance the way OverlayServiceTest does: persist it, then
        // resume it via the null-intent restart path -- this service was never a fresh start.
        PauseState.setTimer(app, System.currentTimeMillis(), end)
        val service = newService()
        service.onStartCommand(null, 0, 1)
        assertEquals("precondition: the timer resumed", end, PauseState.snapshot(app).timerEndMillis)

        service.onStartCommand(Intent(app, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW_PICKER
        }, 0, 2)

        assertEquals(
            "ACTION_SHOW_PICKER must not reset a timer that's already running",
            end,
            PauseState.snapshot(app).timerEndMillis
        )
    }

    @Test
    fun `ACTION_SHOW_PICKER on an idle service still starts it`() {
        ShadowSettings.setCanDrawOverlays(true)
        val service = newService()

        service.onStartCommand(Intent(app, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW_PICKER
        }, 0, 1)

        assertEquals(0L, PauseState.snapshot(app).timerEndMillis)
        org.junit.Assert.assertTrue(OverlayService.running.value)
    }

    /**
     * The tile branch returns before the state setters that swap the real glyph in, so a bubble
     * it created kept the placeholder `android:src` from `overlay_bubble.xml`: a bare vector,
     * with no [ShadowDrawable] around it. That costs the drop shadow the white glyph relies on
     * to stay legible on a light background, and -- since the wrapper is also what insets the
     * icon inside its blur margin -- draws it across the whole bubble window instead.
     */
    @Test
    fun `the tile's bubble gets the real glyph, not the layout placeholder`() {
        ShadowSettings.setCanDrawOverlays(true)
        val service = newService()

        service.onStartCommand(Intent(app, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW_PICKER
        }, 0, 1)

        val icon = bubbleIcon()
        assertNotNull("precondition: the tile put a bubble up", icon)
        assertTrue(
            "the tile's glyph needs the same shadow wrapper every other start path gives it",
            icon!!.drawable is ShadowDrawable
        )
    }
}
