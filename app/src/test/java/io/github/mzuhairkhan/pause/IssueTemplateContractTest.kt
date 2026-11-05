package io.github.mzuhairkhan.pause

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GitHub fills an issue-form field from a URL parameter named after the field's `id`. The app
 * sends [IssueFields]; if someone renames an `id` in the YAML, the app would silently stop
 * filling that field in. This reads the real forms so that fails a build instead.
 *
 * Unit tests run with the module (`app/`) as the working directory, hence the `..`.
 */
class IssueTemplateContractTest {

    private fun idsIn(template: String): Set<String> {
        val file = File("../.github/ISSUE_TEMPLATE/$template")
        assertTrue("missing ${file.path}", file.isFile)
        return Regex("""^\s*id:\s*([A-Za-z0-9_-]+)\s*$""", RegexOption.MULTILINE)
            .findAll(file.readText())
            .map { it.groupValues[1] }
            .toSet()
    }

    @Test fun `the bug form has every device field the app fills in`() {
        val missing = IssueFields.DEVICE - idsIn(IssueTemplates.BUG)

        assertTrue("bug_report.yml lacks ids: $missing", missing.isEmpty())
    }

    @Test fun `the crash form has every device field the app fills in`() {
        val missing = IssueFields.DEVICE - idsIn(IssueTemplates.CRASH)

        assertTrue("crash_report.yml lacks ids: $missing", missing.isEmpty())
    }

    @Test fun `the question form has the version field the app fills in`() {
        assertTrue(IssueFields.APP_VERSION in idsIn(IssueTemplates.QUESTION))
    }

    @Test fun `the feature form exists`() {
        idsIn(IssueTemplates.FEATURE)
    }
}
