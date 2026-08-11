package com.blurt.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blurt.app.auth.AuthUser
import com.blurt.app.data.model.CaptureType
import com.blurt.app.ui.components.AccountMenu
import com.blurt.app.ui.components.BlurtIcons
import com.blurt.app.ui.components.CaptureListItem
import com.blurt.app.ui.components.EmptyState
import com.blurt.app.ui.components.blurtPressScale
import com.blurt.app.ui.components.rememberBlurtInteractionSource
import com.blurt.app.ui.components.typeIcon
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
    onCapture: (CaptureType) -> Unit,
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
        Spacer(Modifier.height(BlurtSpacing.xl))
        QuickCaptureCard(onCapture = onCapture)
        Spacer(Modifier.height(28.dp))

        if (captures.isEmpty()) {
            Spacer(Modifier.height(40.dp))
            EmptyState(
                icon = BlurtIcons.BlurtMark,
                title = "Nothing here yet",
                body = "Blurt anything — text, ideas, links — and it'll be waiting for you here.",
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Recent",
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
 * The prominent quick capture surface. Tapping it starts a text blurt; the
 * pills jump straight into a specific capture type (Text is the emphasized,
 * active type).
 */
@Composable
private fun QuickCaptureCard(onCapture: (CaptureType) -> Unit) {
    val interactionSource = rememberBlurtInteractionSource()
    Surface(
        onClick = { onCapture(CaptureType.TEXT) },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(BlurtSpacing.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .blurtPressScale(interactionSource),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = BlurtSpacing.xl, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "What's on your mind?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(BlurtSpacing.m))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CaptureType.entries.forEach { type ->
                    QuickTypeChip(
                        type = type,
                        emphasized = type == CaptureType.TEXT,
                        onClick = { onCapture(type) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickTypeChip(
    type: CaptureType,
    emphasized: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = rememberBlurtInteractionSource()
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(BlurtSpacing.s),
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.blurtPressScale(interactionSource),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = typeIcon(type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = type.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (emphasized) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
