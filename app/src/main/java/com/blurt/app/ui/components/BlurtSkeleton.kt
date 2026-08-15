package com.blurt.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.blurt.app.ui.theme.BlurtRadii
import com.blurt.app.ui.theme.BlurtSpacing
import com.blurt.app.ui.theme.rememberReduceMotion

/**
 * Skeleton placeholder cards for list screens — shown only in the gap before
 * the first real data lands, so the UI never flashes a wrong "empty" state.
 * The bars quietly pulse; under reduce motion they sit still (design standard
 * §12 — looping indicators freeze).
 */
@Composable
fun BlurtListSkeleton(
    items: Int = 4,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val alpha = if (reduceMotion) {
        0.65f
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "skeletonAlpha",
        ).value
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(BlurtSpacing.s),
    ) {
        repeat(items) {
            Surface(
                shape = RoundedCornerShape(BlurtRadii.m),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .graphicsLayer { this.alpha = alpha },
            ) {
                Row(
                    modifier = Modifier.padding(BlurtSpacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    )
                    Spacer(Modifier.width(BlurtSpacing.m))
                    Column {
                        Bar(widthFraction = 0.6f, height = 14.dp, strong = true)
                        Spacer(Modifier.height(BlurtSpacing.s))
                        Bar(widthFraction = 0.4f, height = 12.dp, strong = false)
                    }
                }
            }
        }
    }
}

/** One muted placeholder bar, a fraction of the card's width. */
@Composable
private fun Bar(
    widthFraction: Float,
    height: androidx.compose.ui.unit.Dp,
    strong: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (strong) 0.22f else 0.12f,
                ),
                shape = RoundedCornerShape(BlurtRadii.pill),
            ),
    )
}
