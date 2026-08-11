package com.blurt.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Blurt's semantic palettes. Both themes share the same token names so every
 * component responds correctly when the theme switches — one product, two
 * visual modes.
 *
 * Dark follows the reference: near-black background, charcoal surfaces, warm
 * gold used selectively. Light mirrors it: white background, soft gray
 * surfaces, the same gold accent.
 */
object BlurtDark {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF161616)
    val SurfaceElevated = Color(0xFF1C1C1E)
    val Border = Color(0xFF2C2C2E)

    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFF8E8E93)
    val TextTertiary = Color(0xFF636366)

    val Accent = Color(0xFFE6B56A)        // warm Blurt gold
    val OnAccent = Color(0xFF1C1C1E)      // dark ink on gold
    val AccentSoft = Color(0xFF33291A)    // gold-tinted container

    val Error = Color(0xFFE06C6C)
}

object BlurtLight {
    val Background = Color(0xFFFFFFFF)
    val Surface = Color(0xFFF5F5F7)
    val SurfaceElevated = Color(0xFFFFFFFF)
    val Border = Color(0xFFE5E5EA)

    val TextPrimary = Color(0xFF1C1C1E)
    val TextSecondary = Color(0xFF6E6E73)
    val TextTertiary = Color(0xFF8E8E93)

    val Accent = Color(0xFFE6B56A)        // same gold accent
    val OnAccent = Color(0xFF1C1C1E)
    val AccentSoft = Color(0xFFF7EFDD)    // pale gold container

    val Error = Color(0xFFD64545)
}
