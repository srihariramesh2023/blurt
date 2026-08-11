package com.blurt.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Blurt's brand mark: a black rounded tile with the speech-bubble glyph —
 * gold in dark mode (with a subtle gold border), white in light mode —
 * matching the reference identity.
 */
@Composable
fun BlurtLogo(size: Dp, modifier: Modifier = Modifier) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Color.Black)
            .then(
                if (isDark) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = BlurtIcons.BlurtMark,
            contentDescription = null,
            tint = if (isDark) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.size(size * 0.6f),
        )
    }
}
