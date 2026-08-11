package com.blurt.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Blurt is dark-first by design — no light mode in Phase 1.
 */
private val BlurtColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    primaryContainer = AmberSoft,
    onPrimaryContainer = Amber,
    secondary = Amber,
    onSecondary = Ink,
    secondaryContainer = AmberSoft,
    onSecondaryContainer = Amber,
    background = Ink,
    onBackground = Paper,
    surface = InkRaised,
    onSurface = Paper,
    surfaceVariant = InkOverlay,
    onSurfaceVariant = Mute,
    outline = Line,
    outlineVariant = Line,
    error = Rose,
    onError = Ink,
)

@Composable
fun BlurtTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BlurtColorScheme,
        typography = BlurtTypography,
        content = content,
    )
}
