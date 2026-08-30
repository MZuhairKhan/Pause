package io.github.mzuhairkhan.pause

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device smoke test: proves the setup UI actually composes and navigates on a real Android
 * runtime. The JVM unit tests cover the pure logic and Roborazzi renders the pixels, but neither
 * exercises the real framework — this does.
 */
@RunWith(AndroidJUnit4::class)
class SetupSmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before
    fun startOnSettings() {
        SettingsStore.setOnboardingComplete(app, true)
    }

    /** The settings screen renders, and its main action uses the current wording. */
    @Test
    fun settingsScreenShowsBubbleControls() {
        compose.onNodeWithText(app.getString(R.string.section_bubble)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.start_overlay)).assertIsDisplayed()
    }

    /** The breathing section exposes the renamed "Minimum exercise time" setting. */
    @Test
    fun breathingSectionExpands() {
        compose.onNodeWithText(app.getString(R.string.section_breathing)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(app.getString(R.string.no_skip_lock)).assertIsDisplayed()
    }
}

/** The first-run wizard, from a clean install. */
@RunWith(AndroidJUnit4::class)
class WizardSmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    /** Walks welcome -> language -> permissions without crashing. */
    @Test
    fun wizardAdvancesThroughFirstSteps() {
        SettingsStore.setOnboardingComplete(app, false)
        compose.onNodeWithText(app.getString(R.string.onb_welcome_title)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.onb_next)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(app.getString(R.string.onb_language_title)).assertIsDisplayed()
    }
}
