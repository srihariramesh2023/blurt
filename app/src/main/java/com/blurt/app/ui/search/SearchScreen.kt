package com.blurt.app.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blurt.app.auth.AuthUser
import com.blurt.app.data.model.Capture
import com.blurt.app.ui.components.BlurtIcons
import com.blurt.app.ui.components.CaptureListItem
import com.blurt.app.ui.components.EmptyState
import com.blurt.app.ui.components.LocalBlurtListState
import com.blurt.app.ui.components.LocalTabBarInset
import com.blurt.app.ui.components.blurtPressScale
import com.blurt.app.ui.components.rememberBlurtInteractionSource
import com.blurt.app.ui.theme.BlurtRadii
import com.blurt.app.ui.theme.BlurtSpacing
import com.blurt.app.ui.theme.fieldFill
import com.blurt.app.ui.theme.rememberReduceMotion

/**
 * Search — the board's screen 10: a greeting, the recessed search field,
 * filter pills, and Top results as board cards. Meaning-based when a Gemini
 * key is present; keyword otherwise.
 */
@Composable
fun SearchScreen(
    user: AuthUser,
    onOpenCapture: (Long) -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val semanticUsed by viewModel.semanticUsed.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(SearchFilter.ALL) }
    val filtered = results.filter { capture ->
        when (filter) {
            SearchFilter.ALL -> true
            SearchFilter.TASKS -> capture.intent == com.blurt.app.data.model.CaptureIntent.TASK
            SearchFilter.REMINDERS -> capture.reminderAt != null ||
                capture.intent == com.blurt.app.data.model.CaptureIntent.REMINDER
            SearchFilter.NOTES -> capture.intent != com.blurt.app.data.model.CaptureIntent.TASK &&
                capture.reminderAt == null &&
                capture.intent != com.blurt.app.data.model.CaptureIntent.REMINDER
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = BlurtSpacing.grouped),
    ) {
        Spacer(Modifier.height(BlurtSpacing.m))
        SearchHeader(user = user, onOpenProfile = onOpenProfile)
        Spacer(Modifier.height(BlurtSpacing.l))
        SearchField(query = query, onQueryChange = viewModel::onQueryChange)
        if (query.isNotBlank()) {
            Spacer(Modifier.height(BlurtSpacing.s))
            SearchFilterRow(
                selected = filter,
                onSelect = { filter = it },
            )
        }
        Spacer(Modifier.height(BlurtSpacing.l))

        when {
            searching && query.isNotBlank() -> SearchingState()

            query.isBlank() -> {
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = "Find it later",
                    body = "Search your blurts by any word you remember — or by what they mean.",
                )
                SuggestedQueries(onPick = viewModel::onQueryChange)
            }

            filtered.isEmpty() -> Column {
                EmptyState(
                    icon = BlurtIcons.Quote,
                    title = "Nothing matches",
                    body = if (semanticUsed) {
                        "Nothing like \u201C$query\u201D came up. Try saying it a different way."
                    } else {
                        "Nothing matches \u201C$query\u201D. Searching by words right now — try a different one."
                    },
                )
                // Air so the empty state never sits under the glass pill.
                Spacer(Modifier.height(LocalTabBarInset.current + BlurtSpacing.l))
            }

            else -> Column {
                if (semanticUsed) {
                    Surface(
                        shape = RoundedCornerShape(BlurtRadii.pill),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(bottom = BlurtSpacing.m),
                    ) {
                        Text(
                            text = "\u2728 meaning match",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
                Text(
                    text = "TOP RESULTS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp),
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = BlurtSpacing.s),
                )
                // The shell's shared list state, so the frosted copy under the
                // tab bar scrolls in lockstep with the sharp layer.
                val listState = LocalBlurtListState.current ?: rememberLazyListState()
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(BlurtSpacing.s),
                    contentPadding = PaddingValues(bottom = LocalTabBarInset.current + BlurtSpacing.s),
                ) {
                    items(filtered, key = { it.id }) { capture: Capture ->
                        CaptureListItem(
                            capture = capture,
                            onClick = { onOpenCapture(capture.id) },
                            onDelete = viewModel::delete,
                            onArchive = { id, _ -> viewModel.archive(id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
        // Air below the results / empty state so nothing sits under the glass pill.
        Spacer(Modifier.height(LocalTabBarInset.current + BlurtSpacing.s))
    }
}

/**
 * Quiet feedback while a query is in flight — a small violet spinner and
 * "Searching…". Under reduce motion the spinner becomes static text only.
 */
@Composable
private fun SearchingState() {
    val reduceMotion = rememberReduceMotion()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BlurtSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!reduceMotion) {
            CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.5.dp,
            )
            Spacer(Modifier.height(BlurtSpacing.m))
        }
        Text(
            text = "Searching…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The greeting header — "Good morning, Srihari / What are you looking for?" */
@Composable
private fun SearchHeader(user: AuthUser, onOpenProfile: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(
                text = greetingFor(user.displayName),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "What are you looking for?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        val source = rememberBlurtInteractionSource()
        Surface(
            onClick = onOpenProfile,
            interactionSource = source,
            shape = androidx.compose.foundation.shape.CircleShape,
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
                    modifier = Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.CircleShape),
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

/** A few meaning-based example queries — tap one to try it. */
@Composable
private fun SuggestedQueries(onPick: (String) -> Unit) {
    val suggestions = listOf(
        "something I have to do",
        "an idea I liked",
        "a time or a place",
    )
    Text(
        text = "SUGGESTED",
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp),
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = BlurtSpacing.m, bottom = BlurtSpacing.s),
    )
    Column(verticalArrangement = Arrangement.spacedBy(BlurtSpacing.xs)) {
        suggestions.forEach { suggestion ->
            val source = rememberBlurtInteractionSource()
            Text(
                text = suggestion,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .defaultMinSize(minHeight = 44.dp)
                    .clip(RoundedCornerShape(BlurtRadii.s))
                    .clickable(onClick = { onPick(suggestion) })
                    .padding(horizontal = BlurtSpacing.m, vertical = BlurtSpacing.s)
                    .blurtPressScale(source),
            )
        }
    }
}

/** The board's filter pills for search results — All / Tasks / Reminders / Notes. */
private enum class SearchFilter { ALL, TASKS, REMINDERS, NOTES }

@Composable
private fun SearchFilterRow(
    selected: SearchFilter,
    onSelect: (SearchFilter) -> Unit,
) {
    val options = SearchFilter.entries
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options, key = { it.name }) { filter ->
            val label = when (filter) {
                SearchFilter.ALL -> "All"
                SearchFilter.TASKS -> "Tasks"
                SearchFilter.REMINDERS -> "Reminders"
                SearchFilter.NOTES -> "Notes"
            }
            val isSelected = selected == filter
            val source = rememberBlurtInteractionSource()
            Surface(
                onClick = { onSelect(filter) },
                interactionSource = source,
                shape = RoundedCornerShape(BlurtRadii.pill),
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .defaultMinSize(minHeight = 38.dp)
                    .blurtPressScale(source),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val interactionSource = rememberBlurtInteractionSource()
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = RoundedCornerShape(BlurtRadii.s),
        color = fieldFill(),
        border = if (focused) BorderStroke(1.5.dp, borderColor) else null,
        shadowElevation = if (focused) 3.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .blurtPressScale(interactionSource),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = BlurtSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(BlurtSpacing.m))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focused = it.isFocused }
                    .padding(vertical = BlurtSpacing.m),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Search your blurts",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                },
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
