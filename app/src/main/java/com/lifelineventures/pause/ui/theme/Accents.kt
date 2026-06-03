package com.lifelineventures.pause.ui.theme

/**
 * Accent color choices, shared by the Compose theme (setup screen) and the overlay
 * picker tint. Stored as ARGB ints so they work both as Android color ints and, via
 * Color(int), as Compose colors. The bubble icons deliberately ignore the accent.
 */
object Accents {
    val colors = intArrayOf(
        0xFF7C5CFF.toInt(), // Purple
        0xFF4C8DFF.toInt(), // Blue
        0xFF15B8A6.toInt(), // Teal
        0xFF3FB950.toInt(), // Green
        0xFFFF8A3D.toInt()  // Orange
    )
    val names = arrayOf("Purple", "Blue", "Teal", "Green", "Orange")
    const val DEFAULT = 0

    fun safeIndex(index: Int): Int = if (index in colors.indices) index else DEFAULT
}
