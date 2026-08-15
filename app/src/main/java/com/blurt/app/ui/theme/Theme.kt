package com.blurt.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance

/**
 * Both themes map the same semantic tokens onto Material3's slots, so
 * components written against colorScheme automatically re-skin correctly.
 * The mappings follow iOS conventions: surfaceVariant carries elevated
 * surfaces (sheets/menus), onSurfaceVariant is secondary label, tertiary is
 * secondary label (section headers), and primary is the system-blue tint.
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
    outline = BlurtDark.Hairline,
    outlineVariant = BlurtDark.Hairline,
    error = BlurtDark.Error,
    onError = Color.White,
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
    outline = BlurtLight.Hairline,
    outlineVariant = BlurtLight.Hairline,
    error = BlurtLight.Error,
    onError = Color.White,
    surfaceTint = BlurtLight.Accent,
)

/**
 * The iOS search-field fill — a quiet gray that reads as a recessed input
 * against both page backgrounds. Read from the active theme's darkness so it
 * stays correct even when the user overrides the system theme.
 */
@Composable
fun fieldFill(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) BlurtDark.FieldFill
    else BlurtLight.FieldFill

/**
 * The semantic success green (iOS systemGreen), resolved for the active
 * theme — toasts, completions. Exposed because Material3's scheme has no
 * success slot; the semantic color only ever means success.
 */
@Composable
fun successColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) BlurtDark.Success
    else BlurtLight.Success

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
