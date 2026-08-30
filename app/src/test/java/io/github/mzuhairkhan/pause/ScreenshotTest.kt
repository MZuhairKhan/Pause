package io.github.mzuhairkhan.pause

import android.app.Activity
import android.app.Application
import android.view.LayoutInflater
import android.view.View
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real screens to PNGs on the JVM (Robolectric + Roborazzi) so the UI can actually be
 * looked at. This machine is Windows on ARM and cannot run the Android emulator at all, so this is
 * the only way to see the app here.
 *
 * Record with `gradlew recordRoborazziDebug`; images land in `app/build/outputs/roborazzi/`.
 *
 * Note the activity is launched by hand rather than with `createAndroidComposeRule`: that rule
 * launches during its own `before`, which runs *earlier* than `@Before`, so seeding
 * SharedPreferences there would be too late to choose which screen composes.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class SettingsScreenshotTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun settingsScreen() {
        SettingsStore.setOnboardingComplete(app, true)
        ActivityScenario.launch(MainActivity::class.java)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings_en.png")
    }
}

/** The first-run wizard, captured step by step. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class SetupWizardScreenshotTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun wizardSteps() {
        SettingsStore.setOnboardingComplete(app, false)
        ActivityScenario.launch(MainActivity::class.java)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/wizard_1_welcome_en.png")
        compose.onNodeWithText(app.getString(R.string.onb_next)).performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/wizard_2_language_en.png")
        compose.onNodeWithText(app.getString(R.string.onb_next)).performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/wizard_3_permissions_en.png")
    }
}

/**
 * The same screens in Finnish, to check the translated strings actually fit their controls.
 * The locale is applied with `setQualifiers("+fi")` — the leading `+` modifies the device
 * qualifiers already set by [Config], whereas appending "-fi" to them is not valid syntax.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class FinnishScreenshotTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun settingsScreenFinnish() {
        RuntimeEnvironment.setQualifiers("+fi")
        SettingsStore.setOnboardingComplete(app, true)
        ActivityScenario.launch(MainActivity::class.java)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings_fi.png")
    }

    /**
     * Wizard page 5 (bubble size) in Finnish. Joonas's round-2 correction made this string
     * noticeably longer - "Sovitamme kuplan koon sovelluksen painikkeisiin." against the English
     * "We'll size the bubble to match that app's buttons." - so it is worth seeing it laid out.
     */
    @Test
    fun bubbleSizeStepFinnish() {
        RuntimeEnvironment.setQualifiers("+fi")
        SettingsStore.setOnboardingComplete(app, false)
        ActivityScenario.launch(MainActivity::class.java)
        val next = app.getString(R.string.onb_next)
        repeat(4) {
            compose.onNodeWithText(next).performClick()
            compose.waitForIdle()
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/wizard_5_size_fi.png")
    }

    @Test
    fun wizardWelcomeFinnish() {
        RuntimeEnvironment.setQualifiers("+fi")
        SettingsStore.setOnboardingComplete(app, false)
        ActivityScenario.launch(MainActivity::class.java)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/wizard_1_welcome_fi.png")
    }
}

/**
 * The overlay UI is classic Views rather than Compose, so it is inflated directly. These are the
 * app's signature surfaces: the timer picker, the breathing wind-down and the block cover.
 *
 * Roborazzi refuses to capture a detached View ("View should have Activity"), so each layout is
 * attached as the content view of a host activity themed like the real overlay.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class OverlayLayoutScreenshotTest {

    private fun capture(layout: Int, name: String) {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.Theme_Pause_Overlay)
        controller.setup()
        val view: View = LayoutInflater.from(activity).inflate(layout, null, false)
        activity.setContentView(view)
        activity.window.decorView.captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun timerPicker() = capture(R.layout.timer_picker, "overlay_timer_picker")

    @Test
    fun breathing() = capture(R.layout.breathing, "overlay_breathing")

    @Test
    fun blockCover() = capture(R.layout.block_overlay, "overlay_block_cover")
}
