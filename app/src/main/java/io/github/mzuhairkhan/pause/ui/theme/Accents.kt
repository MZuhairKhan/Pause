package io.github.mzuhairkhan.pause.ui.theme

/**
 * Accent colors for the Compose theme and overlay picker tint. ARGB ints so they serve as
 * both Android color ints and, via Color(int), Compose colors. Bubble icons ignore them.
 */
object Accents {
    val colors = intArrayOf(
        0xFF4C8DFF.toInt(), // Blue
        0xFF7C5CFF.toInt(), // Purple
        0xFF15B8A6.toInt(), // Teal
        0xFF3FB950.toInt(), // Green
        0xFFFF8A3D.toInt(), // Orange
        0xFF757575.toInt()  // Mono (grayscale / B&W)
    )
    val names = arrayOf("Blue", "Purple", "Teal", "Green", "Orange", "Mono")
    const val DEFAULT = 0 // Blue
}
