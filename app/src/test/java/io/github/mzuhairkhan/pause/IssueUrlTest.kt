package io.github.mzuhairkhan.pause

import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueUrlTest {
    private val base = "https://example.test/issues/new"

    private fun decodedParams(url: String): Map<String, String> =
        url.substringAfter('?').split('&').associate {
            val (k, v) = it.split('=', limit = 2)
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }

    @Test fun `names the template and encodes spaces as percent twenty`() {
        val url = IssueUrl.build(
            base, "bug_report.yml",
            mapOf("app-version" to "0.6.0", "device-model" to "Samsung SM-S921B")
        )

        assertEquals(
            "$base?template=bug_report.yml&app-version=0.6.0&device-model=Samsung%20SM-S921B",
            url
        )
    }

    @Test fun `no fields leaves just the template`() {
        assertEquals("$base?template=question.yml", IssueUrl.build(base, "question.yml", emptyMap()))
    }

    @Test fun `characters that would split the query are escaped`() {
        val url = IssueUrl.build(base, "bug_report.yml", mapOf("steps" to "a&b=c#d?e"))

        assertEquals("$base?template=bug_report.yml&steps=a%26b%3Dc%23d%3Fe", url)
        assertEquals("a&b=c#d?e", decodedParams(url)["steps"])
    }

    @Test fun `non-ASCII text round-trips`() {
        val url = IssueUrl.build(base, "bug_report.yml", mapOf("rom" to "Pysäytä"))

        assertEquals("Pysäytä", decodedParams(url)["rom"])
    }

    @Test fun `an over-long value is cut so the link stays under the cap`() {
        val long = "x".repeat(10_000)
        val url = IssueUrl.build(
            base, "crash_report.yml",
            mapOf("app-version" to "0.6.0", "crash-log" to long),
            maxLength = 500
        )

        assertTrue("was ${url.length}", url.length <= 500)
        val params = decodedParams(url)
        assertEquals("short fields survive untouched", "0.6.0", params["app-version"])
        assertTrue("the long one keeps its start", long.startsWith(params.getValue("crash-log").trimEnd('…')))
        assertTrue(params.getValue("crash-log").isNotEmpty())
    }

    @Test fun `the cap trims the longest field and leaves short ones whole`() {
        val url = IssueUrl.build(
            base, "crash_report.yml",
            mapOf("a" to "y".repeat(100), "b" to "z".repeat(900)),
            maxLength = 600
        )

        val params = decodedParams(url)
        assertEquals("the shorter field is not touched", 100, params.getValue("a").length)
        assertTrue(params.getValue("b").length < 900)
        assertTrue("was ${url.length}", url.length <= 600)
    }

    @Test fun `a cap too small for any value still returns a valid link`() {
        val url = IssueUrl.build(base, "bug_report.yml", mapOf("steps" to "long ".repeat(50)), maxLength = 10)

        assertTrue(url.startsWith("$base?template=bug_report.yml"))
    }
}

class InstallSourceTest {
    @Test fun `F-Droid's clients are named`() {
        assertEquals("F-Droid", InstallSource.label("org.fdroid.fdroid"))
        assertEquals("F-Droid", InstallSource.label("org.fdroid.basic"))
    }

    @Test fun `Play is named`() {
        assertEquals("Google Play", InstallSource.label("com.android.vending"))
    }

    @Test fun `another store shows its package name`() {
        assertEquals("com.example.store", InstallSource.label("com.example.store"))
    }

    @Test fun `no recorded installer reads as a sideload`() {
        assertEquals("Unknown (APK or adb)", InstallSource.label(null))
        assertEquals("Unknown (APK or adb)", InstallSource.label(""))
    }
}
