package com.blurt.app.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blurt.app.data.model.Capture
import com.blurt.app.ui.components.BlurtIcons
import com.blurt.app.ui.components.CaptureListItem
import com.blurt.app.ui.components.EmptyState
import com.blurt.app.ui.components.LocalBlurtListState
import com.blurt.app.ui.components.blurtPressScale
import com.blurt.app.ui.components.rememberBlurtInteractionSource
import com.blurt.app.ui.theme.BlurtSpacing
import com.blurt.app.ui.theme.fieldFill

/**
 * Search: live local keyword + semantic search across blurts. The field is
 * an iOS-style gray fill (magnifier left, clear right); the blue ring
 * appears only while typing.
 */
@Composable
fun SearchScreen(
    onOpenCapture: (Long) -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val semanticUsed by viewModel.semanticUsed.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = BlurtSpacing.grouped),
    ) {
        Spacer(Modifier.height(BlurtSpacing.m))
        Text(
            text = "Search",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(BlurtSpacing.l))
        SearchField(query = query, onQueryChange = viewModel::onQueryChange)
        Spacer(Modifier.height(BlurtSpacing.l))

        when {
            query.isBlank() -> {
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = "Find it later",
                    body = "Search your blurts by any word you remember — or by what they mean.",
                )
                SuggestedQueries(onPick = viewModel::onQueryChange)
            }

            results.isEmpty() -> EmptyState(
                icon = BlurtIcons.Quote,
                title = "Nothing matches",
                body = if (semanticUsed) {
                    "Nothing like \u201C$query\u201D came up. Try saying it a different way."
                } else {
                    "Nothing matches \u201C$query\u201D. Searching by words right now — try a different one."
                },
            )

            else -> Column {
                if (semanticUsed) {
                    Surface(
                        shape = RoundedCornerShape(com.blurt.app.ui.theme.BlurtRadii.pill),
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
                // The iOS grouped card — results in one rounded surface.
                Surface(
                    shape = RoundedCornerShape(com.blurt.app.ui.theme.BlurtRadii.m),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LazyColumn(
                        state = LocalBlurtListState.current ?: rememberLazyListState(),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                    items(results, key = { it.id }) { capture: Capture ->
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
        }
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
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
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
                    .clip(RoundedCornerShape(com.blurt.app.ui.theme.BlurtRadii.s))
                    .clickable(onClick = { onPick(suggestion) })
                    .padding(horizontal = BlurtSpacing.m, vertical = BlurtSpacing.s)
                    .blurtPressScale(source),
            )
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
    // The iOS search field: a quiet gray fill, no border at rest, a soft
    // blue ring once focused.
    Surface(
        shape = RoundedCornerShape(com.blurt.app.ui.theme.BlurtRadii.s),
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
