package io.github.mzuhairkhan.pause

import android.Manifest
import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import android.os.PowerManager
import android.os.Process
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * The app now opens to a simple main screen -- logo, Start/Stop, a gear -- with everything else
 * (permissions in full, bubble, appearance, breathing, app blocking, about) moved behind it into
 * [SettingsScreen]. These tests pin the navigation between the two and the main screen's own
 * "what's missing" permissions card, which is what the Quick Settings tile's no-permission
 * redirect now lands on.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class MainScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    /** Seeds all four permission flags explicitly -- an unset one can default either way. */
    private fun seedGrants(overlay: Boolean, notifications: Boolean, battery: Boolean, usage: Boolean) {
        ShadowSettings.setCanDrawOverlays(overlay)
        if (notifications) {
            shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        }
        val powerManager = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        shadowOf(powerManager).setIgnoringBatteryOptimizations(app.packageName, battery)
        val appOps = app.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            app.packageName,
            if (usage) AppOpsManager.MODE_ALLOWED else AppOpsManager.MODE_IGNORED
        )
    }

    private fun launchMain(): ActivityScenario<MainActivity> {
        SettingsStore.setOnboardingComplete(app, true)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        return scenario
    }

    @Test
    fun `main screen shows Start and the gear, not the settings sections`() {
        seedGrants(overlay = true, notifications = true, battery = true, usage = true)
        launchMain()

        compose.onNodeWithText(app.getString(R.string.start_overlay)).assertIsDisplayed()
        compose.onNodeWithContentDescription(app.getString(R.string.settings_title)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.section_bubble)).assertDoesNotExist()
    }

    @Test
    fun `the gear opens Settings, and the header back arrow returns to the main screen`() {
        seedGrants(overlay = true, notifications = true, battery = true, usage = true)
        launchMain()

        compose.onNodeWithContentDescription(app.getString(R.string.settings_title)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(app.getString(R.string.settings_title)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.section_bubble)).assertIsDisplayed()

        compose.onNodeWithContentDescription(app.getString(R.string.onb_back)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(app.getString(R.string.start_overlay)).assertIsDisplayed()
    }

    @Test
    fun `system back leaves Settings for the main screen without finishing the activity`() {
        seedGrants(overlay = true, notifications = true, battery = true, usage = true)
        val scenario = launchMain()
        compose.onNodeWithContentDescription(app.getString(R.string.settings_title)).performClick()
        compose.waitForIdle()

        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        compose.onNodeWithText(app.getString(R.string.start_overlay)).assertIsDisplayed()
        scenario.onActivity { assertFalse("back out of Settings must not finish the activity", it.isFinishing) }
    }

    @Test
    fun `system back on the main screen itself is not intercepted`() {
        seedGrants(overlay = true, notifications = true, battery = true, usage = true)
        val scenario = launchMain()

        // No BackHandler is enabled here, so the dispatcher's default (finish the activity)
        // applies -- the same as leaving the app from any other top-level screen.
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        scenario.onActivity { assertTrue("back on the main screen should still leave the app", it.isFinishing) }
    }

    @Test
    fun `Settings stays open across a recreate`() {
        seedGrants(overlay = true, notifications = true, battery = true, usage = true)
        val scenario = launchMain()
        compose.onNodeWithContentDescription(app.getString(R.string.settings_title)).performClick()
        compose.waitForIdle()

        scenario.recreate()
        compose.waitForIdle()

        compose.onNodeWithText(app.getString(R.string.section_bubble)).assertIsDisplayed()
    }

    @Test
    fun `a missing overlay permission disables Start and shows its row on the main screen`() {
        seedGrants(overlay = false, notifications = true, battery = true, usage = true)
        launchMain()

        compose.onNodeWithText(app.getString(R.string.start_overlay)).assertIsNotEnabled()
        compose.onNodeWithText(app.getString(R.string.permissions_title)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.perm_overlay)).assertIsDisplayed()
        // Optional usage access never appears on the main screen, granted or not.
        compose.onNodeWithText(app.getString(R.string.perm_usage)).assertDoesNotExist()
    }

    @Test
    fun `once every required permission is granted, the main screen shows no permission rows`() {
        seedGrants(overlay = true, notifications = true, battery = true, usage = false)
        launchMain()

        compose.onNodeWithText(app.getString(R.string.permissions_title)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.perm_overlay)).assertDoesNotExist()
    }

    @Test
    fun `Settings always lists all four permission rows, including optional usage access`() {
        seedGrants(overlay = true, notifications = true, battery = true, usage = false)
        launchMain()

        compose.onNodeWithContentDescription(app.getString(R.string.settings_title)).performClick()
        compose.waitForIdle()
        // The permission rows are their own page behind the list now.
        compose.onNodeWithText(app.getString(R.string.permissions_title)).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(app.getString(R.string.perm_overlay)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.perm_notifications)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.perm_battery)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.perm_usage)).assertIsDisplayed()
    }

    @Test
    fun `granting a permission elsewhere updates the main screen's card on resume`() {
        seedGrants(overlay = false, notifications = true, battery = true, usage = true)
        val scenario = launchMain()
        compose.onNodeWithText(app.getString(R.string.perm_overlay)).assertIsDisplayed()

        // Simulate the user granting it on the external Settings screen the row's tap opens,
        // then returning to this one -- caught by the same ON_RESUME re-read the settings
        // screen always relied on.
        ShadowSettings.setCanDrawOverlays(true)
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()

        compose.onNodeWithText(app.getString(R.string.permissions_title)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.start_overlay)).assertIsDisplayed()
    }
}
