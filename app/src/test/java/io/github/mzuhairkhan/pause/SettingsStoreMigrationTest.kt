package io.github.mzuhairkhan.pause

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The breathing on/off switch was replaced by a 0-second minimum: anyone who had the exercise
 * off keeps the closest equivalent, a lock of 0, the first time [SettingsStore.lockSeconds] is
 * read. There is no `Application` subclass to run this at, so the migration has to live on the
 * read path itself -- the boot receiver can start the service without `MainActivity` ever
 * running.
 */
@RunWith(AndroidJUnit4::class)
class SettingsStoreMigrationTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = app.getSharedPreferences("pause_settings", Context.MODE_PRIVATE)

    @Test
    fun `breathing off migrates to a zero lock`() {
        prefs.edit().putBoolean("breath_enabled", false).apply()

        assertEquals(0, SettingsStore.lockSeconds(app))
        assertFalse(
            "the legacy key is a one-shot -- it must not linger after being read",
            prefs.contains("breath_enabled")
        )
    }

    @Test
    fun `breathing on leaves the stored lock untouched`() {
        prefs.edit().putBoolean("breath_enabled", true).putInt("breath_lock", 45).apply()

        assertEquals(45, SettingsStore.lockSeconds(app))
        assertFalse(prefs.contains("breath_enabled"))
    }

    @Test
    fun `no legacy key falls back to the shipped default`() {
        assertEquals(SettingsDefaults.LOCK_SECONDS, SettingsStore.lockSeconds(app))
    }

    @Test
    fun `a second read after migration is stable`() {
        prefs.edit().putBoolean("breath_enabled", false).apply()

        SettingsStore.lockSeconds(app)

        assertEquals(0, SettingsStore.lockSeconds(app))
    }
}
