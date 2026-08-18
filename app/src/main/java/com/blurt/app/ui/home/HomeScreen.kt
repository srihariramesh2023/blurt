package com.blurt.app.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blurt.app.auth.AuthUser
import com.blurt.app.ui.components.BlurtIcons
import com.blurt.app.ui.components.BlurtOrb
import com.blurt.app.ui.components.BlurtSound
import com.blurt.app.ui.components.LocalTabBarInset
import com.blurt.app.ui.components.OrbState
import com.blurt.app.ui.components.blurtPressScale
import com.blurt.app.ui.components.rememberBlurtHaptics
import com.blurt.app.ui.components.rememberBlurtInteractionSource
import com.blurt.app.ui.theme.BlurtMotion
import com.blurt.app.ui.theme.BlurtSpacing
import com.blurt.app.ui.theme.rememberReduceMotion
import com.blurt.app.ui.voice.VoiceCaptureFlow
import com.blurt.app.ui.voice.VoicePhase
import com.blurt.app.ui.voice.VoiceViewModel

/**
 * Home — the capture surface. Pressing the orb transitions **in place** into
 * listening: no page push, no second orb — the hero fades into the flowing
 * waveform, live transcript, and stop control. Everything captured lives in
 * Library.
 */
@Composable
fun HomeScreen(
    user: AuthUser,
    onCapture: (String?) -> Unit,
    onSearch: () -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: VoiceViewModel = viewModel(factory = VoiceViewModel.Factory),
) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val needsMicPermission by viewModel.needsMicPermission.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val haptics = rememberBlurtHaptics()
    val reduceMotion = rememberReduceMotion()

    // Mic permission — owned here because the orb (the thing that requests
    // it) lives on Home now.
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

    // Errors buzz + tone, whatever the phase.
    LaunchedEffect(error) {
        if (error != null) {
            haptics.error()
            BlurtSound.playError()
        }
    }

    val idle = phase == VoicePhase.IDLE
    val conversationActive by viewModel.conversationActive.collectAsStateWithLifecycle()
    val turns by viewModel.turns.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = BlurtSpacing.grouped)
            .padding(bottom = LocalTabBarInset.current + BlurtSpacing.m),
    ) {
        Spacer(Modifier.height(BlurtSpacing.m))
        HomeHeader(user = user, onSearch = onSearch, onOpenProfile = onOpenProfile)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (conversationActive) {
                // Conversation mode — smaller orb at top, thread below.
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(BlurtSpacing.s))
                    BlurtOrb(
                        state = if (idle) OrbState.IDLE else OrbState.RECORDING,
                        size = 100.dp,
                        icon = BlurtIcons.BlurtMark,
                        contentDescription = "Blurt",
                    )
                    VoiceCaptureFlow(
                        viewModel = viewModel,
                        onEdit = onCapture,
                        onDone = { viewModel.dismissSaved() },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
            AnimatedContent(
                targetState = idle,
                transitionSpec = {
                    if (reduceMotion) {
                        (fadeIn(tween(BlurtMotion.FADE_MS)) togetherWith fadeOut(tween(BlurtMotion.FADE_MS / 2)))
                    } else if (targetState) {
                        // Back to the resting hero — quick and calm, no lingering.
                        val enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.98f)
                        val exit = fadeOut(tween(120))
                        enter togetherWith exit
                    } else {
                        // The orb press — the hero dims out, listening rises in.
                        val enter = fadeIn(BlurtMotion.standard()) + slideInVertically(BlurtMotion.entrance()) { it / 6 }
                        val exit = fadeOut(BlurtMotion.micro()) + scaleOut(BlurtMotion.micro(), targetScale = 0.97f)
                        enter togetherWith exit
                    }
                },
                label = "homeCapture",
            ) { isIdle ->
                if (isIdle) {
                    MicHero(
                        error = error,
                        onMicTapped = {
                            haptics.tick()
                            BlurtSound.playStart()
                            viewModel.onMicTapped()
                        },
                        onType = { onCapture(null) },
                    )
                } else {
                    VoiceCaptureFlow(
                        viewModel = viewModel,
                        onEdit = onCapture,
                        onDone = { viewModel.dismissSaved() },
                    )
                }
            }
            }
        }
    }
}

/** The V2 home header — a greeting, not a wordmark. */
@Composable
private fun HomeHeader(
    user: AuthUser,
    onSearch: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(
                text = greetingFor(user.displayName),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "What's on your mind today?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearch) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        // The avatar — one tap into Settings.
        val source = rememberBlurtInteractionSource()
        Surface(
            onClick = onOpenProfile,
            interactionSource = source,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .size(44.dp)
                .blurtPressScale(source),
        ) {
            if (!user.photoUrl.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = user.photoUrl,
                    contentDescription = "Account",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
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
    }
}

/** "Good morning / afternoon / evening, <first name>" — calm and personal. */
private fun greetingFor(displayName: String?): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val part = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    val first = displayName?.trim()?.split(Regex("\\s+"))?.firstOrNull().orEmpty()
    return if (first.isBlank()) part else "$part, $first"
}

private fun initials(displayName: String?): String {
    val parts = displayName.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "B"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

/**
 * The resting hero — the glowing violet orb with the burst mark, "Tap the
 * orb and just talk.", a quiet "or" divider, then "Type instead". Typing
 * never competes with the orb. Pressing the orb transitions this whole
 * surface in place into listening.
 */
@Composable
private fun MicHero(
    error: String?,
    onMicTapped: () -> Unit,
    onType: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BlurtOrb(
            state = OrbState.IDLE,
            size = 176.dp,
            icon = BlurtIcons.BlurtMark,
            contentDescription = "Speak a blurt",
            onClick = onMicTapped,
        )
        Spacer(Modifier.height(BlurtSpacing.l))
        Text(
            text = "Tap the orb and just talk.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Blurt listens. Blurt organizes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        error?.let {
            Spacer(Modifier.height(BlurtSpacing.s))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        Spacer(Modifier.height(BlurtSpacing.m))
        // The quiet "or" divider — the board's consistent pattern.
        Row(verticalAlignment = Alignment.CenterVertically) {
            OrLine()
            Spacer(Modifier.width(BlurtSpacing.m))
            Text(
                text = "or",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(BlurtSpacing.m))
            OrLine()
        }
        Spacer(Modifier.height(BlurtSpacing.s))
        val typeSource = rememberBlurtInteractionSource()
        TextButton(
            onClick = onType,
            interactionSource = typeSource,
            modifier = Modifier
                .defaultMinSize(minHeight = 44.dp)
                .blurtPressScale(typeSource),
        ) {
            Icon(
                imageVector = BlurtIcons.Keyboard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Type instead", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A short muted hairline used by the "or" divider. */
@Composable
private fun OrLine() {
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
    )
}
