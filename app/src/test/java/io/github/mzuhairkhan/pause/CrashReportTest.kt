package io.github.mzuhairkhan.pause

import org.acra.ReportField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StackTraceDigestTest {
    private fun frames(n: Int, prefix: String = "a.B.c") = (1..n).joinToString("\n") { "\tat $prefix$it(B.java:$it)" }

    private val twoCauses = buildString {
        append("java.lang.IllegalStateException: outer\n")
        append(frames(10, "outer")).append("\n")
        append("Caused by: java.io.IOException: inner\n")
        append(frames(8, "inner")).append("\n")
        append("\t... 5 more")
    }

    @Test fun `a short trace is returned as it is`() {
        val trace = "java.lang.IllegalStateException: boom\n${frames(3)}"

        assertEquals(trace, StackTraceDigest.digest(trace, maxChars = 5_000))
    }

    @Test fun `only the top frames of a long trace are kept`() {
        val out = StackTraceDigest.digest("java.lang.IllegalStateException: boom\n${frames(20)}", 5_000)

        assertTrue(out.startsWith("java.lang.IllegalStateException: boom\n\tat a.B.c1("))
        assertTrue("at a.B.c6(" in out)
        assertFalse("at a.B.c7(" in out)
        assertTrue("14 more frames" in out)
    }

    @Test fun `every cause keeps its header and its own top frames`() {
        val out = StackTraceDigest.digest(twoCauses, 5_000)

        assertTrue("outer header", "java.lang.IllegalStateException: outer" in out)
        assertTrue("outer frames", "at outer6(" in out && "at outer7(" !in out)
        assertTrue("cause header", "Caused by: java.io.IOException: inner" in out)
        assertTrue("cause frames", "at inner6(" in out && "at inner7(" !in out)
    }

    @Test fun `a trailing 'more' line from the JVM is not carried over`() {
        assertFalse("... 5 more" in StackTraceDigest.digest(twoCauses, 5_000))
    }

    @Test fun `a trace over the limit is cut at a line and marked`() {
        val out = StackTraceDigest.digest(twoCauses, maxChars = 200)

        assertTrue("was ${out.length}", out.length <= 200)
        assertTrue(out.endsWith("…"))
        assertTrue("the first line survives", out.startsWith("java.lang.IllegalStateException: outer"))
    }

    @Test fun `an empty trace stays empty`() {
        assertEquals("", StackTraceDigest.digest("", 100))
    }
}

class CrashReportModelTest {
    private fun report(note: String = "") = CrashReport(
        appVersion = "0.6.0 (9)",
        androidVersion = "16",
        device = "samsung SM-S921B",
        crashedAt = "2026-11-03T20:01:02.000+02:00",
        stackTrace = "java.lang.IllegalStateException: boom\n\tat a.B.c(B.java:1)",
        log = "11-03 20:01:02.000 E/Pause: something"
    ).also { note }

    @Test fun `the headline is the exception line`() {
        assertEquals("java.lang.IllegalStateException: boom", report().headline)
    }

    @Test fun `the markdown carries the device, the trace and the log`() {
        val md = report().toMarkdown("")

        listOf("0.6.0 (9)", "16", "samsung SM-S921B", "2026-11-03T20:01:02.000+02:00",
            "java.lang.IllegalStateException: boom", "at a.B.c(B.java:1)", "E/Pause: something")
            .forEach { assertTrue("missing $it in:\n$md", it in md) }
    }

    @Test fun `the user's note comes first when there is one`() {
        val md = report().toMarkdown("I was setting a timer")

        assertTrue("I was setting a timer" in md)
        assertTrue(md.indexOf("I was setting a timer") < md.indexOf("IllegalStateException"))
    }

    @Test fun `a blank note adds nothing`() {
        assertEquals(report().toMarkdown(""), report().toMarkdown("   "))
        assertFalse("What I was doing" in report().toMarkdown(""))
    }

    @Test fun `it is built from ACRA's stored fields`() {
        val fields = mapOf(
            ReportField.APP_VERSION_NAME to "0.6.0",
            ReportField.APP_VERSION_CODE to "9",
            ReportField.ANDROID_VERSION to "16",
            ReportField.BRAND to "samsung",
            ReportField.PHONE_MODEL to "SM-S921B",
            ReportField.USER_CRASH_DATE to "2026-11-03T20:01:02.000+02:00",
            ReportField.STACK_TRACE to "java.lang.IllegalStateException: boom\n\tat a.B.c(B.java:1)",
            ReportField.LOGCAT to "E/Pause: something"
        )

        val built = CrashReport.from { fields[it] }

        assertEquals("0.6.0 (9)", built.appVersion)
        assertEquals("16", built.androidVersion)
        assertEquals("samsung SM-S921B", built.device)
        assertEquals("2026-11-03T20:01:02.000+02:00", built.crashedAt)
        assertEquals("E/Pause: something", built.log)
        assertEquals("java.lang.IllegalStateException: boom", built.headline)
    }

    @Test fun `missing fields become empty text rather than null or a crash`() {
        val built = CrashReport.from { null }

        assertEquals("", built.stackTrace)
        assertEquals("", built.headline)
        assertTrue(built.toMarkdown("").isNotEmpty())
    }
}
