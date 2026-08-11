package com.blurt.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * A remembered interaction source to pair with clickable surfaces, buttons,
 * and icon buttons so their press state can drive [blurtPressScale].
 */
@Composable
fun rememberBlurtInteractionSource(): MutableInteractionSource =
    remember { MutableInteractionSource() }

/**
 * Subtle tactile feedback: the element dips slightly while pressed and
 * springs back. Pass the same interaction source the clickable uses.
 */
@Composable
fun Modifier.blurtPressScale(interactionSource: InteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "blurtPressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
