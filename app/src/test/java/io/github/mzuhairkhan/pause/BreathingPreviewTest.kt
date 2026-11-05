package io.github.mzuhairkhan.pause

import android.app.Application
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The setup wizard's breathing preview is a guide to the 4-7-8 pattern. Compose scales animation
 * time by the system animator scale, so with animations removed it used to sit on one frame: a phone
 * showed "Breathe out" for as long as the page was open.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BreathingPreviewTest {

    /** What "Remove animations" hands every Compose animation. */
    private object AnimationsOff : MotionDurationScale {
        override val scaleFactor: Float = 0f
    }

    @get:Rule
    val compose = createComposeRule(effectContext = AnimationsOff)

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `the preview keeps breathing when the system animations are off`() {
        // Infinite animations only run while the test clock is driven by hand.
        compose.mainClock.autoAdvance = false
        compose.setContent { BreathingPreview(accentColor = 0xFF4488FF.toInt()) }

        compose.mainClock.advanceTimeBy(5_000)

        compose.onNodeWithText(app.getString(R.string.breathing_hold)).assertExists()
    }
}
