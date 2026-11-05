package io.github.mzuhairkhan.pause

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDestinationTest {
    @Test fun `the main screen is the root`() {
        assertNull(SettingsDestination.MAIN.parent)
    }

    @Test fun `the settings list leads back to main`() {
        assertEquals(SettingsDestination.MAIN, SettingsDestination.SETTINGS.parent)
    }

    @Test fun `every page leads back to the list`() {
        val pages = SettingsDestination.values().filter { it != SettingsDestination.MAIN && it != SettingsDestination.SETTINGS }

        assertTrue(pages.isNotEmpty())
        pages.forEach { assertEquals("$it", SettingsDestination.SETTINGS, it.parent) }
    }

    @Test fun `back from anywhere reaches main in at most two steps`() {
        SettingsDestination.values().forEach { start ->
            var at: SettingsDestination? = start
            var steps = 0
            while (at != SettingsDestination.MAIN) {
                at = at?.parent
                steps++
                assertTrue("$start never reaches MAIN", steps <= 2 && at != null)
            }
        }
    }
}
