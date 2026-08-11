package com.blurt.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Centralized design tokens — spacing, corner radii, and motion timings.
 * Components should read from these so the product stays consistent and the
 * look can be tuned in one place.
 */
object BlurtSpacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val screen = xl
}

object BlurtRadii {
    val s = 12.dp
    val m = 16.dp
    val l = 20.dp
    val xl = 24.dp
}

object BlurtDuration {
    /** Press/selection feedback. */
    val fast = 150
    /** Small transitions (fades, slides). */
    val medium = 250
    /** Larger entrances. */
    val slow = 400
}

val BlurtShapes = Shapes(
    small = RoundedCornerShape(BlurtRadii.s),
    medium = RoundedCornerShape(BlurtRadii.m),
    large = RoundedCornerShape(BlurtRadii.l),
    extraLarge = RoundedCornerShape(BlurtRadii.xl),
)
