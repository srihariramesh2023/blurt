package com.blurt.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blurt.app.auth.AuthUser
import com.blurt.app.ui.components.AccountMenu
import com.blurt.app.ui.components.BlurtIcons
import com.blurt.app.ui.components.CaptureListItem
import com.blurt.app.ui.components.EmptyState
import com.blurt.app.ui.components.blurtPressScale
import com.blurt.app.ui.components.rememberBlurtInteractionSource
import com.blurt.app.ui.theme.BlurtDuration
import com.blurt.app.ui.theme.BlurtSpacing
import com.blurt.app.ui.theme.ThemeMode
import com.blurt.app.util.TimeFormat

/**
 * Home: the Blurt wordmark with a date line, the quick capture surface, and
 * recent captures that stagger in with a gentle rise.
 */
@Composable
fun HomeScreen(
    user: AuthUser,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onSignOut: () -> Unit,
    onVoice: () -> Unit,
    onCapture: () -> Unit,
    onOpenCapture: (Long) -> Unit,
    onOpenLibrary: () -> Unit,
    onSearch: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val captures by viewModel.recent.collectAsStateWithLifecycle()
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = BlurtSpacing.xl),
    ) {
        Spacer(Modifier.height(BlurtSpacing.m))
        HomeHeader(user = user, themeMode = themeMode, onThemeChange = onThemeChange, onSignOut = onSignOut, onSearch = onSearch)
        MicHero(onVoice = onVoice, onType = onCapture)
        Spacer(Modifier.height(20.dp))

        if (captures.isEmpty()) {
            Spacer(Modifier.height(20.dp))
            EmptyState(
                icon = BlurtIcons.BlurtMark,
                title = "Nothing here yet",
                body = "Blurt anything — text, ideas, links — and it'll be waiting for you here.",
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Recent Blurts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpenLibrary) {
                    Text(
                        text = "See all",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(captures, key = { _, capture -> capture.id }) { index, capture ->
                    AnimatedVisibility(
                        visible = entered,
                        enter = fadeIn(tween(BlurtDuration.medium, delayMillis = index * 55)) +
                            slideInVertically(tween(BlurtDuration.medium, delayMillis = index * 55)) { it / 4 },
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

@Composable
private fun HomeHeader(
    user: AuthUser,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onSignOut: () -> Unit,
    onSearch: () -> Unit,
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
        AccountMenu(
            user = user,
            themeMode = themeMode,
            onThemeChange = onThemeChange,
            onSignOut = onSignOut,
        )
    }
}

/**
 * The V2 home hero: a large mic and "What's on your mind?". Typing stays as
 * the quiet secondary path — the mic must never compete with anything.
 */
@Composable
private fun MicHero(onVoice: () -> Unit, onType: () -> Unit) {
    val interactionSource = rememberBlurtInteractionSource()
    val infinite = rememberInfiniteTransition(label = "homeMic")
    val ringAlpha by infinite.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(tween(2_200, easing = LinearEasing), RepeatMode.Reverse),
        label = "homeRing",
    )
    val ringScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(2_200, easing = LinearEasing), RepeatMode.Reverse),
        label = "homeRingScale",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(168.dp)
                        .scale(ringScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = ringAlpha)),
                )
                Surface(
                    onClick = onVoice,
                    interactionSource = interactionSource,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(124.dp)
                        .blurtPressScale(interactionSource),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = BlurtIcons.Mic,
                            contentDescription = "Speak a blurt",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            Text(
                text = "What's on your mind?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tap the mic and say it — Blurt figures out the rest.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            val typeSource = rememberBlurtInteractionSource()
            TextButton(onClick = onType, interactionSource = typeSource, modifier = Modifier.blurtPressScale(typeSource)) {
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
