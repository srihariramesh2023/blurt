package com.blurt.app.ui.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blurt.app.ai.CaptureAnalysis
import com.blurt.app.data.model.Recurrence
import com.blurt.app.ui.components.BlurtIcons
import com.blurt.app.ui.components.BlurtOrb
import com.blurt.app.ui.components.BlurtSound
import com.blurt.app.ui.components.BlurtToast
import com.blurt.app.ui.components.OrbState
import com.blurt.app.ui.components.blurtPressScale
import com.blurt.app.ui.components.rememberBlurtHaptics
import com.blurt.app.ui.components.rememberBlurtInteractionSource
import com.blurt.app.ui.theme.BlurtMotion
import com.blurt.app.ui.theme.BlurtRadii
import com.blurt.app.ui.theme.BlurtSpacing
import com.blurt.app.ui.theme.rememberReduceMotion
import com.blurt.app.util.TimeFormat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The shared voice capture surface — everything that happens after the user
 * starts talking. Home embeds it in place (the orb morphs into listening,
 * no navigation), and the pushed VoiceScreen embeds it after its idle orb.
 * One component, one voice.
 *
 * The mic permission lives with whoever draws the orb (Home / VoiceScreen);
 * this stays focused on the running capture: listening, organizing, error,
 * review, and saved.
 */
@Composable
fun VoiceCaptureFlow(
    viewModel: VoiceViewModel,
    onEdit: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val transcript by viewModel.transcript.collectAsStateWithLifecycle()
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    val progressive by viewModel.progressive.collectAsStateWithLifecycle()
    val reply by viewModel.reply.collectAsStateWithLifecycle()
    val followUpQuestion by viewModel.followUpQuestion.collectAsStateWithLifecycle()
    val level by viewModel.level.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val savedReminderAt by viewModel.savedReminderAt.collectAsStateWithLifecycle()
    val savedCount by viewModel.savedCount.collectAsStateWithLifecycle()
    val editRequested by viewModel.editRequested.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val haptics = rememberBlurtHaptics()
    val reduceMotion = rememberReduceMotion()

    // Analysis landed → medium pulse.
    LaunchedEffect(phase) { if (phase == VoicePhase.CONFIRM) haptics.pulse() }
    // Saved → success haptic + tone.
    LaunchedEffect(saved) { saved?.let { haptics.success(); BlurtSound.playSave() } }
    // "Edit" → the typed composer, pre-filled.
    LaunchedEffect(editRequested) {
        if (editRequested) {
            viewModel.onEditHandled()
            onEdit(transcript)
        }
    }

    // Notification permission (Android 13+) when a reminder is about to save.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.save() else viewModel.saveWithoutReminder()
    }

    AnimatedContent(
        targetState = phase,
        transitionSpec = {
            if (reduceMotion) {
                (fadeIn(tween(BlurtMotion.FADE_MS)) togetherWith fadeOut(tween(BlurtMotion.FADE_MS / 2)))
            } else {
                val rise = slideInVertically(BlurtMotion.entrance()) { it / 5 }
                (fadeIn(BlurtMotion.standard()) + rise) togetherWith fadeOut(BlurtMotion.micro())
            }
        },
        label = "voiceFlow",
        modifier = modifier,
    ) { current ->
        when (current) {
            VoicePhase.LISTENING -> ListeningState(
                transcript = transcript,
                progressive = progressive,
                level = level,
                onStop = {
                    haptics.doubleTick()
                    BlurtSound.playStop()
                    viewModel.stop()
                },
                onCancel = viewModel::cancel,
            )
            VoicePhase.FOLLOWUP -> ListeningState(
                transcript = transcript,
                progressive = progressive,
                level = level,
                prompt = followUpQuestion,
                onStop = {
                    haptics.doubleTick()
                    BlurtSound.playStop()
                    viewModel.stop()
                },
                onCancel = viewModel::cancel,
            )
            VoicePhase.ANALYZING -> ProcessingState(
                transcript = transcript,
                onDiscard = { viewModel.cancel() },
                onShare = { shareTranscript(context, transcript) },
                onEdit = viewModel::requestEdit,
            )
            VoicePhase.REPLYING -> ReplyingState(
                reply = reply,
                onSkip = viewModel::skipReply,
            )
            VoicePhase.ERROR -> ErrorState(
                transcript = transcript,
                error = error,
                onRetry = viewModel::retry,
                onSaveAsNote = viewModel::saveAsNote,
                onTypeInstead = { viewModel.requestEdit() },
            )
            VoicePhase.CONFIRM -> ReviewState(
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
            VoicePhase.SAVED -> SavedReviewState(
                transcript = transcript,
                analysis = analysis,
                savedReminderAt = savedReminderAt,
                savedCount = savedCount,
                onDone = onDone,
            )
            VoicePhase.IDLE -> Unit // the caller renders its own resting orb
        }
    }
}

/**
 * Listening — the reference look: a quiet "Listening…" line, the live
 * transcript, and a flowing horizontal waveform bar that rides the mic
 * level over time, with the square stop control at the bottom.
 */
@Composable
private fun ListeningState(
    transcript: String,
    progressive: CaptureAnalysis?,
    level: Float,
    prompt: String? = null,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(BlurtSpacing.m))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RecordingDot()
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Listening…",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        prompt?.let {
            Spacer(Modifier.height(BlurtSpacing.s))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        Spacer(Modifier.weight(1f))

        AnimatedVisibility(visible = progressive != null && transcript.isNotBlank()) {
            progressive?.let { UnderstandingChips(analysis = it) }
        }
        Spacer(Modifier.height(BlurtSpacing.m))
        Text(
            text = if (transcript.isBlank()) "…" else transcript,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.weight(1f))

        // The flowing waveform — the reference's horizontal sound bar.
        FlowingWaveform(level = level, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(BlurtSpacing.m))
        Text(
            text = "Tap to stop",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(BlurtSpacing.s))
        // The square stop button in a rounded container — the board's control.
        val stopSource = rememberBlurtInteractionSource()
        Surface(
            onClick = onStop,
            interactionSource = stopSource,
            shape = RoundedCornerShape(BlurtRadii.l),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(64.dp)
                .blurtPressScale(stopSource),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = BlurtIcons.Stop,
                    contentDescription = "Stop recording",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.height(BlurtSpacing.m))
        val cancelSource = rememberBlurtInteractionSource()
        TextButton(onClick = onCancel, interactionSource = cancelSource, modifier = Modifier.blurtPressScale(cancelSource)) {
            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** A tiny pulsing red dot — the classic "we are recording" signal. */
@Composable
private fun RecordingDot() {
    val reduceMotion = rememberReduceMotion()
    val infinite = rememberInfiniteTransition(label = "recDot")
    val a by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "recAlpha",
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error.copy(alpha = if (reduceMotion) 0.8f else a)),
    )
}

/**
 * The flowing waveform — the reference's horizontal sound bar. Flat when the
 * mic is silent, alive the moment sound arrives: the mic level scales a
 * wave that travels across the bar continuously in one direction (backward,
 * like rewinding), looping invisibly so it never stops and never jumps.
 * Taller and brighter toward the center, with a glow that only breathes
 * under the peak while sound is present.
 */
@Composable
private fun FlowingWaveform(level: Float, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val primary = MaterialTheme.colorScheme.primary
    val peak = Color(0xFF9B7BFF) // the brand's bright violet
    val infinite = rememberInfiniteTransition(label = "waveFlow")
    // One full phase advance per loop; because the wave is periodic across
    // the bar count, the restart lands on an identical frame — infinite
    // backward flow with no visible jump.
    val flow by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing), RepeatMode.Restart),
        label = "flow",
    )

    Canvas(modifier = modifier.fillMaxWidth().height(60.dp)) {
        val barCount = 44
        val gap = 3.dp.toPx()
        val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(2f)
        val centerIdx = (barCount - 1) / 2f
        // ~3 full waves across the bar, so the scroll has visible motion.
        val angleStep = 2 * PI * 3 / barCount

        // Soft glow behind the peak — only while sound is present.
        if (level > 0.01f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(peak.copy(alpha = 0.3f * level), Color.Transparent),
                    center = Offset(size.width / 2, size.height / 2),
                    radius = size.width * 0.2f,
                ),
                radius = size.width * 0.2f,
                center = Offset(size.width / 2, size.height / 2),
            )
        }

        repeat(barCount) { i ->
            val dist = abs(i - centerIdx) / centerIdx // 0 center, 1 edge
            // Center-taper with a touch of organic per-bar variation.
            val shape = (0.35f + 0.65f * (1f - dist)) * (0.6f + 0.4f * (0.5f + 0.5f * sin(i * 0.9).toFloat()))
            // The wave travels backward (crest moves left) as flow advances;
            // reduce motion freezes the shape, level still drives height.
            val wave = if (reduceMotion) 0f else sin(2 * PI * flow + i * angleStep).toFloat()
            // Flat baseline in silence; sound scales the flowing wave up.
            val heightFrac = (BASE_FRAC + level * shape * (0.5f + 0.5f * wave)).coerceIn(BASE_FRAC, 1f)
            val h = size.height * heightFrac
            val x = i * (barWidth + gap)
            val y = (size.height - h) / 2f
            val fade = (1f - dist).coerceIn(0f, 1f)
            val color = if (fade > 0.85f) peak else lerp(primary.copy(alpha = 0.22f), primary, fade)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
            )
        }
    }
}

/** The quiet flat line the waveform rests on when the mic is silent. */
private const val BASE_FRAC = 0.07f

/**
 * Processing — the board's screen 07: "Thinking it through…", the orb with
 * a swirl ring, and the checklist (Transcribing ✓ Understanding ✓ Organizing).
 */
@Composable
private fun ProcessingState(
    transcript: String,
    onDiscard: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(BlurtSpacing.l))
        Text(
            text = "Thinking it through…",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(BlurtSpacing.m))

        Box(contentAlignment = Alignment.Center) {
            com.blurt.app.ui.components.OrbProcessingRing(size = 200.dp)
            com.blurt.app.ui.components.BlurtOrb(
                state = com.blurt.app.ui.components.OrbState.PROCESSING,
                size = 168.dp,
                icon = BlurtIcons.Sparkle,
                contentDescription = "Processing",
            )
        }

        Spacer(Modifier.height(BlurtSpacing.xl))
        Text(
            text = transcript,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = BlurtSpacing.m),
        )
        Spacer(Modifier.height(BlurtSpacing.xl))

        // The board's checklist.
        ProcessingCheckRow(label = "Transcribing", done = true)
        Spacer(Modifier.height(BlurtSpacing.m))
        ProcessingCheckRow(label = "Understanding", done = true)
        Spacer(Modifier.height(BlurtSpacing.m))
        ProcessingCheckRow(label = "Organizing", done = false)

        Spacer(Modifier.weight(1f))
        // The quiet toolbar: discard / share / edit.
        Row(horizontalArrangement = Arrangement.spacedBy(BlurtSpacing.l)) {
            UnderstandingToolbarButton(BlurtIcons.Trash, "Discard", onDiscard)
            UnderstandingToolbarButton(BlurtIcons.Share, "Share", onShare)
            UnderstandingToolbarButton(BlurtIcons.Keyboard, "Edit", onEdit)
        }
        Spacer(Modifier.height(BlurtSpacing.l))
    }
}

@Composable
private fun ProcessingCheckRow(label: String, done: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BlurtSpacing.xl),
    ) {
        if (done) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = BlurtIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(13.dp),
                )
            }
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.5.dp,
            )
        }
        Spacer(Modifier.width(BlurtSpacing.m))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (done) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = if (done) "Done" else "Working…",
            style = MaterialTheme.typography.labelMedium,
            color = if (done) com.blurt.app.ui.theme.successColor()
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UnderstandingToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val source = rememberBlurtInteractionSource()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
            .clip(RoundedCornerShape(BlurtRadii.s))
            .clickable(onClick = onClick, interactionSource = source, indication = null)
            .blurtPressScale(source),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The companion speaking back — the reply as a quiet bubble under the
 * complete orb. No buttons: the utterance drives the transition (auto-save
 * or drop), and a tap skips ahead. The reply itself says what happened, so
 * the screen stays quiet.
 */
@Composable
private fun ReplyingState(
    reply: String?,
    onSkip: () -> Unit,
) {
    val source = rememberBlurtInteractionSource()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onSkip, interactionSource = source, indication = null)
            .padding(horizontal = BlurtSpacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BlurtOrb(
            state = OrbState.COMPLETE,
            size = 120.dp,
            icon = BlurtIcons.Sparkle,
            contentDescription = "Blurt's reply",
        )
        Spacer(Modifier.height(BlurtSpacing.xl))
        Surface(
            shape = RoundedCornerShape(BlurtRadii.xl),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = reply ?: "…",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = BlurtSpacing.l, vertical = BlurtSpacing.l),
            )
        }
        Spacer(Modifier.height(BlurtSpacing.xl))
        Text(
            text = "Tap to continue",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The board's error state (screen 12): the orb with an exclamation,
 * "Couldn't organize that", and Try Again / Save as Note / Type instead.
 */
@Composable
private fun ErrorState(
    transcript: String,
    error: String?,
    onRetry: () -> Unit,
    onSaveAsNote: () -> Unit,
    onTypeInstead: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            com.blurt.app.ui.components.BlurtOrb(
                state = com.blurt.app.ui.components.OrbState.IDLE,
                size = 140.dp,
                icon = null,
                contentDescription = null,
            )
            Text(
                text = "!",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(BlurtSpacing.m))
        Text(
            text = "Couldn't organize that",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(BlurtSpacing.s))
        Text(
            text = "Your recording is safe, but we couldn't turn it into structured information.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = BlurtSpacing.l),
        )
        error?.let {
            Spacer(Modifier.height(BlurtSpacing.m))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(BlurtSpacing.xl))
        val retrySource = rememberBlurtInteractionSource()
        Button(
            onClick = onRetry,
            interactionSource = retrySource,
            modifier = Modifier
                .height(52.dp)
                .blurtPressScale(retrySource),
            shape = RoundedCornerShape(BlurtRadii.l),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text("Try Again", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(BlurtSpacing.s))
        val noteSource = rememberBlurtInteractionSource()
        Button(
            onClick = onSaveAsNote,
            interactionSource = noteSource,
            modifier = Modifier
                .height(52.dp)
                .blurtPressScale(noteSource),
            shape = RoundedCornerShape(BlurtRadii.l),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Text("Save as Note", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(BlurtSpacing.s))
        val typeSource = rememberBlurtInteractionSource()
        TextButton(
            onClick = onTypeInstead,
            interactionSource = typeSource,
            modifier = Modifier.blurtPressScale(typeSource),
        ) {
            Text("Type instead", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(BlurtSpacing.l))
    }
}

/**
 * The review moment — a full screen, not a sheet: the understood blurt in
 * large type, the reminder pill and date, and a single blue Save bar with a
 * quiet Edit beside it. No forms — the AI decided.
 */
@Composable
private fun ReviewState(
    transcript: String,
    analysis: CaptureAnalysis?,
    error: String?,
    notice: String?,
    onSave: () -> Unit,
    onSaveWithoutReminder: () -> Unit,
    onEdit: () -> Unit,
) {
    val hasReminder = analysis?.reminderAt?.let { it > System.currentTimeMillis() } == true

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = BlurtSpacing.s),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (hasReminder) {
                Surface(
                    shape = RoundedCornerShape(BlurtRadii.pill),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "REMINDER",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp),
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(BlurtSpacing.m))
            }
            Text(
                text = transcript,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                // A long capture must stay inside its space — the review's
                // save bar never gets pushed off or overridden.
                maxLines = 5,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(BlurtSpacing.m))
            if (hasReminder && analysis?.reminderAt != null) {
                val recurrence = TimeFormat.recurrenceLabel(analysis.recurrence, analysis.reminderAt)
                Text(
                    text = if (recurrence != null) {
                        "$recurrence · ${TimeFormat.inDuration(analysis.reminderAt)}"
                    } else {
                        "Reminder · ${TimeFormat.inDuration(analysis.reminderAt)}"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = TimeFormat.todayLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                analysis?.let { UnderstandingChips(analysis = it, large = true) }
            }
            Spacer(Modifier.height(BlurtSpacing.m))
            if (!hasReminder && analysis?.reminderAt != null) {
                Text(
                    text = "That time has already passed — saved as a note.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
            }
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
            }
            notice?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        Spacer(Modifier.height(BlurtSpacing.l))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val editSource = rememberBlurtInteractionSource()
            TextButton(
                onClick = onEdit,
                interactionSource = editSource,
                modifier = Modifier.blurtPressScale(editSource),
            ) {
                Text("Edit", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(BlurtSpacing.s))
            val saveSource = rememberBlurtInteractionSource()
            Button(
                onClick = onSave,
                interactionSource = saveSource,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .blurtPressScale(saveSource),
                shape = RoundedCornerShape(BlurtRadii.l),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("Save Blurt", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (hasReminder) {
            Spacer(Modifier.height(4.dp))
            val quietSource = rememberBlurtInteractionSource()
            TextButton(
                onClick = onSaveWithoutReminder,
                interactionSource = quietSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp)
                    .blurtPressScale(quietSource),
            ) {
                Text("Save without reminder", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(BlurtSpacing.m))
    }
}

/**
 * The saved moment — a clean confirmation, not the review again: the saved
 * blurt and its chips, a green toast that says what actually happened, then
 * a quick return. Nothing lingers to second-guess.
 */
@Composable
private fun SavedReviewState(
    transcript: String,
    analysis: CaptureAnalysis?,
    savedReminderAt: Long?,
    savedCount: Int,
    onDone: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = BlurtSpacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = transcript,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 5,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            // Chips reflect what was really saved (no ghost reminder).
            analysis?.let {
                Spacer(Modifier.height(BlurtSpacing.m))
                UnderstandingChips(analysis = it.copy(reminderAt = savedReminderAt), large = true)
            }
        }
        val reminderText = savedReminderAt
            ?.takeIf { it > System.currentTimeMillis() }
            ?.let { ", you'll be reminded ${TimeFormat.inDuration(it)}" }
            .orEmpty()
        val message = if (savedCount > 1) {
            "$savedCount blurts saved$reminderText."
        } else {
            "Blurt saved. $transcript$reminderText."
        }
        BlurtToast(
            message = message,
            onDismissed = onDone,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Quiet chips: intent · category · reminder timing (incl. recurrence). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnderstandingChips(analysis: CaptureAnalysis, large: Boolean = false) {
    val chips = buildList {
        add(analysis.intent.label)
        add(analysis.category.label)
        analysis.reminderAt?.takeIf { it > System.currentTimeMillis() }?.let {
            add("Reminder · ${TimeFormat.inDuration(it)}")
            TimeFormat.recurrenceLabel(analysis.recurrence, analysis.reminderAt)?.let { label -> add(label) }
        }
        if (analysis.important) add("Important")
    }
    // FlowRow so a stack of chips (intent · category · reminder · recurrence
    // · important) wraps instead of running off the edge of the screen.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
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

/** Hand the transcript to another app (Messages, Notes, wherever). */
private fun shareTranscript(context: Context, text: String) {
    if (text.isBlank()) return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(Intent.createChooser(send, "Share blurt"))
    }
}

/** Android 13+ needs a runtime grant before any notification can be posted. */
private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
