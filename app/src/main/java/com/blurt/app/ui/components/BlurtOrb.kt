package com.blurt.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blurt.app.ui.theme.rememberReduceMotion

/** The four orb states from the design board. */
enum class OrbState {
    /** Calm, soft pulse. Ready when you are. */
    IDLE,

    /** Active waveform. Listening closely. */
    RECORDING,

    /** Analyzing and organizing. */
    PROCESSING,

    /** Done and ready. Results are in. */
    COMPLETE,
}

/**
 * The heart of Blurt V2 — the glowing violet orb. One component, four states,
 * consistent everywhere the board shows it (Home, Listening, Processing,
 * Complete, empty and error states).
 *
 * The orb is a violet radial gradient with a soft outer glow. The glow
 * breathes gently in idle ("calm, soft pulse" — the board's words), and the
 * disc accepts an icon (mic, stop, check, exclamation). Motion only ever
 * answers a state change; the reduce-motion fallback is a static glow with
 * the same composition.
 */
@Composable
fun BlurtOrb(
    state: OrbState,
    size: Dp = 168.dp,
    icon: ImageVector? = null,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val infinite = rememberInfiniteTransition(label = "orbGlow")
    val breath by infinite.animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1_800, easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "orbBreath",
    )

    val glowAlpha = when (state) {
        OrbState.IDLE -> if (reduceMotion) 0.55f else 0.5f + 0.18f * breath
        OrbState.RECORDING -> 0.75f
        OrbState.PROCESSING -> 0.65f
        OrbState.COMPLETE -> 0.85f
    }

    // The violet gradient — the board's "glowing blue/purple gradient orb".
    val orbBrush = Brush.radialGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
        ),
        center = Offset(0.35f, 0.3f),
        radius = 1.15f,
    )

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (state == OrbState.PROCESSING && !reduceMotion) {
                    Modifier.scale(1f + 0.03f * breath)
                } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Outer glow — a wide soft halo behind the disc.
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha),
                            MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha * 0.35f),
                            Color.Transparent,
                        ),
                        center = Offset(0.5f, 0.5f),
                        radius = 1f,
                    ),
                ),
        )
        // The disc itself.
        val discSize = size * 0.62f
        if (onClick != null) {
            val source = rememberBlurtInteractionSource()
            Surface(
                onClick = onClick,
                interactionSource = source,
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier
                    .size(discSize)
                    .blurtPressScale(source),
            ) {
                OrbDisc(discSize = discSize, brush = orbBrush, icon = icon, contentDescription = contentDescription)
            }
        } else {
            OrbDisc(discSize = discSize, brush = orbBrush, icon = icon, contentDescription = contentDescription)
        }
    }
}

@Composable
private fun OrbDisc(
    discSize: Dp,
    brush: Brush,
    icon: ImageVector?,
    contentDescription: String?,
) {
    Box(
        modifier = Modifier
            .size(discSize)
            .clip(CircleShape)
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(discSize * 0.46f),
            )
        }
    }
}

/**
 * The thin arc that orbits the orb while Blurt processes — a quiet,
 * state-communicating loop (the board's "swirl orb"), frozen under reduce
 * motion. Pairs with [BlurtOrb] in the PROCESSING state.
 */
@Composable
fun OrbProcessingRing(size: Dp = 168.dp, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val infinite = rememberInfiniteTransition(label = "orbRing")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing), RepeatMode.Restart),
        label = "orbRotation",
    )
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer { if (!reduceMotion) rotationZ = rotation }
                .clip(CircleShape)
                .background(
                    Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        // Carve out the center so only a thin arc remains.
        Box(
            modifier = Modifier
                .size(size - 8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background),
        )
    }
}
