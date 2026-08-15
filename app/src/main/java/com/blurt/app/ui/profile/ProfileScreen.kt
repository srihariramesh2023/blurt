package com.blurt.app.ui.profile

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.blurt.app.BuildConfig
import com.blurt.app.auth.AuthUser
import com.blurt.app.ui.components.AiKeyDialog
import com.blurt.app.ui.components.AiKeyViewModel
import com.blurt.app.ui.components.BlurtIcons
import com.blurt.app.ui.components.LocalBlurtScrollState
import com.blurt.app.ui.components.LocalTabBarInset
import com.blurt.app.ui.components.blurtPressScale
import com.blurt.app.ui.components.rememberBlurtInteractionSource
import com.blurt.app.ui.theme.BlurtRadii
import com.blurt.app.ui.theme.BlurtSpacing
import com.blurt.app.ui.theme.ThemeMode

/**
 * The account surface — a full page, not a menu: the Google avatar and name
 * at the top, Appearance (System / Light / Dark), then the settings rows.
 * Every row does something real — nothing is decorative.
 */
@Composable
fun ProfileScreen(
    user: AuthUser,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onSignOut: () -> Unit,
    aiKeyViewModel: AiKeyViewModel = viewModel(factory = AiKeyViewModel.Factory),
) {
    val context = LocalContext.current
    var showAiDialog by remember { mutableStateOf(false) }
    var infoDialog by remember { mutableStateOf<InfoRow?>(null) }

    // The shell's shared scroll state, so the frosted copy under the tab bar
    // scrolls in lockstep with the sharp layer.
    val scrollState = LocalBlurtScrollState.current ?: rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = BlurtSpacing.grouped),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = LocalTabBarInset.current + BlurtSpacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(BlurtSpacing.l))
            // The account header — avatar, name, email.
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (!user.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = user.photoUrl,
                        contentDescription = "Profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Text(
                        text = initials(user.displayName),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(BlurtSpacing.m))
            Text(
                text = user.displayName ?: "Blurt account",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (!user.email.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(BlurtSpacing.xxl))

            // Appearance — the theme lives here, one tap from the avatar.
            Text(
                text = "APPEARANCE",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(BlurtSpacing.s))
            Surface(
                shape = RoundedCornerShape(BlurtRadii.m),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    ThemeOptionRow(ThemeMode.SYSTEM, BlurtIcons.Monitor, "System", themeMode, onThemeChange)
                    DividerLine()
                    ThemeOptionRow(ThemeMode.LIGHT, BlurtIcons.Sun, "Light", themeMode, onThemeChange)
                    DividerLine()
                    ThemeOptionRow(ThemeMode.DARK, BlurtIcons.Moon, "Dark", themeMode, onThemeChange)
                }
            }

            Spacer(Modifier.height(BlurtSpacing.l))
            Text(
                text = "SETTINGS",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(BlurtSpacing.s))
            Surface(
                shape = RoundedCornerShape(BlurtRadii.m),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    SettingsRow(
                        icon = BlurtIcons.Key,
                        title = "AI keys",
                        subtitle = "Bring your own free Groq or Gemini key",
                        onClick = { showAiDialog = true },
                    )
                    DividerLine()
                    SettingsRow(
                        icon = BlurtIcons.Bell,
                        title = "Notifications",
                        subtitle = "Reminder alerts and quick actions",
                        onClick = {
                            runCatching {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                context.startActivity(intent)
                            }
                        },
                    )
                    DividerLine()
                    SettingsRow(
                        icon = BlurtIcons.Mic,
                        title = "Voice & Transcription",
                        subtitle = "Speech stays on this device",
                        onClick = { infoDialog = InfoRow.VOICE },
                    )
                    DividerLine()
                    SettingsRow(
                        icon = BlurtIcons.BlurtMark,
                        title = "Privacy",
                        subtitle = "Your blurts belong to you",
                        onClick = { infoDialog = InfoRow.PRIVACY },
                    )
                    DividerLine()
                    SettingsRow(
                        icon = BlurtIcons.Archive,
                        title = "Data",
                        subtitle = "Synced to your account",
                        onClick = { infoDialog = InfoRow.DATA },
                    )
                    DividerLine()
                    SettingsRow(
                        icon = BlurtIcons.Sparkle,
                        title = "About",
                        subtitle = "Blurt ${BuildConfig.VERSION_NAME}",
                        onClick = { infoDialog = InfoRow.ABOUT },
                    )
                }
            }

            Spacer(Modifier.height(BlurtSpacing.l))
            // Sign out — the one destructive row, in system red.
            Surface(
                shape = RoundedCornerShape(BlurtRadii.m),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val signOutSource = rememberBlurtInteractionSource()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .clickable(onClick = onSignOut, interactionSource = signOutSource, indication = null)
                        .blurtPressScale(signOutSource)
                        .padding(horizontal = BlurtSpacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(BlurtSpacing.s))
                    Text(
                        text = "Sign out",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            // Air so the last row clears the floating glass pill.
            Spacer(Modifier.height(BlurtSpacing.s))
        }
    }

    if (showAiDialog) {
        AiKeyDialog(
            viewModel = aiKeyViewModel,
            onDismiss = { showAiDialog = false },
        )
    }

    infoDialog?.let { info ->
        AlertDialog(
            onDismissRequest = { infoDialog = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(BlurtRadii.xl),
            title = { Text(info.title) },
            text = { Text(info.body, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { infoDialog = null }) {
                    Text("Done", color = MaterialTheme.colorScheme.primary)
                }
            },
        )
    }
}

/** A hairline that separates rows inside grouped cards (the iOS separator). */
@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

/** One appearance choice — icon, label, checkmark when selected. */
@Composable
private fun ThemeOptionRow(
    mode: ThemeMode,
    icon: ImageVector,
    label: String,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val selected = mode == current
    val source = rememberBlurtInteractionSource()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clickable(onClick = { onSelect(mode) }, interactionSource = source, indication = null)
            .blurtPressScale(source)
            .padding(horizontal = BlurtSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(BlurtSpacing.s))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** One settings row — icon, title, subtitle, chevron. */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val source = rememberBlurtInteractionSource()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClick = onClick, interactionSource = source, indication = null)
            .blurtPressScale(source)
            .padding(horizontal = BlurtSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(BlurtSpacing.s))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The informative rows — real content, presented as a quiet dialog. */
private enum class InfoRow(val title: String, val body: String) {
    VOICE(
        "Voice & Transcription",
        "Blurt listens only while you're recording. Transcription runs through your device's speech service, and the mic is never left open in the background.",
    ),
    PRIVACY(
        "Privacy",
        "Your blurts belong to you. Everything you capture is tied to your Google account, and nothing is ever sold or shared. Signing out clears the local session immediately.",
    ),
    DATA(
        "Data",
        "Blurts sync privately to your account so they're available across devices. You can delete any blurt at any time, and deleting it removes it from the sync backend too.",
    ),
    ABOUT(
        "About Blurt",
        "Blurt is a place to instantly say something — and Blurt figures out what it means. Version ${BuildConfig.VERSION_NAME}.",
    ),
}

private fun initials(displayName: String?): String {
    val parts = displayName.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "B"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}
