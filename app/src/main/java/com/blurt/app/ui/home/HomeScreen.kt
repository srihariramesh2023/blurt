package com.blurt.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.blurt.app.auth.AuthUser
import com.blurt.app.ui.components.BlurtIcons
import com.blurt.app.ui.components.BlurtOrb
import com.blurt.app.ui.components.LocalBlurtScrollState
import com.blurt.app.ui.components.LocalTabBarInset
import com.blurt.app.ui.components.OrbState
import com.blurt.app.ui.components.blurtPressScale
import com.blurt.app.ui.components.rememberBlurtHaptics
import com.blurt.app.ui.components.rememberBlurtInteractionSource
import com.blurt.app.ui.theme.BlurtSpacing

/**
 * Home — the board's capture surface (screen 05): a greeting, the glowing
 * orb, "or", and Type instead. Nothing competes with the orb; everything
 * the user captures lives in Library.
 */
@Composable
fun HomeScreen(
    user: AuthUser,
    onVoice: () -> Unit,
    onCapture: () -> Unit,
    onSearch: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    // Scrollable so the hero never collides with the floating tab bar on
    // short screens — the bottom inset clears the glass. The scroll state is
    // the shell's (LocalBlurtScrollState) so the frosted bar copy tracks it.
    val scrollState = LocalBlurtScrollState.current ?: rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = BlurtSpacing.grouped)
            .verticalScroll(scrollState)
            .padding(bottom = LocalTabBarInset.current + BlurtSpacing.m),
    ) {
        Spacer(Modifier.height(BlurtSpacing.m))
        HomeHeader(user = user, onSearch = onSearch, onOpenProfile = onOpenProfile)
        MicHero(onVoice = onVoice, onType = onCapture)
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
 * The V2 home hero — the orb (board screen 05): a glowing violet orb with a
 * mic, "Tap the orb and just talk. / Blurt listens. Blurt organizes.", a
 * quiet "or" divider, then "Type instead". Typing never competes with it.
 */
@Composable
private fun MicHero(onVoice: () -> Unit, onType: () -> Unit) {
    val haptics = rememberBlurtHaptics()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BlurtOrb(
                state = OrbState.IDLE,
                size = 176.dp,
                icon = BlurtIcons.Mic,
                contentDescription = "Speak a blurt",
                onClick = {
                    haptics.tick()
                    onVoice()
                },
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
