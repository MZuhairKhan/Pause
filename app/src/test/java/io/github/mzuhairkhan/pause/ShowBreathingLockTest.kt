package io.github.mzuhairkhan.pause

import android.app.Application
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl

/**
 * A lock of 0 (the user's "Skip right away") used to be a different code path from the deleted
 * breathing on/off switch: switching the exercise off hid the circle entirely behind a "Time's
 * up" headline, while a 0 s lock left the circle breathing and just skipped the wait. Collapsing
 * both into one 0 s minimum means the circle must always be what shows -- this pins that down so
 * a future edit to `showBreathing()` can't quietly bring the hidden-circle branch back.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ShowBreathingLockTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private var createdService: OverlayService? = null

    private fun newService(): OverlayService =
        Robolectric.buildService(OverlayService::class.java).create().get().also { createdService = it }

    /** The breathing overlay's root, found among the views actually handed to the WindowManager. */
    private fun breathingRoot(): View? {
        val shadowWm = Shadow.extract<ShadowWindowManagerImpl>(
            app.getSystemService(WindowManager::class.java)
        )
        return shadowWm.views.firstOrNull { it.findViewById<View>(R.id.breathing_circle) != null }
    }

    @After
    fun tearDown() {
        createdService?.onDestroy()
    }

    @Test
    fun `a zero second lock still shows the breathing circle and reveals actions right away`() {
        ShadowSettings.setCanDrawOverlays(true)
        SettingsStore.setLockSeconds(app, 0)
        val service = newService()

        service.onStartCommand(Intent(app, OverlayService::class.java).apply {
            action = OverlayService.ACTION_TIMER_FIRED
        }, 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        val root = breathingRoot()
        assertNotNull("precondition: the wind-down came up", root)
        assertEquals(
            "the circle must keep breathing at 0 s, not hide behind a headline",
            View.VISIBLE,
            root!!.findViewById<View>(R.id.breathing_circle).visibility
        )
        assertEquals(
            "skipping the lock should reveal the dismiss buttons without waiting",
            View.VISIBLE,
            root.findViewById<View>(R.id.breathing_actions).visibility
        )
    }
}
