package com.blurt.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Both themes map the same semantic tokens onto Material3's slots, so
 * components written against colorScheme automatically re-skin correctly.
 */
private val DarkScheme = darkColorScheme(
    primary = BlurtDark.Accent,
    onPrimary = BlurtDark.OnAccent,
    primaryContainer = BlurtDark.AccentSoft,
    onPrimaryContainer = BlurtDark.Accent,
    secondary = BlurtDark.Accent,
    onSecondary = BlurtDark.OnAccent,
    secondaryContainer = BlurtDark.AccentSoft,
    onSecondaryContainer = BlurtDark.Accent,
    tertiary = BlurtDark.TextTertiary,
    onTertiary = BlurtDark.TextPrimary,
    background = BlurtDark.Background,
    onBackground = BlurtDark.TextPrimary,
    surface = BlurtDark.Surface,
    onSurface = BlurtDark.TextPrimary,
    surfaceVariant = BlurtDark.SurfaceElevated,
    onSurfaceVariant = BlurtDark.TextSecondary,
    outline = BlurtDark.Border,
    outlineVariant = BlurtDark.Border,
    error = BlurtDark.Error,
    onError = BlurtDark.TextPrimary,
    surfaceTint = BlurtDark.Accent,
)

private val LightScheme = lightColorScheme(
    primary = BlurtLight.Accent,
    onPrimary = BlurtLight.OnAccent,
    primaryContainer = BlurtLight.AccentSoft,
    onPrimaryContainer = BlurtLight.Accent,
    secondary = BlurtLight.Accent,
    onSecondary = BlurtLight.OnAccent,
    secondaryContainer = BlurtLight.AccentSoft,
    onSecondaryContainer = BlurtLight.Accent,
    tertiary = BlurtLight.TextTertiary,
    onTertiary = BlurtLight.TextPrimary,
    background = BlurtLight.Background,
    onBackground = BlurtLight.TextPrimary,
    surface = BlurtLight.Surface,
    onSurface = BlurtLight.TextPrimary,
    surfaceVariant = BlurtLight.SurfaceElevated,
    onSurfaceVariant = BlurtLight.TextSecondary,
    outline = BlurtLight.Border,
    outlineVariant = BlurtLight.Border,
    error = BlurtLight.Error,
    onError = Color.White,
    surfaceTint = BlurtLight.Accent,
)

@Composable
fun BlurtTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = BlurtTypography,
        content = content,
    )
}
