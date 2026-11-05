package io.github.mzuhairkhan.pause

/**
 * Where the user is in the app after onboarding: the main screen, the settings list, or one of its
 * pages. [parent] is where Back goes, so the header arrow and the system gesture share one rule.
 * No navigation library: this is two levels deep and every page is a plain composable.
 */
internal enum class SettingsDestination(val parent: SettingsDestination?) {
    MAIN(null),
    SETTINGS(MAIN),
    PERMISSIONS(SETTINGS),
    BUBBLE(SETTINGS),
    APPEARANCE(SETTINGS),
    WIND_DOWN(SETTINGS),
    APP_BLOCKING(SETTINGS),
    FEEDBACK(SETTINGS),
    CREDITS(SETTINGS),
    ABOUT(SETTINGS)
}
