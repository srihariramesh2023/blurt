package com.blurt.app.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blurt.app.ui.components.BlurtTopBar
import com.blurt.app.ui.components.blurtPressScale
import com.blurt.app.ui.components.rememberBlurtInteractionSource
import com.blurt.app.ui.theme.BlurtMotion
import com.blurt.app.ui.theme.BlurtSpacing
import com.blurt.app.ui.theme.rememberReduceMotion
import com.blurt.app.util.TimeFormat
import com.blurt.app.util.isHttpUrl
import com.blurt.app.util.normalizedHttpUrl
import com.blurt.app.util.urlDomain

/**
 * The fast capture composer. No type selector — the user just types and Blurt
 * decides (rules for links, AI for categories). When the AI finds a concrete
 * time, a confirm sheet offers a priority reminder before anything is saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CaptureViewModel = viewModel(factory = CaptureViewModel.Factory),
) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    val analyzing by viewModel.analyzing.collectAsStateWithLifecycle()
    val pendingReminder by viewModel.pendingReminder.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()

    LaunchedEffect(saved) {
        saved?.let {
            viewModel.onSavedHandled()
            onSaved()
        }
    }

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.confirmReminder() else viewModel.dismissReminder(notificationsBlocked = true)
    }

    val canSave = content.isNotBlank() && !analyzing && pendingReminder == null
    val saveSource = rememberBlurtInteractionSource()

    // Autofocus the editor once on open; don't fight the user afterward.
    val textFocusRequester = FocusRequester()
    var autofocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autofocused) {
            textFocusRequester.requestFocus()
            autofocused = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = BlurtSpacing.grouped),
    ) {
        BlurtTopBar(title = "New Blurt", onBack = onBack)
        Spacer(Modifier.height(BlurtSpacing.m))

        val normalized = content.normalizedHttpUrl()
        val isLink = content.isNotBlank() && normalized.isHttpUrl()
        TextField(
            value = content,
            onValueChange = viewModel::onContentChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp)
                .focusRequester(textFocusRequester),
            placeholder = { Text("What's on your mind?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            shape = RoundedCornerShape(20.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Spacer(Modifier.height(BlurtSpacing.s))
        // Quiet helper: what Blurt understood so far.
        Text(
            text = when {
                isLink -> "Link blurt · opens ${normalized.urlDomain()}"
                content.isNotBlank() -> "I'll figure out where this belongs."
                else -> "Blurt anything — text, ideas, links."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AnimatedVisibility(
            visible = error != null,
            enter = if (rememberReduceMotion()) {
                fadeIn(androidx.compose.animation.core.tween(BlurtMotion.FADE_MS)) +
                    expandVertically(androidx.compose.animation.core.tween(BlurtMotion.FADE_MS))
            } else {
                fadeIn(BlurtMotion.micro()) + expandVertically(BlurtMotion.micro())
            },
        ) {
            error?.let {
                Spacer(Modifier.height(BlurtSpacing.s))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        AnimatedVisibility(
            visible = notice != null,
            enter = if (rememberReduceMotion()) {
                fadeIn(androidx.compose.animation.core.tween(BlurtMotion.FADE_MS)) +
                    expandVertically(androidx.compose.animation.core.tween(BlurtMotion.FADE_MS))
            } else {
                fadeIn(BlurtMotion.micro()) + expandVertically(BlurtMotion.micro())
            },
        ) {
            notice?.let {
                Spacer(Modifier.height(BlurtSpacing.s))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = viewModel::save,
            enabled = canSave,
            interactionSource = saveSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .blurtPressScale(saveSource),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            if (analyzing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.size(8.dp))
                Text("Analyzing…", style = MaterialTheme.typography.labelLarge)
            } else {
                Text("Save Blurt", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(BlurtSpacing.m))
    }

    // Confirm sheet: the AI found a time — set a reminder or just save.
    pendingReminder?.let { pending ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissReminder() },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = pending.analysis.category.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(BlurtSpacing.m))
                Text(
                    text = "Remind me at ${TimeFormat.full(pending.at)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(BlurtSpacing.s))
                Text(
                    text = "A Blurt notification will pop up when it's time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(BlurtSpacing.l))
                val remindSource = rememberBlurtInteractionSource()
                Button(
                    onClick = {
                        if (needsNotificationPermission(context)) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.confirmReminder()
                        }
                    },
                    interactionSource = remindSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .blurtPressScale(remindSource),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("Remind me", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { viewModel.dismissReminder() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("Just save", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(BlurtSpacing.l))
            }
        }
    }
}

/** Android 13+ needs a runtime grant before any notification can be posted. */
private fun needsNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
