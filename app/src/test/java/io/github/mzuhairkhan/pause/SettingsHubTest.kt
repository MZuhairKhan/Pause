package io.github.mzuhairkhan.pause

import android.Manifest
import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.os.Process
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
import java.io.File
import org.acra.file.ReportLocator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * The gear opens a short list of categories, each a small page, instead of one long scroll.
 * These pin the navigation (list, pages, Back one level at a time), what each row summarises,
 * and the Feedback and Credits pages that are new with it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class SettingsHubTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun seedGrants(overlay: Boolean = true, notifications: Boolean = true, battery: Boolean = true, usage: Boolean = true) {
        ShadowSettings.setCanDrawOverlays(overlay)
        if (notifications) shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        else shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        shadowOf(power).setIgnoringBatteryOptimizations(app.packageName, battery)
        val appOps = app.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName,
            if (usage) AppOpsManager.MODE_ALLOWED else AppOpsManager.MODE_IGNORED
        )
    }

    private fun str(id: Int) = app.getString(id)

    /** Launches onto the main screen and opens the settings list through the gear. */
    private fun openList(): ActivityScenario<MainActivity> {
        SettingsStore.setOnboardingComplete(app, true)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        compose.onNodeWithContentDescription(str(R.string.settings_title)).performClick()
        compose.waitForIdle()
        return scenario
    }

    private fun openRow(title: String) {
        compose.onNodeWithText(title).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun backArrow() {
        compose.onNodeWithContentDescription(str(R.string.onb_back)).performClick()
        compose.waitForIdle()
    }

    private val rowTitles
        get() = listOf(
            R.string.permissions_title, R.string.section_bubble, R.string.section_appearance,
            R.string.section_breathing, R.string.section_app_blocking, R.string.feedback_title,
            R.string.credits_title, R.string.section_about
        ).map { str(it) }

    @Test
    fun `the settings list shows one row per category`() {
        seedGrants()
        openList()

        rowTitles.forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
    }

    @Test
    fun `each row opens its own page`() {
        seedGrants()
        openList()

        val expectations = listOf(
            str(R.string.permissions_title) to str(R.string.perm_overlay),
            str(R.string.section_bubble) to str(R.string.show_countdown_title),
            str(R.string.section_appearance) to str(R.string.custom_color),
            str(R.string.section_breathing) to str(R.string.no_skip_lock),
            str(R.string.section_app_blocking) to str(R.string.break_length),
            str(R.string.feedback_title) to str(R.string.feedback_report_bug),
            str(R.string.section_about) to str(R.string.about_version)
        )
        expectations.forEach { (row, onPage) ->
            openRow(row)
            compose.onNodeWithText(onPage).performScrollTo().assertIsDisplayed()
            backArrow()
        }
    }

    @Test
    fun `the page title matches the row that opened it, and the list is gone`() {
        seedGrants()
        openList()

        openRow(str(R.string.section_appearance))

        compose.onNodeWithText(str(R.string.section_appearance)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.section_bubble)).assertDoesNotExist()
    }

    @Test
    fun `the header arrow goes back one level at a time`() {
        seedGrants()
        openList()
        openRow(str(R.string.section_bubble))

        backArrow()
        compose.onNodeWithText(str(R.string.section_appearance)).assertIsDisplayed() // the list

        backArrow()
        compose.onNodeWithText(str(R.string.start_overlay)).assertIsDisplayed() // main
    }

    @Test
    fun `system back goes up one level and only leaves the app from main`() {
        seedGrants()
        val scenario = openList()
        openRow(str(R.string.section_bubble))

        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText(str(R.string.section_appearance)).assertIsDisplayed()
        scenario.onActivity { assertFalse(it.isFinishing) }

        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText(str(R.string.start_overlay)).assertIsDisplayed()
        scenario.onActivity { assertFalse(it.isFinishing) }
    }

    @Test
    fun `a page stays open across a recreate`() {
        seedGrants()
        val scenario = openList()
        openRow(str(R.string.section_bubble))

        scenario.recreate()
        compose.waitForIdle()

        compose.onNodeWithText(str(R.string.show_countdown_title)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `permissions summarise as All set once the required ones are granted`() {
        seedGrants(usage = false)
        openList()

        compose.onNodeWithText(str(R.string.all_set)).assertIsDisplayed()
    }

    @Test
    fun `permissions summarise how many required ones are missing`() {
        seedGrants(overlay = false, battery = false)
        openList()

        compose.onNodeWithText("2 missing").assertIsDisplayed()
    }

    @Test
    fun `the other rows summarise their current values`() {
        seedGrants()
        SettingsStore.setBubblePreset(app, BubblePresets.TIKTOK)
        SettingsStore.setLockSeconds(app, 20)
        SettingsStore.setSnoozeMinutes(app, 7)
        SettingsStore.setBlockMinutes(app, 45)
        openList()

        compose.onNodeWithText(str(R.string.preset_tiktok)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("20s · 7m").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("${str(R.string.apps_chosen_none)} · 45m").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `Report a bug opens the bug form with the device filled in`() {
        seedGrants()
        openList()
        openRow(str(R.string.feedback_title))

        compose.onNodeWithText(str(R.string.feedback_report_bug)).performScrollTo().performClick()

        val started = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        val uri: Uri = started!!.data!!
        assertTrue(uri.toString().startsWith(LinkTargets.NEW_ISSUE))
        assertEquals(IssueTemplates.BUG, uri.getQueryParameter("template"))
        IssueFields.DEVICE.forEach { id ->
            assertFalse("$id not filled in", uri.getQueryParameter(id).isNullOrBlank())
        }
        // It must not carry which apps the user chose to block.
        assertFalse(uri.toString().contains("blocked", ignoreCase = true))
    }

    @Test
    fun `Suggest a feature and Ask a question open their own forms`() {
        seedGrants()
        openList()
        openRow(str(R.string.feedback_title))

        compose.onNodeWithText(str(R.string.feedback_suggest)).performScrollTo().performClick()
        val feature = shadowOf(app).nextStartedActivity!!.data!!
        assertEquals(IssueTemplates.FEATURE, feature.getQueryParameter("template"))

        compose.onNodeWithText(str(R.string.feedback_question)).performScrollTo().performClick()
        val question = shadowOf(app).nextStartedActivity!!.data!!
        assertEquals(IssueTemplates.QUESTION, question.getQueryParameter("template"))
        assertFalse(question.getQueryParameter(IssueFields.APP_VERSION).isNullOrBlank())
    }

    @Test
    fun `Help translate opens the Weblate project`() {
        seedGrants()
        openList()
        openRow(str(R.string.feedback_title))

        compose.onNodeWithText(str(R.string.about_translate)).performScrollTo().performClick()

        assertEquals(Uri.parse(LinkTargets.TRANSLATE), shadowOf(app).nextStartedActivity?.data)
    }

    @Test
    fun `Credits lists the people in CONTRIBUTORS md`() {
        seedGrants()
        openList()
        openRow(str(R.string.credits_title))

        compose.onNodeWithText("Maintainer").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Zuhair Khan - creator and maintainer").performScrollTo().assertIsDisplayed()
        assertNotNull(app.assets.open("CONTRIBUTORS.md"))
    }

    /** Where ACRA keeps a crash nobody has approved sending, which is every crash here. */
    private fun saveCrash(): File =
        File(app.getDir("ACRA-unapproved", Context.MODE_PRIVATE), "crash.stacktrace").apply { writeText("{}") }

    @Test
    fun `no crash row is offered while nothing is saved`() {
        seedGrants()
        openList()
        openRow(str(R.string.feedback_title))

        compose.onNodeWithText(str(R.string.feedback_last_crash)).assertDoesNotExist()
    }

    @Test
    fun `a saved crash adds a row that opens its report screen`() {
        seedGrants()
        val file = saveCrash()
        assertTrue("precondition: ACRA sees the file", ReportLocator(app).unapprovedReports.any { it.path == file.path })
        openList()
        openRow(str(R.string.feedback_title))

        compose.onNodeWithText(str(R.string.feedback_last_crash)).performScrollTo().performClick()

        val started = shadowOf(app).nextStartedActivity
        assertEquals(CrashReportActivity::class.java.name, started?.component?.className)
        assertEquals(file.path, started?.getStringExtra(CrashReportActivity.EXTRA_REPORT_PATH))
    }
}
