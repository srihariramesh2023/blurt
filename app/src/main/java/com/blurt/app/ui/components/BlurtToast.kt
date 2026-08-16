package com.blurt.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.blurt.app.ui.theme.BlurtMotion
import com.blurt.app.ui.theme.BlurtRadii
import com.blurt.app.ui.theme.BlurtSpacing
import com.blurt.app.ui.theme.rememberReduceMotion
import com.blurt.app.ui.theme.successColor
import kotlinx.coroutines.delay

/**
 * Blurt's toast — a quiet green pill that rises from the bottom, holds, and
 * leaves. Used for the save confirmation ("Blurt saved. Message ChatGPT.
 * You'll be reminded in 5 min."). Success-only; errors use inline text so
 * they stay visible until acted on.
 */
@Composable
fun BlurtToast(
    message: String,
    onDismissed: () -> Unit,
    durationMs: Long = 1_700L,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    val reduceMotion = rememberReduceMotion()
    LaunchedEffect(Unit) {
        visible = true
        delay(durationMs)
        visible = false
        delay(if (reduceMotion) 150L else 240L)
        onDismissed()
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (reduceMotion) {
            fadeIn(androidx.compose.animation.core.tween(BlurtMotion.FADE_MS))
        } else {
            fadeIn(BlurtMotion.standard()) +
                slideInVertically(BlurtMotion.entrance()) { it / 2 }
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        // The pill itself: white text on system green, a soft shadow, rounded
        // like everything else in Blurt. Success is the one semantic green.
        Surface(
            shape = RoundedCornerShape(BlurtRadii.pill),
            color = successColor(),
            shadowElevation = 8.dp,
            modifier = Modifier
                .shadow(10.dp, RoundedCornerShape(BlurtRadii.pill), clip = false)
                .padding(horizontal = BlurtSpacing.grouped),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = BlurtSpacing.m, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = BlurtIcons.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    // A long blurt must never blow the pill up — cap it and
                    // ellipsize so the toast stays a toast.
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}
