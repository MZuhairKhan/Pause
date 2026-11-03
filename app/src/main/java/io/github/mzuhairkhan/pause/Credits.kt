package io.github.mzuhairkhan.pause

import android.content.Context
import java.io.IOException

/** Where the credits come from: a copy of `CONTRIBUTORS.md`, bundled into the app's assets at build time. */
internal object Credits {
    const val FILE = "CONTRIBUTORS.md"

    fun load(context: Context): List<CreditSection> = try {
        context.assets.open(FILE).bufferedReader(Charsets.UTF_8).use { ContributorsParser.parse(it.readText()) }
    } catch (e: IOException) {
        emptyList()
    }
}

/** One heading from `CONTRIBUTORS.md` and the people listed under it. */
internal data class CreditSection(val title: String, val entries: List<String>)

/**
 * Reads `CONTRIBUTORS.md` so the Credits page and the file contributors edit are one list.
 * Pure, so it unit-tests on a plain JVM.
 */
internal object ContributorsParser {
    private val link = Regex("""\[([^\]]*)]\([^)]*\)""")

    fun parse(markdown: String): List<CreditSection> {
        val sections = mutableListOf<CreditSection>()
        var title: String? = null
        var entries = mutableListOf<String>()

        fun close() {
            val t = title
            // A heading nobody is listed under yet is not worth showing.
            if (t != null && entries.isNotEmpty()) sections += CreditSection(t, entries)
        }

        for (line in markdown.lines()) {
            when {
                line.startsWith("## ") -> {
                    close()
                    title = line.removePrefix("## ").trim()
                    entries = mutableListOf()
                }
                // Before the first heading is the intro, which is addressed to contributors.
                title != null && line.startsWith("- ") -> {
                    // Links are shown as plain text; nothing in this file may be tappable.
                    val entry = link.replace(line.removePrefix("- "), "$1").trim()
                    if (entry.isNotEmpty()) entries += entry
                }
            }
        }
        close()
        return sections
    }
}
