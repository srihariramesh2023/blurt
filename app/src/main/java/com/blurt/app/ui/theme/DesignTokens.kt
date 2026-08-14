package com.blurt.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Centralized design tokens — spacing, corner radii, and motion timings.
 * Components read from these so the product stays consistent and the look
 * can be tuned in one place. Radii follow iOS (design/BLURT-DESIGN-STANDARD.md
 * §4): grouped list cards ~12, buttons ~14, sheets ~16; pills stay capsules
 * for chips and the Google button.
 */
object BlurtSpacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val screen = l

    /** iOS inset for grouped lists and nav content — 16pt from the edge. */
    val grouped = 16.dp
}

object BlurtRadii {
    /** Capsule — chips, pills, the Google button. */
    val pill = 999.dp
    /** Tiles, small fills. */
    val s = 12.dp
    /** Grouped list cards. */
    val m = 12.dp
    /** Buttons, containers. */
    val l = 14.dp
    /** Sheets — top corners. */
    val xl = 16.dp
}

object BlurtDuration {
    /** Press/selection feedback. */
    val fast = 150
    /** Small transitions (fades, slides). */
    val medium = 250
    /** Larger entrances (sheets). */
    val slow = 400
}

val BlurtShapes = Shapes(
    small = RoundedCornerShape(BlurtRadii.s),
    medium = RoundedCornerShape(BlurtRadii.m),
    large = RoundedCornerShape(BlurtRadii.l),
    extraLarge = RoundedCornerShape(BlurtRadii.xl),
)
