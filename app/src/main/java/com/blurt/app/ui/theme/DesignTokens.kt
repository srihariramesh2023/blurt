package com.blurt.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Centralized design tokens — spacing, corner radii, and motion.
 *
 * Spacing follows the 8pt grid (design standard §4): every value is a
 * multiple of 8, with 4pt reserved for fine adjustments inside components.
 * Components read from these so the product stays consistent and the look
 * can be tuned in one place.
 */
object BlurtSpacing {
    /** 4 — fine adjustment within a component only. */
    val xs = 4.dp
    /** 8. */
    val s = 8.dp
    /** 16 — the standard screen-edge margin and the common gap. */
    val m = 16.dp
    /** 24. */
    val l = 24.dp
    /** 32. */
    val xl = 32.dp
    /** 40. */
    val xxl = 40.dp
    /** 48. */
    val xxxl = 48.dp

    /** Screen-edge inset for grouped lists and nav content — 16pt. */
    val grouped = 16.dp
    val screen = 16.dp
}

object BlurtRadii {
    /** Capsule — chips, pills, the Google button. */
    val pill = 999.dp
    /** Tiles, small fills (8–12 per HIG). */
    val s = 12.dp
    /** Grouped list cards (16–20 per HIG). */
    val m = 12.dp
    /** Buttons, containers (8–12 per HIG). */
    val l = 14.dp
    /** Sheets — top corners (larger than cards). */
    val xl = 16.dp
}

/**
 * Motion — spring physics, never linear/ease curves (design standard §7).
 *
 * Apple's spring language, mapped to Compose. Compose's `stiffness` is
 * Apple's `response` in disguise: response ≈ 2π/√stiffness seconds.
 *
 *   micro     ≈ 0.09 s — presses, toggles        (damping 1.0, no overshoot)
 *   standard  ≈ 0.31 s — ordinary transitions    (damping 1.0, no overshoot)
 *   entrance  ≈ 0.31 s — sheets, full-screen     (damping 0.75)
 *
 * The entrance's slight overshoot mirrors Apple's sheet/drawer spring
 * (damping ~0.8, response ~0.3) — bounce appears only where a surface
 * physically arrives, never on routine fades. Every spring animates from
 * the current on-screen value, so all of these are interruptible and can be
 * re-targeted mid-flight (the fluid-interface principle). When the OS
 * requests reduced motion, callers fall back to a plain fade via
 * [BlurtMotion.FADE_MS].
 */
object BlurtMotion {
    /** Presses, toggles, tiny reveals. No overshoot. */
    fun <T> micro(): SpringSpec<T> = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessHigh)
    /** Ordinary transitions (fades, small slides). No overshoot. */
    fun <T> standard(): SpringSpec<T> = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)
    /** Sheets, full-screen entrances — gentle overshoot, like Apple's 0.8. */
    fun <T> entrance(): SpringSpec<T> = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)

    /** The reduce-motion fallback: a plain fade of this duration. */
    const val FADE_MS = 200
}

/**
 * Honors the OS "remove animations" setting (the animator-duration proxy).
 * Reduced motion swaps every spring/slide for a fade-only fallback.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

val BlurtShapes = Shapes(
    small = RoundedCornerShape(BlurtRadii.s),
    medium = RoundedCornerShape(BlurtRadii.m),
    large = RoundedCornerShape(BlurtRadii.l),
    extraLarge = RoundedCornerShape(BlurtRadii.xl),
)
