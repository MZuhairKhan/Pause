package io.github.mzuhairkhan.pause

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import java.io.File
import org.acra.ReportField
import org.acra.data.CrashReportData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * The screen a crash notification opens. It shows exactly what would be shared, and nothing leaves
 * the phone except through a button on it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class CrashReportActivityTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private fun str(id: Int) = app.getString(id)

    private fun savedReport(): File {
        val data = CrashReportData().apply {
            put(ReportField.APP_VERSION_NAME, "0.6.0")
            put(ReportField.APP_VERSION_CODE, "9")
            put(ReportField.ANDROID_VERSION, "16")
            put(ReportField.BRAND, "samsung")
            put(ReportField.PHONE_MODEL, "SM-S921B")
            put(ReportField.USER_CRASH_DATE, "2026-11-03T20:01:02.000+02:00")
            put(ReportField.STACK_TRACE, "java.lang.IllegalStateException: boom\n\tat a.B.c(B.java:1)")
            put(ReportField.LOGCAT, "E/Pause: something")
        }
        return File(app.cacheDir, "crash-${System.nanoTime()}.json").apply { writeText(data.toJSON()) }
    }

    private var launched: ActivityScenario<CrashReportActivity>? = null

    private fun open(file: File): ActivityScenario<CrashReportActivity> {
        val scenario = ActivityScenario.launch<CrashReportActivity>(CrashReportActivity.intent(app, file))
        launched = scenario
        compose.waitForIdle()
        return scenario
    }

    /**
     * Several of these finish the activity themselves. Left to the test environment's teardown, a
     * finished activity's Compose root stayed registered and the next test class's
     * `waitForIdle` timed out, so each scenario is closed here instead.
     */
    @After
    fun closeScenario() {
        launched?.close()
    }

    private val clipboardText: String?
        get() = (app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .primaryClip?.getItemAt(0)?.text?.toString()

    @Test
    fun `it shows what would be shared`() {
        open(savedReport())

        compose.onNodeWithText(str(R.string.crash_title)).assertIsDisplayed()
        compose.onNodeWithText("IllegalStateException: boom", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `Report on GitHub opens the crash form with the report and the note filled in`() {
        val file = savedReport()
        open(file)
        compose.onNodeWithText(str(R.string.crash_note_label)).performTextInput("I was setting a timer")

        compose.onNodeWithText(str(R.string.crash_report_github)).performScrollTo().performClick()

        val started = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        val uri = started!!.data!!
        assertTrue(uri.toString().startsWith(LinkTargets.NEW_ISSUE))
        assertEquals(IssueTemplates.CRASH, uri.getQueryParameter("template"))
        assertEquals("I was setting a timer", uri.getQueryParameter(CrashIssue.WHAT_HAPPENED))
        assertTrue(uri.getQueryParameter(CrashIssue.CRASH_LOG)!!.contains("IllegalStateException: boom"))
        assertEquals("0.6.0 (9)", uri.getQueryParameter(IssueFields.APP_VERSION))
        IssueFields.DEVICE.forEach { assertFalse("$it blank", uri.getQueryParameter(it).isNullOrBlank()) }
    }

    @Test
    fun `Report on GitHub also copies the full report, then clears the saved one`() {
        val file = savedReport()
        val scenario = open(file)

        compose.onNodeWithText(str(R.string.crash_report_github)).performScrollTo().performClick()

        assertTrue(clipboardText!!.contains("E/Pause: something"))
        assertFalse("the saved report is handed over, so it goes", file.exists())
        scenario.onActivity { assertTrue(it.isFinishing) }
    }

    @Test
    fun `with no browser to open the form, the report is kept and the person is told`() {
        shadowOf(app).checkActivities(true)
        val file = savedReport()
        val scenario = open(file)

        compose.onNodeWithText(str(R.string.crash_report_github)).performScrollTo().performClick()
        compose.waitForIdle()

        assertTrue("nothing was handed over", file.exists())
        scenario.onActivity { assertFalse(it.isFinishing) }
        assertEquals(str(R.string.about_no_browser), ShadowToast.getTextOfLatestToast())
        assertNotNull("the report is still on the clipboard to paste elsewhere", clipboardText)
    }

    @Test
    fun `Copy puts the report on the clipboard and keeps it saved`() {
        val file = savedReport()
        open(file)

        compose.onNodeWithText(str(R.string.crash_copy)).performScrollTo().performClick()

        assertTrue(clipboardText!!.contains("IllegalStateException: boom"))
        assertTrue(file.exists())
        assertEquals(str(R.string.crash_copied), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `Share offers the report to the share sheet and keeps it saved`() {
        val file = savedReport()
        open(file)

        compose.onNodeWithText(str(R.string.crash_share)).performScrollTo().performClick()

        val chooser = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser?.action)
        val send = chooser!!.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertTrue(send.getStringExtra(Intent.EXTRA_TEXT)!!.contains("IllegalStateException: boom"))
        assertTrue(file.exists())
    }

    @Test
    fun `Don't send deletes the saved report and closes`() {
        val file = savedReport()
        val scenario = open(file)

        compose.onNodeWithText(str(R.string.crash_discard)).performScrollTo().performClick()

        assertFalse(file.exists())
        scenario.onActivity { assertTrue(it.isFinishing) }
        assertTrue("nothing was opened or shared", shadowOf(app).nextStartedActivity == null)
    }

    @Test
    fun `a report that is gone says so instead of crashing`() {
        open(File(app.cacheDir, "already-deleted.json"))

        compose.onNodeWithText(str(R.string.crash_unavailable)).assertIsDisplayed()
    }

    @Test
    fun `a report that cannot be read says so instead of crashing`() {
        val broken = File(app.cacheDir, "broken.json").apply { writeText("not json at all") }

        open(broken)

        compose.onNodeWithText(str(R.string.crash_unavailable)).assertIsDisplayed()
    }
}
