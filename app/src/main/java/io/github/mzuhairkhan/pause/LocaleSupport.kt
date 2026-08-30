package io.github.mzuhairkhan.pause

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate

/** Per-app language helpers built on AppCompat's per-app locales. */
object LocaleSupport {
    /**
     * Applies the chosen per-app locale to a non-activity [base] context on API < 33, where
     * AppCompat doesn't auto-localize services. On API 33+ the OS applies the per-app locale
     * app-wide already, so this is a no-op there.
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val locales = AppCompatDelegate.getApplicationLocales()
        val locale = if (locales.isEmpty) null else locales.get(0)
        if (locale == null) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
