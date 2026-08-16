package com.blurt.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blurt.app.ui.theme.BlurtRadii
import com.blurt.app.ui.theme.BlurtSpacing

/**
 * The BYOK sheet: one section per provider. A masked field for a free key, a
 * live Save & Check probe, and a Remove action. Keys are stored encrypted in
 * the Android Keystore and take effect on the next analysis — no rebuild
 * needed.
 */
@Composable
fun AiKeyDialog(
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
            shape = RoundedCornerShape(BlurtRadii.xl),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(BlurtSpacing.l),
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

                Spacer(Modifier.height(BlurtSpacing.m))

                AiKeySection(
                    title = "Gemini — semantic search",
                    icon = BlurtIcons.Sparkle,
                    description = "Backup classifier, plus meaning-based search across your library.",
                    noKeyText = "No key — search falls back to keywords",
                    draftKey = geminiDraftKey,
                    savedTail = geminiSavedTail,
                    status = geminiStatus,
                    providerName = "Gemini",
                    placeholder = "AIza… or AQ.…",
                    onDraftChange = viewModel::onGeminiDraftChange,
                    onSaveAndCheck = viewModel::saveAndCheckGemini,
                    onRemove = viewModel::removeGeminiKey,
                )
            }
        }
    }
}

/** One provider's key section — used by the AI keys dialog and the first-run BYOK onboarding page. */
@Composable
internal fun AiKeySection(
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
