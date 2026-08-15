package com.blurt.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Blurt's brand mark — an iOS-style app icon: a solid violet rounded square
 * (the theme accent) with the white speech-bubble glyph. The corner radius
 * approximates Apple's squircle. The accent is the only color — no
 * gradients, no glass, no gold.
 */
@Composable
fun BlurtLogo(size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.2237f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary, shape = shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = BlurtIcons.BlurtMark,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(size * 0.58f),
        )
    }
}
