package io.github.mzuhairkhan.pause

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device smoke test: proves the setup UI composes and navigates on a real Android runtime.
 * The JVM unit tests cover the pure logic and Roborazzi renders the pixels; neither exercises
 * the real framework.
 *
 * Two things this file has to be careful about, both learned the hard way in CI:
 *
 *  - The activity is launched explicitly rather than by `createAndroidComposeRule`. That rule
 *    launches during its own `before`, which runs *earlier* than `@Before`, so seeding
 *    SharedPreferences there is too late to choose which screen composes.
 *  - SharedPreferences survive between tests in the same process, so every test sets the
 *    onboarding flag itself instead of relying on a clean install.
 *
 * Assertions scroll before asserting: the CI emulator is only 320dp wide by 640dp tall, so most
 * of the settings screen sits below the fold and `assertIsDisplayed` would fail on content that
 * is present but off-screen.
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

    /** The breathing section exposes the renamed "Minimum exercise time" setting. */
    @Test
    fun breathingSectionExpands() {
        launchSettings()
        compose.onNodeWithText(app.getString(R.string.section_breathing))
            .performScrollTo()
            .performClick()
        compose.waitForIdle()
        compose.onNodeWithText(app.getString(R.string.no_skip_lock))
            .performScrollTo()
            .assertIsDisplayed()
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
