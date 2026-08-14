package com.blurt.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Blurt's semantic palettes — the iOS system look, straight from Apple's HIG
 * (design/BLURT-DESIGN-STANDARD.md).
 *
 * Both themes share the same token names so every component responds
 * correctly when the theme switches — one product, two visual modes. The
 * accent is iOS system blue (#007AFF light / #0A84FF dark) and appears in
 * the same roles Apple reserves for tint: active state, primary action,
 * meaning/signal, and the brand mark. No gold — the previous custom identity
 * is gone.
 */
object BlurtDark {
    /** iOS systemBackground / grouped background — the page canvas. */
    val Background = Color(0xFF000000)

    /** iOS secondarySystemGroupedBackground — grouped list cards. */
    val Surface = Color(0xFF1C1C1E)

    /** iOS tertiarySystemGroupedBackground / elevated — sheets, menus, bars. */
    val SurfaceElevated = Color(0xFF2C2C2E)

    /** iOS label. */
    val TextPrimary = Color(0xFFFFFFFF)

    /** iOS secondaryLabel (Apple's exact dark value). */
    val TextSecondary = Color(0xFFAEAEB2)

    /** iOS tertiaryLabel / systemGray. */
    val TextTertiary = Color(0xFF8E8E93)

    /** iOS separator — full opacity here; callers thin it with alpha. */
    val Hairline = Color(0xFF38383A)

    /** iOS systemBlue (dark). */
    val Accent = Color(0xFF0A84FF)
    val OnAccent = Color(0xFFFFFFFF)

    /** systemBlue at ~15% — tinted fills, chips, focus softness. */
    val AccentSoft = Color(0x260A84FF)

    /** iOS systemRed (dark). */
    val Error = Color(0xFFFF453A)

    /** iOS systemGreen (dark) — toggles, success. */
    val Success = Color(0xFF30D158)

    /** iOS search-field fill (secondarySystemFill over black). */
    val FieldFill = Color(0xFF161618)
}

object BlurtLight {
    /** iOS systemGroupedBackground — the page canvas. */
    val Background = Color(0xFFF2F2F7)

    /** iOS secondarySystemGroupedBackground — grouped list cards. */
    val Surface = Color(0xFFFFFFFF)

    /** iOS tertiarySystemGroupedBackground / elevated. */
    val SurfaceElevated = Color(0xFFFFFFFF)

    /** iOS label. */
    val TextPrimary = Color(0xFF000000)

    /** iOS secondaryLabel ≈ systemGray. */
    val TextSecondary = Color(0xFF8E8E93)

    /** iOS tertiaryLabel. */
    val TextTertiary = Color(0xFFC7C7CC)

    /** iOS separator. */
    val Hairline = Color(0xFFC6C6C8)

    /** iOS systemBlue. */
    val Accent = Color(0xFF007AFF)
    val OnAccent = Color(0xFFFFFFFF)

    /** systemBlue at ~10% — tinted fills, chips, focus softness. */
    val AccentSoft = Color(0x1A007AFF)

    /** iOS systemRed. */
    val Error = Color(0xFFFF3B30)

    /** iOS systemGreen — toggles, success. */
    val Success = Color(0xFF34C759)

    /** iOS search-field fill (secondarySystemFill over grouped bg). */
    val FieldFill = Color(0xFFE9E9EE)
}
