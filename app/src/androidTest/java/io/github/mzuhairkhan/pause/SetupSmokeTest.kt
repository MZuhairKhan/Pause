package io.github.mzuhairkhan.pause

import android.app.Application
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device smoke test: proves the setup UI composes and navigates on a real Android runtime.
 * The JVM unit tests cover the pure logic and Roborazzi renders the pixels; neither exercises
 * the real framework.
 *
 * Three things this file has to be careful about, all learned from CI:
 *
 *  - The activity is launched explicitly rather than by `createAndroidComposeRule`. That rule
 *    launches during its own `before`, which runs *earlier* than `@Before`, so seeding
 *    SharedPreferences there is too late to choose which screen composes.
 *  - SharedPreferences survive between tests in the same process, so every test seeds the state
 *    it depends on rather than assuming a clean install.
 *  - The CI emulator is small, so assertions scroll before asserting: `assertIsDisplayed` fails
 *    on content that exists but is below the fold.
 */
@RunWith(AndroidJUnit4::class)
class SetupSmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun launchSettings() {
        SettingsStore.setOnboardingComplete(app, true)
        ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    private fun shell(command: String) {
        val fd: ParcelFileDescriptor =
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        // Draining is what makes this synchronous; closing the descriptor early does not mean
        // the command has run (see OverlayBackTest for the same gotcha with appops grants).
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }

    private fun allowOverlays() {
        shell("appops set ${app.packageName} SYSTEM_ALERT_WINDOW allow")
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!Settings.canDrawOverlays(app) && SystemClock.uptimeMillis() < deadline) {
            Thread.sleep(100)
        }
    }

    /** The settings screen renders, and its main action uses the current wording. */
    @Test
    fun settingsScreenShowsBubbleControls() {
        launchSettings()
        // Near the top, so it should be on screen even on a small display.
        compose.onNodeWithText(app.getString(R.string.start_overlay)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.section_bubble))
            .performScrollTo()
            .assertIsDisplayed()
    }

    /**
     * Sections are disclosure panels that start *expanded*, so tapping the header collapses.
     * This walks collapse then expand, checking the renamed "Minimum exercise time" row follows.
     *
     * The row lives behind `if (breathingOn)`, so the toggle is seeded on: another test in the
     * same process could otherwise have turned it off and taken the row with it.
     */
    @Test
    fun breathingSectionCollapsesAndExpands() {
        SettingsStore.setBreathingEnabled(app, true)
        launchSettings()

        val header = app.getString(R.string.section_breathing)
        val row = app.getString(R.string.no_skip_lock)

        // Expanded by default, so the row is there before anything is tapped.
        compose.onNodeWithText(row).performScrollTo().assertIsDisplayed()

        compose.onNodeWithText(header).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(row).assertDoesNotExist()

        compose.onNodeWithText(header).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(row).performScrollTo().assertIsDisplayed()
    }

    /**
     * The Start button must work with only the overlay permission -- POST_NOTIFICATIONS is not
     * load-bearing. `startForeground()` succeeds without it; it only silently skips the
     * notification (`OverlayService.canPostNotifications()` already guards that). Gating the
     * button on it made the bubble unstartable for anyone who denies notifications, with no way
     * to recover -- found by an external F-Droid reviewer testing on a real device.
     *
     * Revokes POST_NOTIFICATIONS and grants the overlay permission independently via shell, since
     * the Gradle-installed test APK has every manifest permission auto-granted by default.
     */
    @Test
    fun startButtonWorksWithoutNotificationPermission() {
        shell("pm revoke ${app.packageName} android.permission.POST_NOTIFICATIONS")
        allowOverlays()
        try {
            launchSettings()
            compose.onNodeWithText(app.getString(R.string.start_overlay)).assertIsEnabled()
        } finally {
            // Restore so a later test in this process (they share SharedPreferences and, it
            // turns out, permission state too) does not inherit a revoked permission. The appop
            // needs resetting too: revoking the permission also denies POST_NOTIFICATION, and
            // re-granting the permission alone leaves notify() silently dropping everything,
            // which broke BubbleHideTest on API 33+ while API 26 (no runtime permission) passed.
            shell("pm grant ${app.packageName} android.permission.POST_NOTIFICATIONS")
            shell("appops set ${app.packageName} POST_NOTIFICATION allow")
        }
    }
}

/** The first-run wizard, from a clean install. */
@RunWith(AndroidJUnit4::class)
class WizardSmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    /** Walks welcome -> language without crashing. */
    @Test
    fun wizardAdvancesThroughFirstSteps() {
        // Set before launching, and explicitly, since a previous test may have completed setup.
        SettingsStore.setOnboardingComplete(app, false)
        ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()

        compose.onNodeWithText(app.getString(R.string.onb_welcome_title)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.onb_next)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(app.getString(R.string.onb_language_title)).assertIsDisplayed()
    }
}
