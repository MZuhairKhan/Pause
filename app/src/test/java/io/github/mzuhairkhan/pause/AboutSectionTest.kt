package io.github.mzuhairkhan.pause

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import org.robolectric.shadows.ShadowToast

/**
 * The About section's three links are code constants ([LinkTargets]), never string resources --
 * [StringResourceIntegrityTest] rejects links in translations, so this is what actually pins the
 * addresses a tap opens.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class AboutSectionTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun openAbout() {
        SettingsStore.setOnboardingComplete(app, true)
        ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        compose.onNodeWithContentDescription(app.getString(R.string.settings_title)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(app.getString(R.string.section_about)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `source code opens the repo`() {
        openAbout()
        compose.onNodeWithText(app.getString(R.string.about_source_code)).performScrollTo().performClick()

        val started = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals(Uri.parse(LinkTargets.SOURCE_CODE), started?.data)
    }

    @Test
    fun `report a bug opens the issues page`() {
        openAbout()
        compose.onNodeWithText(app.getString(R.string.about_report_bug)).performScrollTo().performClick()

        val started = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals(Uri.parse(LinkTargets.REPORT_BUG), started?.data)
    }

    @Test
    fun `help translate opens the Weblate project`() {
        openAbout()
        compose.onNodeWithText(app.getString(R.string.about_translate)).performScrollTo().performClick()

        val started = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals(Uri.parse(LinkTargets.TRANSLATE), started?.data)
    }

    @Test
    fun `version and license are shown`() {
        openAbout()

        val versionName = app.packageManager.getPackageInfo(app.packageName, 0).versionName
        compose.onNodeWithText(versionName!!).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(LinkTargets.LICENSE).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a link that nothing can open shows a message instead of crashing`() {
        // No activity on this shadow application declares a matching intent filter, so
        // startActivity throws ActivityNotFoundException -- exactly the F-Droid device with no
        // browser installed at all.
        shadowOf(app).checkActivities(true)
        openAbout()

        compose.onNodeWithText(app.getString(R.string.about_source_code)).performScrollTo().performClick()
        compose.waitForIdle()

        // A Toast is a separate window, not part of the Compose semantics tree.
        assertEquals(app.getString(R.string.about_no_browser), ShadowToast.getTextOfLatestToast())
    }
}
