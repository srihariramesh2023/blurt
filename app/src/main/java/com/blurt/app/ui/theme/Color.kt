package com.blurt.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Blurt's semantic palettes — Apple's own system colors, verbatim from iOS
 * (design/BLURT-DESIGN-STANDARD.md §2): pure-black dark canvas, the
 * #F2F2F7 grouped gray in light, white cells, and label/separator colors
 * straight from the HIG. The one brand deviation is the accent: violet
 * instead of system blue, deepened in light and lightened in dark exactly
 * the way Apple shifts its accent between modes.
 *
 * Both themes share the same token names so every component responds
 * correctly when the theme switches. Labels are pure black/white with
 * opacity tiers (60% secondary, 30% tertiary) — never new grays.
 */
object BlurtDark {
    /** systemBackground — pure black, like Apple's own dark mode. */
    val Background = Color(0xFF000000)

    /** secondarySystemBackground — cards and cells. */
    val Surface = Color(0xFF1C1C1E)

    /** tertiarySystemBackground — elevated surfaces, sheets, menus. */
    val SurfaceElevated = Color(0xFF2C2C2E)

    /** labelColor — pure white. */
    val TextPrimary = Color(0xFFFFFFFF)

    /** Secondary label = primary at 60% — one opacity tier, no new grays. */
    val TextSecondary = TextPrimary.copy(alpha = 0.6f)

    /** Tertiary label = primary at 30% — decoration only, never body text. */
    val TextTertiary = TextPrimary.copy(alpha = 0.3f)

    /** separatorColor — the opaque hairline. */
    val Hairline = Color(0xFF38383A)

    /** The violet accent — lightened for dark, the way Apple lightens its
     *  accent in dark mode (systemBlue #0A84FF vs #007AFF). */
    val Accent = Color(0xFF7C6CFF)
    val OnAccent = Color(0xFFFFFFFF)

    /** A brighter violet for the orb's glow and gradient. */
    val AccentBright = Color(0xFF9B7BFF)

    /** Accent at ~16% — tinted fills, chips, focus softness. */
    val AccentSoft = Color(0x297C6CFF)

    /** systemRed — kept semantic. */
    val Error = Color(0xFFFF453A)

    /** systemGreen — completion checkmarks, toasts. */
    val Success = Color(0xFF30D158)

    /** secondarySystemBackground — the recessed input fill. */
    val FieldFill = Color(0xFF1C1C1E)
}

object BlurtLight {
    /** groupedSystemBackground — the standard light list canvas. */
    val Background = Color(0xFFF2F2F7)

    /** Cards and cells — white. */
    val Surface = Color(0xFFFFFFFF)

    /** Elevated surfaces. */
    val SurfaceElevated = Color(0xFFFFFFFF)

    /** labelColor — pure black. */
    val TextPrimary = Color(0xFF000000)

    /** Secondary label = primary at 60%. */
    val TextSecondary = TextPrimary.copy(alpha = 0.6f)

    /** Tertiary label = primary at 30%. */
    val TextTertiary = TextPrimary.copy(alpha = 0.3f)

    /** separatorColor — the opaque hairline. */
    val Hairline = Color(0xFFE5E5EA)

    /** The violet accent, deepened for contrast on white. */
    val Accent = Color(0xFF5A45F2)
    val OnAccent = Color(0xFFFFFFFF)

    /** Brighter violet for the orb's glow and gradient. */
    val AccentBright = Color(0xFF8B6CFF)

    /** Accent at ~12% — tinted fills, chips. */
    val AccentSoft = Color(0x1F5A45F2)

    /** systemRed. */
    val Error = Color(0xFFFF3B30)

    /** systemGreen. */
    val Success = Color(0xFF34C759)

    /** secondarySystemFill — the recessed input fill over the grouped gray. */
    val FieldFill = Color(0xFFE9E9EC)
}
