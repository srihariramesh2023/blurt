package com.blurt.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.blurt.app.ui.components.CaptureListItem
import com.blurt.app.ui.components.EmptyState
import com.blurt.app.ui.components.LocalBlurtListState
import com.blurt.app.ui.components.blurtPressScale
import com.blurt.app.ui.components.rememberBlurtHaptics
import com.blurt.app.ui.components.rememberBlurtInteractionSource
import com.blurt.app.ui.theme.BlurtMotion
import com.blurt.app.ui.theme.BlurtSpacing
import com.blurt.app.ui.theme.rememberReduceMotion
import com.blurt.app.util.TimeFormat

/**
 * Home: the Blurt wordmark with a date line, the quick capture surface, and
 * recent captures that stagger in with a gentle rise.
 */
@Composable
fun HomeScreen(
    user: AuthUser,
    onVoice: () -> Unit,
    onCapture: () -> Unit,
    onOpenCapture: (Long) -> Unit,
    onOpenLibrary: () -> Unit,
    onSearch: () -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val captures by viewModel.recent.collectAsStateWithLifecycle()
    var entered by remember { mutableStateOf(false) }
    val reduceMotion = rememberReduceMotion()
    LaunchedEffect(Unit) { entered = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = BlurtSpacing.grouped),
    ) {
        Spacer(Modifier.height(BlurtSpacing.m))
        HomeHeader(user = user, onSearch = onSearch, onOpenProfile = onOpenProfile)
        MicHero(onVoice = onVoice, onType = onCapture)
        Spacer(Modifier.height(BlurtSpacing.m))

        if (captures.isEmpty()) {
            Spacer(Modifier.height(BlurtSpacing.m))
            EmptyState(
                icon = BlurtIcons.BlurtMark,
                title = "Nothing here yet",
                body = "Blurt anything — text, ideas, links — and it'll be waiting for you here.",
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "RECENT BLURTS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp),
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpenLibrary) {
                    Text(
                        text = "See all",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(BlurtSpacing.s))
            // The iOS grouped card — one rounded surface holding every recent
            // blurt, clipped so only the first row's corners show.
            Surface(
                shape = RoundedCornerShape(com.blurt.app.ui.theme.BlurtRadii.m),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                LazyColumn(
                    state = LocalBlurtListState.current ?: rememberLazyListState(),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    items(captures, key = { it.id }) { capture ->
                        AnimatedVisibility(
                            visible = entered,
                            enter = if (reduceMotion) {
                                fadeIn(tween(BlurtMotion.FADE_MS))
                            } else {
                                fadeIn(BlurtMotion.standard()) +
                                    slideInVertically(BlurtMotion.standard()) { it / 4 }
                            },
                            modifier = Modifier.animateItem(),
                        ) {
                            CaptureListItem(
                                capture = capture,
                                onClick = { onOpenCapture(capture.id) },
                                onDelete = viewModel::delete,
                                onArchive = { id, _ -> viewModel.archive(id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    user: AuthUser,
    onSearch: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(
                text = "Blurt",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = TimeFormat.todayLabel(),
                style = MaterialTheme.typography.bodySmall,
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
        // The avatar — one tap into the account page (Appearance, AI keys,
        // settings, sign out).
        val source = rememberBlurtInteractionSource()
        Surface(
            onClick = onOpenProfile,
            interactionSource = source,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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

private fun initials(displayName: String?): String {
    val parts = displayName.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "B"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

/**
 * The V2 home hero: a large mic and "What's on your mind?". Typing stays as
 * the quiet secondary path — the mic must never compete with anything.
 */
@Composable
private fun MicHero(onVoice: () -> Unit, onType: () -> Unit) {
    val interactionSource = rememberBlurtInteractionSource()
    val haptics = rememberBlurtHaptics()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                // A soft static halo in the accent — the iOS "record" cue,
                // resting (no looping animation; motion only ever answers a
                // state change, per the design standard).
                Box(
                    modifier = Modifier
                        .size(168.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                )
                // A solid system-blue circle with a white mic — the primary
                // action of the whole product, like Voice Memos' record.
                Surface(
                    onClick = {
                        haptics.tick()
                        onVoice()
                    },
                    interactionSource = interactionSource,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(112.dp)
                        .blurtPressScale(interactionSource),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = BlurtIcons.Mic,
                            contentDescription = "Speak a blurt",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(BlurtSpacing.l))
            Text(
                text = "What's on your mind?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(BlurtSpacing.s))
            Text(
                text = "Tap the mic and say it — Blurt figures out the rest.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
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
