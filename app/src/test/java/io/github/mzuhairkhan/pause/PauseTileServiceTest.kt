package io.github.mzuhairkhan.pause

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Below API 34, [PauseTileService] must use the deprecated `Intent` overload of
 * `startActivityAndCollapse` -- see [TileLaunchTest][io.github.mzuhairkhan.pause.TileLaunchTest]
 * for the pure decision, and its KDoc for why the API 34+ `PendingIntent` overload is not
 * exercised here: it reaches through to the real, un-shadowed implementation under Robolectric
 * (confirmed by a throwaway probe -- an unbound `mService` NPEs, not the platform's own
 * `UnsupportedOperationException`), so nothing meaningful about it can be asserted off-device.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class PauseTileServiceTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun newTile() = Robolectric.buildService(PauseTileService::class.java).create().get()

    @Test
    fun `tapping the tile unlocked opens the launch trampoline`() {
        val service = newTile()
        shadowOf(service).setLocked(false)

        service.onClick()

        val started = shadowOf(app).nextStartedActivity
        assertEquals(
            "must open PauseLaunchActivity, not the picker or MainActivity directly",
            PauseLaunchActivity::class.java.name,
            started?.component?.className
        )
    }

    @Test
    fun `tapping the tile locked unlocks before opening`() {
        val service = newTile()
        shadowOf(service).setLocked(true)

        service.onClick()

        val started = shadowOf(app).nextStartedActivity
        assertEquals(
            "a locked device must still reach the trampoline once unlocked, not silently no-op",
            PauseLaunchActivity::class.java.name,
            started?.component?.className
        )
    }
}
