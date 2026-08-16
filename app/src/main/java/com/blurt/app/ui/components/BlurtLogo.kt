package com.blurt.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Blurt's brand mark — the same identity as the launcher icon: a lit glass
 * violet sphere with a soft glow and the white burst. The sphere is the
 * shared [BlurtOrbDisc] — the exact same gradient, specular, shade and rim
 * as the home orb — so the mark is pixel-identical everywhere it appears:
 * launcher, splash, sign-in, onboarding.
 */
@Composable
fun BlurtLogo(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        // The soft outer glow that lifts the orb off the canvas.
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            BlurtBrandMid.copy(alpha = 0.38f),
                            BlurtBrandMid.copy(alpha = 0.1f),
                            Color.Transparent,
                        ),
                        center = Offset(0.5f, 0.5f),
                        radius = 1f,
                    ),
                ),
        )
        BlurtOrbDisc(
            discSize = size * 0.74f,
            icon = BlurtIcons.BlurtMark,
            contentDescription = null,
        )
    }
}

/** The wordmark lockup — the orb mark with "Blurt" in the app's display
 *  type. For splash, sign-in, and onboarding headers. */
@Composable
fun BlurtWordmark(markSize: Dp = 40.dp, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        BlurtLogo(size = markSize)
        Spacer(Modifier.width(markSize * 0.3f))
        Text(
            text = "Blurt",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** The brand's orb gradient — the launcher's #9B7BFF → #5A45F2, lit top-left. */
private val orbBrush = Brush.radialGradient(
    colors = listOf(Color(0xFF9B7BFF), Color(0xFF7C5EF5), Color(0xFF5A45F2)),
    center = Offset(0.35f, 0.3f),
    radius = 1.15f,
)

/** The brand violet — the mid-point of the orb gradient. */
private val BrandViolet = Color(0xFF7C5EF5)
