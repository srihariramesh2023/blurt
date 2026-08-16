package com.blurt.app.ui.voice

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blurt.app.ui.components.BlurtIcons
import com.blurt.app.ui.components.BlurtOrb
import com.blurt.app.ui.components.BlurtSound
import com.blurt.app.ui.components.BlurtTopBar
import com.blurt.app.ui.components.OrbState
import com.blurt.app.ui.components.blurtPressScale
import com.blurt.app.ui.components.rememberBlurtHaptics
import com.blurt.app.ui.components.rememberBlurtInteractionSource
import com.blurt.app.ui.theme.BlurtMotion
import com.blurt.app.ui.theme.BlurtSpacing
import com.blurt.app.ui.theme.rememberReduceMotion

/**
 * The pushed capture page (Library's "Blurt something"): its own resting
 * orb, then the same in-place capture flow Home uses — one component, one
 * voice. Home's orb no longer navigates here; it captures in place.
 */
@Composable
fun VoiceScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onSaved: () -> Unit,
    viewModel: VoiceViewModel = viewModel(factory = VoiceViewModel.Factory),
) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val needsMicPermission by viewModel.needsMicPermission.collectAsStateWithLifecycle()

    val haptics = rememberBlurtHaptics()
    val reduceMotion = rememberReduceMotion()

    val onMicTapped = {
        haptics.tick()
        BlurtSound.playStart()
        viewModel.onMicTapped()
    }

    // Mic permission — owned here because the idle orb lives here.
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = BlurtSpacing.grouped),
    ) {
        BlurtTopBar(
            title = "",
            onBack = {
                if (phase == VoicePhase.LISTENING || phase == VoicePhase.ANALYZING ||
                    phase == VoicePhase.REPLYING || phase == VoicePhase.FOLLOWUP
                ) {
                    viewModel.cancel()
                }
                onBack()
            },
        )
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                if (reduceMotion) {
                    (fadeIn(tween(BlurtMotion.FADE_MS)) togetherWith fadeOut(tween(BlurtMotion.FADE_MS / 2)))
                } else {
                    val rise = slideInVertically(BlurtMotion.entrance()) { it / 4 }
                    (fadeIn(BlurtMotion.standard()) + rise) togetherWith fadeOut(BlurtMotion.micro())
                }
            },
            label = "voicePhase",
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = BlurtSpacing.l),
        ) { current ->
            when (current) {
                VoicePhase.IDLE -> IdleState(
                    onMicTapped = onMicTapped,
                    onType = { onEdit("") },
                    error = error,
                )
                else -> VoiceCaptureFlow(
                    viewModel = viewModel,
                    onEdit = onEdit,
                    onDone = onSaved,
                )
            }
        }
    }
}

/** The resting state: the orb, nothing else to think about. */
@Composable
private fun IdleState(onMicTapped: () -> Unit, onType: () -> Unit, error: String?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "What's on your mind?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(BlurtSpacing.s))
        Text(
            text = "Tap the orb and just talk.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        error?.let {
            Spacer(Modifier.height(BlurtSpacing.m))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        Spacer(Modifier.height(BlurtSpacing.xl))
        BlurtOrb(
            state = OrbState.IDLE,
            size = 168.dp,
            icon = BlurtIcons.BlurtMark,
            contentDescription = "Speak a blurt",
            onClick = onMicTapped,
        )
        Spacer(Modifier.height(BlurtSpacing.xl))
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
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Type instead", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
