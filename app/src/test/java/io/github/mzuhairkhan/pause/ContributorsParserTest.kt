package io.github.mzuhairkhan.pause

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContributorsParserTest {
    private val sample = """
        # Contributors

        People who helped build Pause. Add yourself as `- Name or handle - what you did`, a link is optional.

        ## Maintainer

        - Zuhair Khan - creator and maintainer

        ## Translations

        ## Code

        ## Ideas, design and testing

        - Zarin Maisha - helped with the idea and testing
    """.trimIndent()

    @Test fun `each heading with people becomes a section`() {
        assertEquals(
            listOf(
                CreditSection("Maintainer", listOf("Zuhair Khan - creator and maintainer")),
                CreditSection("Ideas, design and testing", listOf("Zarin Maisha - helped with the idea and testing"))
            ),
            ContributorsParser.parse(sample)
        )
    }

    @Test fun `headings nobody is under yet are left out`() {
        val titles = ContributorsParser.parse(sample).map { it.title }

        assertTrue("Translations" !in titles && "Code" !in titles)
    }

    @Test fun `the intro line is not an entry`() {
        assertTrue(ContributorsParser.parse(sample).flatMap { it.entries }.none { it.startsWith("People who") })
    }

    @Test fun `a link keeps its text and loses its address`() {
        val parsed = ContributorsParser.parse("## Code\n\n- [Ada](https://example.test/ada) - wrote the parser\n")

        assertEquals(listOf("Ada - wrote the parser"), parsed.single().entries)
    }

    @Test fun `several people under one heading keep their order`() {
        val parsed = ContributorsParser.parse("## Code\n- B - second\n- A - first\n")

        assertEquals(listOf("B - second", "A - first"), parsed.single().entries)
    }

    @Test fun `an empty file gives no sections`() {
        assertEquals(emptyList<CreditSection>(), ContributorsParser.parse(""))
    }

    @Test fun `Windows line endings parse the same`() {
        assertEquals(ContributorsParser.parse(sample), ContributorsParser.parse(sample.replace("\n", "\r\n")))
    }
}
