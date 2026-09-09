package io.github.mzuhairkhan.pause

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * [PauseLaunchActivity] is a transparent trampoline: its only job is to start the overlay
 * service (or route to [MainActivity] for permission setup) from an already-foreground
 * activity context, then finish immediately. Both the Quick Settings tile and the widget's
 * body tap use it so their `startForegroundService` call is never a background-start one.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PauseLaunchActivityTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `opens the picker and finishes when overlay permission is already granted`() {
        ShadowSettings.setCanDrawOverlays(true)

        val controller = Robolectric.buildActivity(PauseLaunchActivity::class.java)
        val activity = controller.create().get()

        val startedService = shadowOf(app).nextStartedService
        assertEquals(
            "must open the picker via OverlayService, not start it plainly",
            OverlayService.ACTION_SHOW_PICKER,
            startedService?.action
        )
        assertNull("must not also route to MainActivity", shadowOf(app).nextStartedActivity)
        assertTrue("must finish immediately -- it has no UI of its own", activity.isFinishing)
    }

    @Test
    fun `routes to MainActivity and finishes when overlay permission is missing`() {
        ShadowSettings.setCanDrawOverlays(false)

        val controller = Robolectric.buildActivity(PauseLaunchActivity::class.java)
        val activity = controller.create().get()

        val startedActivity = shadowOf(app).nextStartedActivity
        assertEquals(
            "without overlay permission, must route to the app's own permission-grant UI",
            MainActivity::class.java.name,
            startedActivity?.component?.className
        )
        assertNull("must not have started the overlay service", shadowOf(app).nextStartedService)
        assertTrue(activity.isFinishing)
    }
}
