package io.github.mzuhairkhan.pause

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * The Start button must stay usable when POST_NOTIFICATIONS is denied. Only the overlay
 * permission is load-bearing: `startForeground()` succeeds without the notification permission
 * and simply skips posting (`OverlayService.canPostNotifications()` guards that). Gating the
 * button on it made the bubble unstartable for anyone who denies notifications, with no way to
 * recover -- found by an external F-Droid reviewer testing on a real device.
 *
 * This lives on the JVM rather than in `SetupSmokeTest` on purpose. The instrumented version had
 * to revoke the real permission, and revoking one the app actually holds makes the platform kill
 * the owning process -- which is the instrumentation's own. That went unnoticed only because
 * POST_NOTIFICATIONS is *not* granted by default on the CI emulator, so the revoke was a no-op
 * and the test was passing vacuously. The moment `BubbleHideTest` started granting it (it has to,
 * to assert on notification actions) the revoke became real and killed the run mid-suite: an
 * empty `<failure>`, no message, and three tests that never started.
 *
 * Robolectric has no process to kill and fakes permission state directly, so the two tests no
 * longer fight over one device-wide setting.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class SettingsGatingTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun startButtonIsEnabledWithoutNotificationPermission() {
        ShadowSettings.setCanDrawOverlays(true)
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        // Assert the precondition rather than trusting it. Robolectric grants every manifest
        // permission by default, so a denial that silently failed to apply would leave this
        // passing while proving nothing -- which is exactly how the instrumented version hid a
        // no-op revoke for so long. sdk=34 matters here: below 33 there is no runtime permission
        // to deny at all.
        assertEquals(
            "POST_NOTIFICATIONS should be denied before the screen composes.",
            PackageManager.PERMISSION_DENIED,
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS)
        )

        SettingsStore.setOnboardingComplete(app, true)
        ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()

        compose.onNodeWithText(app.getString(R.string.start_overlay)).assertIsEnabled()
    }
}
