package io.github.mzuhairkhan.pause

import org.acra.ReportField
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a saved crash may contain is an allowlist, and the promise made in the README and the store
 * description ("nothing is shared unless you choose to") rests on it staying short. These fail if a
 * field that says something about the person's phone use, or identifies the phone, is added.
 */
class CrashReportFieldsTest {
    private val fields = PauseApp.REPORT_FIELDS

    @Test fun `the apps the person chose to block are never captured`() {
        // They live in shared preferences, which ACRA would otherwise dump whole.
        assertTrue(ReportField.SHARED_PREFERENCES !in fields)
    }

    @Test fun `system settings are never captured`() {
        assertTrue(listOf(ReportField.SETTINGS_SYSTEM, ReportField.SETTINGS_SECURE, ReportField.SETTINGS_GLOBAL).none { it in fields })
    }

    @Test fun `nothing that identifies the phone is captured`() {
        val identifying = listOf(
            ReportField.DEVICE_ID, ReportField.INSTALLATION_ID, ReportField.USER_EMAIL,
            ReportField.USER_IP, ReportField.REPORT_ID
        )
        assertTrue(identifying.none { it in fields })
    }

    @Test fun `the device's environment and feature list are not captured`() {
        assertTrue(listOf(ReportField.ENVIRONMENT, ReportField.DEVICE_FEATURES, ReportField.DISPLAY).none { it in fields })
    }

    @Test fun `what a maintainer needs to read a crash is captured`() {
        listOf(
            ReportField.STACK_TRACE, ReportField.APP_VERSION_NAME, ReportField.APP_VERSION_CODE,
            ReportField.ANDROID_VERSION, ReportField.PHONE_MODEL, ReportField.BRAND, ReportField.LOGCAT
        ).forEach { assertTrue("$it missing", it in fields) }
    }

    @Test fun `the fields CrashReport reads are all among them`() {
        // CrashReport.from asks for these; a field dropped here would silently come back blank.
        listOf(
            ReportField.APP_VERSION_NAME, ReportField.APP_VERSION_CODE, ReportField.ANDROID_VERSION,
            ReportField.BRAND, ReportField.PHONE_MODEL, ReportField.USER_CRASH_DATE,
            ReportField.STACK_TRACE, ReportField.LOGCAT
        ).forEach { assertTrue("$it not captured", it in fields) }
    }
}
