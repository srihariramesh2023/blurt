package com.blurt.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
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

/** The brand's orb gradient — the launcher's own colors, one voice
 *  everywhere: launcher, splash, sign-in, onboarding, home. */
internal val BlurtBrandHighlight = Color(0xFFB9A3FF)
internal val BlurtBrandMid       = Color(0xFF9B7BFF)
internal val BlurtBrandAccent    = Color(0xFF7C5EF5)
internal val BlurtBrandDeep      = Color(0xFF5A45F2)
internal val BlurtBrandShadow    = Color(0xFF24136E)

/**
 * The heart of Blurt V2 — the glowing violet orb. One component, four states,
 * consistent everywhere the board shows it (Home, Listening, Processing,
 * Complete, empty and error states).
 *
 * The orb is a lit glass sphere: the brand's radial gradient with a top-left
 * light source, a specular catch, an ambient shadow at the bottom, a bright
 * rim where light grazes the edge, and a slow sheen sweeping the glass while
 * idle. The glow breathes gently in idle ("calm, soft pulse" — the board's
 * words), and the disc accepts an icon (mic, stop, check, exclamation).
 * Motion only ever answers a state change; the reduce-motion fallback is a
 * static sphere with the same composition.
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
        OrbState.IDLE -> if (reduceMotion) 0.7f else 0.68f + 0.22f * breath
        OrbState.RECORDING -> 0.95f
        OrbState.PROCESSING -> 0.85f
        OrbState.COMPLETE -> 0.85f
    }
    // The resting orb wears a vivid sunset gradient; active states stay brand-violet.
    val discBase = if (state == OrbState.IDLE) VividSphereBase else SphereBase
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
                            BlurtBrandMid.copy(alpha = glowAlpha),
                            BlurtBrandMid.copy(alpha = glowAlpha * 0.35f),
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
                BlurtOrbDisc(
                    discSize = discSize,
                    icon = icon,
                    contentDescription = contentDescription,
                    sheen = state == OrbState.IDLE || state == OrbState.RECORDING,
                    base = discBase,
                )
            }

        } else {
            BlurtOrbDisc(
                discSize = discSize,
                icon = icon,
                contentDescription = contentDescription,
                sheen = state == OrbState.IDLE || state == OrbState.RECORDING,
                base = discBase,
            )
        }
    }
}

/**
 * The orb's disc — a lit glass sphere shared by [BlurtOrb] and the brand
 * logo, so the mark is pixel-identical everywhere it appears.
 *
 * Layers, light source top-left: the brand radial gradient, a white specular
 * catch, an ambient shadow at the bottom-right, a bright rim at the very
 * edge (the light grazing the glass), and — when [sheen] — a slow light band
 * sweeping across the sphere. The icon sits on top with a soft shadow.
 */
@Composable
internal fun BlurtOrbDisc(
    discSize: Dp,
    icon: ImageVector?,
    contentDescription: String?,
    sheen: Boolean = false,
    base: Brush = SphereBase,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val infinite = rememberInfiniteTransition(label = "orbSheen")
    val sheenRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8_000, easing = LinearEasing), RepeatMode.Restart),
        label = "orbSheenRotation",
    )

    Box(
        modifier = modifier
            .size(discSize)
            .clip(CircleShape)
            .drawBehind {
                drawCircle(base)
                drawCircle(SphereSpecular)
                drawCircle(SphereShade)
                drawCircle(SphereRim)
                if (sheen && !reduceMotion) {
                    rotate(degrees = sheenRotation) {
                        drawRect(SphereSheen)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            // The mark's soft shadow — a whisper of depth under the icon.
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.18f),
                modifier = Modifier
                    .size(discSize * 0.46f)
                    .offset(y = discSize * 0.02f),
            )
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(discSize * 0.46f),
            )
        }
    }
}

/** The resting orb's skin — a vivid sunset gradient, violet to pink to amber. */
private val VividSphereBase = Brush.radialGradient(
    colors = listOf(Color(0xFFB9A3FF), Color(0xFFFF8AD8), Color(0xFFFFB066)),
    center = Offset(0.38f, 0.34f),
    radius = 1.1f,
)
/** The sphere's base — the brand gradient, lit from the top-left. */
private val SphereBase = Brush.radialGradient(
    colors = listOf(BlurtBrandHighlight, BlurtBrandMid, BlurtBrandAccent, BlurtBrandDeep),
    center = Offset(0.38f, 0.34f),
    radius = 1.1f,
)

/** The glass catch — a soft white bloom where the light hits. */
private val SphereSpecular = Brush.radialGradient(
    colors = listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.08f), Color.Transparent),
    center = Offset(0.30f, 0.24f),
    radius = 0.62f,
)

/** Ambient occlusion — the sphere falls into shadow at the bottom-right. */
private val SphereShade = Brush.radialGradient(
    colors = listOf(Color.Transparent, BlurtBrandShadow.copy(alpha = 0.30f)),
    center = Offset(0.66f, 0.78f),
    radius = 0.95f,
)

/** The rim — a bright hairline at the very edge where light grazes glass. */
private val SphereRim = Brush.radialGradient(
    colorStops = arrayOf(
        0f to Color.Transparent,
        0.82f to Color.Transparent,
        0.92f to Color.White.copy(alpha = 0.16f),
        1f to Color.White.copy(alpha = 0.36f),
    ),
    center = Offset(0.5f, 0.5f),
    radius = 0.5f,
)

/** The slow sheen — a soft light band sweeping the glass. */
private val SphereSheen = Brush.linearGradient(
    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
    start = Offset(0f, 0f),
    end = Offset(1f, 1f),
)

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
                            BlurtBrandMid.copy(alpha = 0.9f),
                            BlurtBrandMid.copy(alpha = 0.9f),
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


