package io.github.mzuhairkhan.pause

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element

/**
 * Guards the string resources against a translation platform being used to change what the app
 * says. Translations arrive from Hosted Weblate as pull requests, so anyone with an account on
 * that instance can propose resource edits; PR #35 used that to replace the ongoing-notification
 * title with a spam URL. The CHANGELOG gate happened to catch it -- only because the injected
 * edit touched the English source, which is not on that job's translation-only exemption list --
 * but a gate that fires on file paths is not a check on file *content*. This is.
 *
 * Deliberately plain JUnit rather than Robolectric: it reads the XML off disk, so pulling in an
 * Android runtime would only slow it down.
 */
class StringResourceIntegrityTest {

    /** A URL, a bare domain, or an email address -- none of which belong in this app's copy. */
    private val linkLike = Regex(
        """://|\bwww\.|[a-z0-9-]+\.(?:com|net|org|io|dev|app|xyz|top|shop|ru|cn|info|biz)\b|\S+@\S+\.\S+""",
        RegexOption.IGNORE_CASE,
    )

    /** Android format specifiers: `%1${'$'}s`, `%1${'$'}d`, `%s`. `%%` is a literal percent, not one. */
    private val formatSpecifier = Regex("""%(?:\d+\$)?[a-zA-Z]""")

    @Test
    fun `no string resource contains a link, domain or email address`() {
        val offenders = mutableListOf<String>()
        for (locale in allLocales()) {
            for ((key, values) in locale.strings) {
                for (value in values) {
                    linkLike.find(value)?.let {
                        offenders += "${locale.name}/$key: matched \"${it.value}\" in \"$value\""
                    }
                }
            }
        }
        if (offenders.isNotEmpty()) {
            fail("String resources must not contain links or addresses:\n" + offenders.joinToString("\n"))
        }
    }

    @Test
    fun `every translation preserves the format placeholders of its English source`() {
        val source = readStrings(sourceFile())
        val mismatches = mutableListOf<String>()
        for (locale in allLocales()) {
            if (locale.name == SOURCE_LOCALE) continue
            for ((key, values) in locale.strings) {
                val expected = source[key]?.let { placeholdersIn(it) } ?: continue
                val actual = placeholdersIn(values)
                if (actual != expected) {
                    mismatches += "${locale.name}/$key: expected $expected, found $actual"
                }
            }
        }
        if (mismatches.isNotEmpty()) {
            fail("Translations must use the same placeholders as the English source:\n" + mismatches.joinToString("\n"))
        }
    }

    @Test
    fun `the English source is found and non-trivial`() {
        // Guards the two tests above against passing vacuously if the res path ever moves.
        val source = readStrings(sourceFile())
        assertTrue("expected many English strings, found ${source.size}", source.size > 100)
    }

    private fun placeholdersIn(values: List<String>): Set<String> =
        values.flatMap { formatSpecifier.findAll(it.replace("%%", "")).map(MatchResult::value) }.toSet()

    private fun resDir(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            for (candidate in listOf(File(dir, "app/src/main/res"), File(dir, "src/main/res"))) {
                if (candidate.isDirectory) return candidate
            }
            dir = dir.parentFile
        }
        throw AssertionError("could not locate src/main/res from ${File("").absolutePath}")
    }

    private fun sourceFile(): File = File(resDir(), "$SOURCE_LOCALE/strings.xml")

    private class Locale(val name: String, val strings: Map<String, List<String>>)

    private fun allLocales(): List<Locale> {
        val dirs = resDir().listFiles { f: File -> f.isDirectory && f.name.startsWith("values") }
            .orEmpty()
            .filter { File(it, "strings.xml").isFile }
            .sortedBy { it.name }
        assertTrue("no strings.xml found under ${resDir()}", dirs.isNotEmpty())
        return dirs.map { Locale(it.name, readStrings(File(it, "strings.xml"))) }
    }

    /** Maps each resource name to its text: one entry for a `<string>`, one per item for `<plurals>`. */
    private fun readStrings(file: File): Map<String, List<String>> {
        val doc = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(file)
        val result = mutableMapOf<String, List<String>>()
        for (tag in listOf("string", "plurals")) {
            val nodes = doc.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as Element
                val name = element.getAttribute("name")
                if (name.isEmpty()) continue
                result[name] = if (tag == "string") {
                    listOf(element.textContent)
                } else {
                    val items = element.getElementsByTagName("item")
                    (0 until items.length).map { items.item(it).textContent }
                }
            }
        }
        return result
    }

    private companion object {
        const val SOURCE_LOCALE = "values"
    }
}
