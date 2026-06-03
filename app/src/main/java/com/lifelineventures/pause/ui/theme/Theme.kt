package com.lifelineventures.pause.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * @param themeMode 0 = follow system, 1 = light, 2 = dark
 * @param accentIndex index into [Accents.colors]
 */
@Composable
fun PauseTheme(
    themeMode: Int = 0,
    accentIndex: Int = Accents.DEFAULT,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    val accent = Color(Accents.colors[Accents.safeIndex(accentIndex)])
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    val colorScheme = base.copy(
        primary = accent,
        onPrimary = Color.White,
        secondary = accent,
        onSecondary = Color.White
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
