package io.github.mzuhairkhan.pause

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashIssueTest {
    private val device = DeviceInfo(
        appVersion = "0.7.0",
        installSource = "F-Droid",
        androidVersion = "Android 16 (API 36)",
        deviceModel = "samsung SM-S921B",
        rom = "samsung BP2A",
        permissions = "overlay: granted"
    )

    private fun report(trace: String = "java.lang.IllegalStateException: boom\n\tat a.B.c(B.java:1)") = CrashReport(
        appVersion = "0.6.0 (9)", androidVersion = "16", device = "samsung SM-S921B",
        crashedAt = "2026-11-03T20:01:02.000+02:00", stackTrace = trace, log = ""
    )

    @Test fun `it fills every device field the crash form asks for`() {
        val fields = CrashIssue.fields(device, report(), note = "I was setting a timer")

        IssueFields.DEVICE.forEach { assertTrue("$it missing", fields.getValue(it).isNotBlank()) }
    }

    @Test fun `the version is the one that crashed, not the one installed now`() {
        val fields = CrashIssue.fields(device, report(), note = "")

        assertEquals("0.6.0 (9)", fields[IssueFields.APP_VERSION])
    }

    @Test fun `the user's note becomes the what-happened answer`() {
        val fields = CrashIssue.fields(device, report(), note = "  I was setting a timer ")

        assertEquals("I was setting a timer", fields[CrashIssue.WHAT_HAPPENED])
    }

    @Test fun `a blank note is left out so the form still asks for it`() {
        assertFalse(CrashIssue.WHAT_HAPPENED in CrashIssue.fields(device, report(), note = "   "))
    }

    @Test fun `the crash log is the trimmed trace and fits the budget`() {
        val frames = (1..200).joinToString("\n") { "\tat a.B.c$it(B.java:$it)" }
        val fields = CrashIssue.fields(device, report("java.lang.IllegalStateException: boom\n$frames"), note = "")

        val log = fields.getValue(CrashIssue.CRASH_LOG)
        assertTrue(log.startsWith("java.lang.IllegalStateException: boom"))
        assertTrue("was ${log.length}", log.length <= CrashIssue.LOG_CHARS)
    }

    @Test fun `the ids it uses are the ones the crash form defines`() {
        // IssueTemplateContractTest checks these against the YAML; this pins the constants.
        assertEquals("what-happened", CrashIssue.WHAT_HAPPENED)
        assertEquals("crash-log", CrashIssue.CRASH_LOG)
    }
}
