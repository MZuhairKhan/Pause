package io.github.mzuhairkhan.pause

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The language page used to write its pick through only at Get started, so a user choosing
 * Suomi on page 2 read five more wizard pages in English before it ever took effect. It now
 * applies on tap, like every other wizard control that writes straight through (see
 * [WizardBreathingPageTest]).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class WizardLanguagePageTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    /** Lands on the language page, the one right after welcome. */
    private fun openLanguagePage() {
        SettingsStore.setOnboardingComplete(app, false)
        ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        compose.onNodeWithText(app.getString(R.string.onb_next)).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `picking a language applies it immediately, not at Get started`() {
        openLanguagePage()

        compose.onNodeWithText("Suomi").performClick()
        compose.waitForIdle()

        assertEquals(
            "the rest of the wizard should read in the language just picked",
            "fi",
            AppCompatDelegate.getApplicationLocales().toLanguageTags()
        )
    }

    @Test
    fun `system default clears a chosen locale immediately too`() {
        openLanguagePage()
        compose.onNodeWithText("Suomi").performClick()
        compose.waitForIdle()

        compose.onNodeWithText(app.getString(R.string.lang_system)).performClick()
        compose.waitForIdle()

        assertTrue(
            "system default must not leave the earlier pick behind",
            AppCompatDelegate.getApplicationLocales().isEmpty
        )
    }
}
