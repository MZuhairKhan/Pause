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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The wind-down is the app's most consequential default and the only one that takes control
 * away: its buttons stay locked for 30 seconds. Nothing in setup said so, and an F-Droid
 * reviewer testing 0.5.0 could find no way out of it at all. This page makes the first
 * encounter something the user agreed to -- and, unlike the on/off switch that used to live
 * here, always leaves the exercise itself running: the only choice is how long the lock lasts,
 * down to skipping it (a lock of 0) with the same switch.
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
    fun `the lock starts on, matching the shipped default`() {
        openBreathingPage()

        compose.onNodeWithText(app.getString(R.string.breathing_skip)).assertIsDisplayed()
        toggle().assertIsOff()
        assertEquals(SettingsDefaults.LOCK_SECONDS, SettingsStore.lockSeconds(app))
    }

    @Test
    fun `skipping here persists and hides the stepper it moots`() {
        openBreathingPage()

        toggle().performClick()
        compose.waitForIdle()

        toggle().assertIsOn()
        assertEquals(
            "the wizard writes straight through, like the bubble-size page beside it",
            0,
            SettingsStore.lockSeconds(app)
        )
        compose.onNodeWithText(app.getString(R.string.no_skip_lock)).assertDoesNotExist()
    }

    @Test
    fun `switching skip back off restores the value it replaced`() {
        openBreathingPage()

        toggle().performClick()
        compose.waitForIdle()
        toggle().performClick()
        compose.waitForIdle()

        toggle().assertIsOff()
        assertEquals(
            "turning skip back off should return to what the lock held before, not the default",
            SettingsDefaults.LOCK_SECONDS,
            SettingsStore.lockSeconds(app)
        )
        compose.onNodeWithText(app.getString(R.string.no_skip_lock)).assertIsDisplayed()
    }

    @Test
    fun `stepping the lock down to zero reaches the same skip state as the switch`() {
        openBreathingPage()
        val decrease = app.getString(R.string.stepper_decrease, app.getString(R.string.no_skip_lock))

        // The stepper moves a second at a time; the last tap crosses into skip and the stepper
        // that click landed on disappears, so nothing further can be clicked past it.
        repeat(SettingsDefaults.LOCK_SECONDS) {
            compose.onNodeWithContentDescription(decrease).performClick()
        }
        compose.waitForIdle()

        assertEquals(
            "someone the lock bothers must be able to remove it here, not just uninstall",
            SettingsRanges.LOCK_MIN_SECONDS,
            SettingsStore.lockSeconds(app)
        )
        toggle().assertIsOn()
    }
}
