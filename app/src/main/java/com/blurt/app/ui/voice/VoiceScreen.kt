package com.blurt.app.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blurt.app.ai.CaptureAnalysis
import com.blurt.app.ui.components.BlurtIcons
import com.blurt.app.ui.components.BlurtTopBar
import com.blurt.app.ui.components.blurtPressScale
import com.blurt.app.ui.components.rememberBlurtInteractionSource
import com.blurt.app.util.TimeFormat
import kotlin.math.sin

/**
 * The V2 capture surface: speak, and Blurt figures out what it means.
 *
 * 1. IDLE — a large mic and \"What's on your mind?\"; typing is the quiet
 *    secondary path.
 * 2. LISTENING — the mic reacts to the voice, the transcript grows live, and
 *    progressive \"looks like…\" chips appear while the user is still talking.
 * 3. ANALYZING — a brief reading pause.
 * 4. CONFIRM — the understood blurt with Save Blurt / Edit. A detected time
 *    is shown inline; saving schedules the priority notification.
 * 5. SAVED — a brief checkmark, then back to Home.
 */
@Composable
fun VoiceScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onSaved: () -> Unit,
    viewModel: VoiceViewModel = viewModel(factory = VoiceViewModel.Factory),
) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val transcript by viewModel.transcript.collectAsStateWithLifecycle()
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    val progressive by viewModel.progressive.collectAsStateWithLifecycle()
    val level by viewModel.level.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val needsMicPermission by viewModel.needsMicPermission.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val editRequested by viewModel.editRequested.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // The mic permission gate — the launcher must be registered before any
    // state can request it.
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onMicPermissionResult(granted)
    }
    LaunchedEffect(needsMicPermission) {
        if (needsMicPermission) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Notification permission (Android 13+) when a reminder is about to save.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.save() else {
            viewModel.saveWithoutReminder()
        }
    }

    LaunchedEffect(saved) {
        saved?.let {
            viewModel.onSavedHandled()
            onSaved()
        }
    }

    LaunchedEffect(editRequested) {
        if (editRequested) {
            viewModel.onEditHandled()
            onEdit(transcript)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        BlurtTopBar(
            title = "",
            onBack = {
                if (phase == VoicePhase.LISTENING || phase == VoicePhase.ANALYZING) viewModel.cancel()
                onBack()
            },
        )
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(160))
            },
            label = "voicePhase",
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 28.dp),
        ) { current ->
            when (current) {
                VoicePhase.IDLE -> IdleState(
                    onMicTapped = viewModel::onMicTapped,
                    onType = { onEdit("") },
                    error = error,
                )
                VoicePhase.LISTENING -> ListeningState(
                    transcript = transcript,
                    progressive = progressive,
                    level = level,
                    onStop = viewModel::stop,
                    onCancel = viewModel::cancel,
                )
                VoicePhase.ANALYZING -> AnalyzingState(transcript = transcript)
                VoicePhase.CONFIRM -> ConfirmState(
                    transcript = transcript,
                    analysis = analysis,
                    error = error,
                    notice = notice,
                    onSave = {
                        val hasReminder = analysis?.reminderAt?.let { it > System.currentTimeMillis() } == true
                        if (hasReminder && needsNotificationPermission(context)) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.save()
                        }
                    },
                    onSaveWithoutReminder = viewModel::saveWithoutReminder,
                    onEdit = viewModel::requestEdit,
                )
                VoicePhase.SAVED -> SavedState()
            }
        }
    }
}

/** The resting state: a large gold mic, nothing else to think about. */
@Composable
private fun IdleState(onMicTapped: () -> Unit, onType: () -> Unit, error: String?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "What's on your mind?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Tap the mic and just say it — Blurt figures out the rest.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        error?.let {
            Spacer(Modifier.height(14.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        Spacer(Modifier.height(44.dp))
        VoiceButton(
            listening = false,
            level = 0f,
            onClick = onMicTapped,
        )
        Spacer(Modifier.height(40.dp))
        val typeSource = rememberBlurtInteractionSource()
        TextButton(onClick = onType, interactionSource = typeSource, modifier = Modifier.blurtPressScale(typeSource)) {
            Icon(
                imageVector = BlurtIcons.Keyboard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Type instead", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Mic live: level-reactive bars, the transcript growing in real time. */
@Composable
private fun ListeningState(
    transcript: String,
    progressive: CaptureAnalysis?,
    level: Float,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Listening",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Say it — I'm right here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        VoiceButton(listening = true, level = level, onClick = onStop)
        Spacer(Modifier.height(30.dp))
        VoiceLevelBars(level = level)

        Spacer(Modifier.height(30.dp))
        AnimatedVisibility(visible = progressive != null && transcript.isNotBlank()) {
            progressive?.let { UnderstandingChips(analysis = it) }
        }
        Spacer(Modifier.height(14.dp))

        Text(
            text = if (transcript.isBlank()) "…" else transcript,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.weight(1f))

        Row {
            val cancelSource = rememberBlurtInteractionSource()
            TextButton(onClick = onCancel, interactionSource = cancelSource, modifier = Modifier.blurtPressScale(cancelSource)) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(18.dp))
            val stopSource = rememberBlurtInteractionSource()
            Button(
                onClick = onStop,
                interactionSource = stopSource,
                modifier = Modifier.blurtPressScale(stopSource),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = BlurtIcons.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Done", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Brief pause while the AI reads the transcript. */
@Composable
private fun AnalyzingState(transcript: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(22.dp))
        Text(
            text = "Understanding…",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = transcript,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/** \"Message ChatGPT — Reminder · in 4 minutes — Save Blurt / Edit\". */
@Composable
private fun ConfirmState(
    transcript: String,
    analysis: CaptureAnalysis?,
    error: String?,
    notice: String?,
    onSave: () -> Unit,
    onSaveWithoutReminder: () -> Unit,
    onEdit: () -> Unit,
) {
    val hasReminder = analysis?.reminderAt?.let { it > System.currentTimeMillis() } == true

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(36.dp))
        Text(
            text = transcript,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Spacer(Modifier.height(20.dp))
        analysis?.let { UnderstandingChips(analysis = it, large = true) }
        if (!hasReminder && analysis?.reminderAt != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "That time has already passed — saved as a note.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        notice?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.weight(1f))

        val saveSource = rememberBlurtInteractionSource()
        Button(
            onClick = onSave,
            interactionSource = saveSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .blurtPressScale(saveSource),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text("Save Blurt", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val editSource = rememberBlurtInteractionSource()
            TextButton(onClick = onEdit, interactionSource = editSource, modifier = Modifier.blurtPressScale(editSource)) {
                Text("Edit", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (hasReminder) {
                Spacer(Modifier.width(10.dp))
                Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                val quietSource = rememberBlurtInteractionSource()
                TextButton(
                    onClick = onSaveWithoutReminder,
                    interactionSource = quietSource,
                    modifier = Modifier.blurtPressScale(quietSource),
                ) {
                    Text("Save without reminder", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** A brief gold check — then the screen pops itself. */
@Composable
private fun SavedState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }
        val scale by animateFloatAsState(if (visible) 1f else 0.6f, tween(320))
        Box(
            modifier = Modifier
                .size(88.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = BlurtIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Saved",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** The mic itself — calm at rest, breathing ring + level ring while live. */
@Composable
private fun VoiceButton(listening: Boolean, level: Float, onClick: () -> Unit) {
    val interactionSource = rememberBlurtInteractionSource()
    val infinite = rememberInfiniteTransition(label = "micPulse")
    val ringAlpha by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = LinearEasing), RepeatMode.Reverse),
        label = "ringAlpha",
    )
    val ringScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = LinearEasing), RepeatMode.Reverse),
        label = "ringScale",
    )

    Box(contentAlignment = Alignment.Center) {
        if (listening) {
            Box(
                modifier = Modifier
                    .size(148.dp)
                    .scale(ringScale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = ringAlpha)),
            )
        }
        val pressScale by animateFloatAsState(
            targetValue = if (listening) 1f + level * 0.04f else 1f,
            animationSpec = tween(120),
            label = "micPress",
        )
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(112.dp)
                .scale(pressScale)
                .blurtPressScale(interactionSource),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (listening) BlurtIcons.Stop else BlurtIcons.Mic,
                    contentDescription = if (listening) "Stop listening" else "Start listening",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
    }
}

/** Seven thin bars that ride the input level — alive, never flashy. */
@Composable
private fun VoiceLevelBars(level: Float) {
    val infinite = rememberInfiniteTransition(label = "levelIdle")
    val idle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing), RepeatMode.Reverse),
        label = "idle",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(7) { index ->
            val phase = sin(index * 1.1).toFloat()
            val raw = if (level > 0.02f) level else 0.18f + 0.12f * idle
            val height by animateFloatAsState(
                targetValue = 8.dp.value * (0.5f + raw * (0.9f + phase * 0.3f)),
                animationSpec = tween(90),
                label = "bar$index",
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index % 2 == 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    ),
            )
        }
    }
}

/** Quiet chips: intent · category · reminder timing. */
@Composable
private fun UnderstandingChips(analysis: CaptureAnalysis, large: Boolean = false) {
    val chips = buildList {
        add(analysis.intent.label)
        add(analysis.category.label)
        analysis.reminderAt?.takeIf { it > System.currentTimeMillis() }?.let {
            add("Reminder · ${TimeFormat.inDuration(it)}")
        }
        if (analysis.important) add("Important")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { chip ->
            Surface(
                shape = RoundedCornerShape(if (large) 12.dp else 10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            ) {
                Text(
                    text = chip,
                    style = if (large) MaterialTheme.typography.labelLarge
                    else MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = if (large) 14.dp else 10.dp, vertical = if (large) 8.dp else 5.dp),
                )
            }
        }
    }
}

/** Android 13+ needs a runtime grant before any notification can be posted. */
private fun needsNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
