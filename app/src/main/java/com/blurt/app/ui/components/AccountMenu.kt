package com.blurt.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.blurt.app.auth.AuthUser
import com.blurt.app.ui.theme.BlurtRadii
import com.blurt.app.ui.theme.BlurtSpacing
import com.blurt.app.ui.theme.ThemeMode

/**
 * The signed-in user's avatar. Tapping it opens a clean, Apple-style popover
 * with the account, an Appearance section (System / Light / Dark), an
 * AI & Groq entry (BYOK — paste a free key, stored encrypted in the Android
 * Keystore), and a single Sign out action.
 */
@Composable
fun AccountMenu(
    user: AuthUser,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    aiKeyViewModel: AiKeyViewModel = viewModel(factory = AiKeyViewModel.Factory),
) {
    val interactionSource = rememberBlurtInteractionSource()
    var expanded by remember { mutableStateOf(false) }
    var showAiDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            interactionSource = interactionSource,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .size(34.dp)
                .blurtPressScale(interactionSource),
        ) {
            if (!user.photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = user.photoUrl,
                    contentDescription = "Account",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initials(user.displayName),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(BlurtSpacing.l),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            // Account header.
            Row(
                modifier = Modifier.padding(horizontal = BlurtSpacing.l, vertical = BlurtSpacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!user.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = user.photoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        Text(
                            text = initials(user.displayName),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.width(BlurtSpacing.m))
                Column {
                    Text(
                        text = user.displayName ?: "Blurt account",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!user.email.isNullOrBlank()) {
                        Text(
                            text = user.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Appearance.
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = BlurtSpacing.l,
                    top = BlurtSpacing.m,
                    bottom = BlurtSpacing.xs,
                ),
            )
            ThemeOptionRow(ThemeMode.SYSTEM, BlurtIcons.Monitor, themeMode, onThemeChange)
            ThemeOptionRow(ThemeMode.LIGHT, BlurtIcons.Sun, themeMode, onThemeChange)
            ThemeOptionRow(ThemeMode.DARK, BlurtIcons.Moon, themeMode, onThemeChange)

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // AI & Groq — bring your own key.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(BlurtSpacing.s))
                    .clickable {
                        expanded = false
                        showAiDialog = true
                    }
                    .padding(horizontal = BlurtSpacing.l, vertical = BlurtSpacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = BlurtIcons.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "AI & Groq",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Sign out.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(BlurtSpacing.s))
                    .clickable { expanded = false; onSignOut() }
                    .padding(horizontal = BlurtSpacing.l, vertical = BlurtSpacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Sign out",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    if (showAiDialog) {
        AiKeyDialog(
            viewModel = aiKeyViewModel,
            onDismiss = { showAiDialog = false },
        )
    }
}

/**
 * The BYOK sheet: one section per provider. A masked field for a free key, a
 * live Save & Check probe, and a Remove action. Keys are stored encrypted in
 * the Android Keystore and take effect on the next analysis — no rebuild
 * needed.
 */
@Composable
private fun AiKeyDialog(
    viewModel: AiKeyViewModel,
    onDismiss: () -> Unit,
) {
    val draftKey by viewModel.draftKey.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val geminiDraftKey by viewModel.geminiDraftKey.collectAsStateWithLifecycle()
    val geminiStatus by viewModel.geminiStatus.collectAsStateWithLifecycle()
    val savedTail = viewModel.savedKeyTail()
    val geminiSavedTail = viewModel.savedGeminiKeyTail()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(BlurtRadii.l),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(BlurtSpacing.xl),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = BlurtIcons.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "AI keys",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(BlurtSpacing.s))
                Text(
                    text = "Paste your own free keys to power Blurt. Each is stored encrypted on this device and never leaves it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(BlurtSpacing.m))
                AiKeySection(
                    title = "Groq — classification",
                    icon = BlurtIcons.Bolt,
                    description = "Reads every blurt to pick its type, category, and any reminder time.",
                    noKeyText = "No key — blurts save unclassified",
                    draftKey = draftKey,
                    savedTail = savedTail,
                    status = status,
                    providerName = "Groq",
                    placeholder = "gsk_…",
                    onDraftChange = viewModel::onDraftChange,
                    onSaveAndCheck = viewModel::saveAndCheck,
                    onRemove = viewModel::removeKey,
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = BlurtSpacing.m),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )

                AiKeySection(
                    title = "Gemini — semantic search",
                    icon = BlurtIcons.Sparkle,
                    description = "Backup classifier, plus meaning-based search across your library.",
                    noKeyText = "No key — search falls back to keywords",
                    draftKey = geminiDraftKey,
                    savedTail = geminiSavedTail,
                    status = geminiStatus,
                    providerName = "Gemini",
                    placeholder = "AIza…",
                    onDraftChange = viewModel::onGeminiDraftChange,
                    onSaveAndCheck = viewModel::saveAndCheckGemini,
                    onRemove = viewModel::removeGeminiKey,
                )
            }
        }
    }
}

/** One provider's key section inside the AI keys dialog. */
@Composable
private fun AiKeySection(
    title: String,
    icon: ImageVector,
    description: String,
    noKeyText: String,
    draftKey: String,
    savedTail: String?,
    status: AiKeyStatus,
    providerName: String,
    placeholder: String,
    onDraftChange: (String) -> Unit,
    onSaveAndCheck: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Spacer(Modifier.height(BlurtSpacing.xs))
    Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(BlurtSpacing.s))
    Text(
        text = if (savedTail != null) "Your key is active · ends in …$savedTail" else noKeyText,
        style = MaterialTheme.typography.labelMedium,
        color = if (savedTail != null) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(BlurtSpacing.m))
    OutlinedTextField(
        value = draftKey,
        onValueChange = onDraftChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("$providerName API key") },
        placeholder = { Text(placeholder) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(BlurtRadii.s),
    )

    Spacer(Modifier.height(BlurtSpacing.s))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = onSaveAndCheck,
            enabled = draftKey.isNotBlank() && status !is AiKeyStatus.Checking,
            shape = RoundedCornerShape(BlurtRadii.s),
        ) {
            if (status is AiKeyStatus.Checking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Save & Check")
            }
        }
        if (savedTail != null) {
            Spacer(Modifier.width(BlurtSpacing.m))
            TextButton(onClick = onRemove) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    when (status) {
        is AiKeyStatus.Valid -> StatusLine(
            "Connected — your key is active.",
            MaterialTheme.colorScheme.primary,
        )
        is AiKeyStatus.Invalid -> StatusLine(
            "$providerName rejected that key. Check it and try again.",
            MaterialTheme.colorScheme.error,
        )
        is AiKeyStatus.Unreachable -> StatusLine(
            "Key saved, but $providerName couldn't be reached. It'll work once you're back online.",
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> {}
    }
}

@Composable
private fun StatusLine(text: String, color: androidx.compose.ui.graphics.Color) {
    Spacer(Modifier.height(BlurtSpacing.s))
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}

@Composable
private fun ThemeOptionRow(
    mode: ThemeMode,
    icon: ImageVector,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val selected = mode == current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BlurtSpacing.s))
            .clickable { onSelect(mode) }
            .padding(horizontal = BlurtSpacing.l, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = mode.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun initials(displayName: String?): String {
    val parts = displayName.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "B"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}
