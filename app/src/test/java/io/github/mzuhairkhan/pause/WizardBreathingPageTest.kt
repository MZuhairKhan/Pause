package io.github.mzuhairkhan.pause

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The wind-down is the app's most consequential default and the only one that takes control
 * away: it is on, and its buttons stay locked for 30 seconds. Nothing in setup said so, and an
 * F-Droid reviewer testing 0.5.0 could find no way out of it at all. This page makes the first
 * encounter something the user agreed to.
 *
 * Walks the real wizard rather than the composable, so the page's position in the flow is part
 * of what's asserted -- it has to come after bubble size and before the finish.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class WizardBreathingPageTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    /** Advances to the breathing page, which is the one after bubble size. */
    private fun openBreathingPage() {
        SettingsStore.setOnboardingComplete(app, false)
        ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        repeat(5) {
            compose.onNodeWithText(app.getString(R.string.onb_next)).performClick()
            compose.waitForIdle()
        }
    }

    @Test
    fun `the page sits between bubble size and the finish`() {
        openBreathingPage()

        compose.onNodeWithText(app.getString(R.string.onb_breathing_title)).assertIsDisplayed()
        // One more Next reaches the end, where the button becomes Get started.
        compose.onNodeWithText(app.getString(R.string.onb_next)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(app.getString(R.string.onb_get_started)).assertIsDisplayed()
    }

    @Test
    fun `the lock is disclosed, not left as a surprise`() {
        // The 30 seconds of dead buttons is the whole reason this page exists.
        openBreathingPage()

        compose.onNodeWithText(app.getString(R.string.no_skip_lock)).assertIsDisplayed()
    }

    /**
     * `SwitchRow` puts its label and its `Switch` in separate semantics nodes -- the label is
     * plain text and carries no toggleable state -- so the switch is addressed by role.
     * The page has exactly one.
     */
    private fun toggle() = compose.onNode(isToggleable())

    @Test
    fun `the exercise starts on, matching the shipped default`() {
        openBreathingPage()

        compose.onNodeWithText(app.getString(R.string.breathing_toggle)).assertIsDisplayed()
        toggle().assertIsOn()
        assertTrue(SettingsStore.breathingEnabled(app))
    }

    @Test
    fun `turning it off here persists and hides the controls it moots`() {
        openBreathingPage()

        toggle().performClick()
        compose.waitForIdle()

        toggle().assertIsOff()
        assertFalse(
            "the wizard writes straight through, like the bubble-size page beside it",
            SettingsStore.breathingEnabled(app)
        )
        // Same rule the settings screen follows: a minimum time is meaningless with no exercise.
        compose.onNodeWithText(app.getString(R.string.no_skip_lock)).assertDoesNotExist()
    }

    @Test
    fun `the lock can be set to zero before ever meeting it`() {
        openBreathingPage()
        val decrease = app.getString(R.string.stepper_decrease, app.getString(R.string.no_skip_lock))

        // The stepper moves a second at a time; the extra taps also prove the floor clamps.
        repeat(SettingsDefaults.LOCK_SECONDS + 2) {
            compose.onNodeWithContentDescription(decrease).performClick()
        }
        compose.waitForIdle()

        assertTrue(
            "someone the lock bothers must be able to remove it here, not just uninstall",
            SettingsStore.lockSeconds(app) == SettingsRanges.LOCK_MIN_SECONDS
        )
    }
}
