package com.lifelineventures.pause.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils

/**
 * @param themeMode 0 = follow system, 1 = light, 2 = dark
 * @param accentColor the accent as an ARGB color int (a preset or a custom pick)
 */
@Composable
fun PauseTheme(
    themeMode: Int = 0,
    accentColor: Int = Accents.colors[Accents.DEFAULT],
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    val accent = Color(accentColor)
    // Pick black or white text on the accent based on its brightness, so light custom
    // colors stay readable.
    val onAccent = if (ColorUtils.calculateLuminance(accentColor) > 0.5) Color.Black else Color.White

    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    val colorScheme = base.copy(
        primary = accent,
        onPrimary = onAccent,
        secondary = accent,
        onSecondary = onAccent
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
