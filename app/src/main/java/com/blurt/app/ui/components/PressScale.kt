package com.blurt.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.blurt.app.ui.theme.BlurtMotion

/**
 * A remembered interaction source to pair with clickable surfaces, buttons,
 * and icon buttons so their press state can drive [blurtPressScale].
 */
@Composable
fun rememberBlurtInteractionSource(): MutableInteractionSource =
    remember { MutableInteractionSource() }

/**
 * Subtle tactile feedback: the element dips to ~0.98 and loses a touch of
 * opacity while pressed, then springs back fast, with no overshoot. Pass the
 * same interaction source the clickable uses (design standard §6).
 */
@Composable
fun Modifier.blurtPressScale(interactionSource: InteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = BlurtMotion.micro(),
        label = "blurtPressScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = BlurtMotion.micro(),
        label = "blurtPressAlpha",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}
