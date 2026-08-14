package com.blurt.app.ui.library

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blurt.app.ui.components.blurtPressScale
import com.blurt.app.ui.components.rememberBlurtInteractionSource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blurt.app.data.model.Capture
import com.blurt.app.data.model.CaptureCategory
import com.blurt.app.ui.components.BlurtIcons
import com.blurt.app.ui.components.CaptureListItem
import com.blurt.app.ui.components.EmptyState
import com.blurt.app.ui.components.LocalBlurtListState
import com.blurt.app.ui.theme.BlurtSpacing

/**
 * Library: every capture, newest first, browsable through automatically
 * maintained collections — Reminders, Tasks, Ideas, Important, Archived —
 * plus the AI-category chips for the live lists.
 */
@Composable
fun LibraryScreen(
    onOpenCapture: (Long) -> Unit,
    onCaptureNew: () -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val captures by viewModel.captures.collectAsStateWithLifecycle()
    val allCaptures by viewModel.allCaptures.collectAsStateWithLifecycle()
    val selectedCollection by viewModel.selectedCollection.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = BlurtSpacing.grouped),
    ) {
        Spacer(Modifier.height(BlurtSpacing.m))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.weight(1f))
            // The single primary action — a filled round blue add.
            val addSource = rememberBlurtInteractionSource()
            Surface(
                onClick = onCaptureNew,
                interactionSource = addSource,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(44.dp)
                    .blurtPressScale(addSource),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "New blurt",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (captures.size == 1) "1 blurt" else "${captures.size} blurts",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(BlurtSpacing.l))

        CollectionFilterRow(
            selected = selectedCollection,
            onSelect = viewModel::selectCollection,
        )
        // Categories belong to the live lists — the archive is its own view.
        if (selectedCollection != LibraryCollection.ARCHIVED) {
            Spacer(Modifier.height(8.dp))
            CategoryFilterRow(
                captures = allCaptures,
                selected = selectedCategory,
                onSelect = viewModel::selectCategory,
            )
        }
        Spacer(Modifier.height(BlurtSpacing.l))

        if (captures.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            EmptyState(
                icon = BlurtIcons.BlurtMark,
                title = emptyTitle(selectedCollection),
                body = if (allCaptures.isEmpty()) {
                    "Your blurts — text, ideas and links — will live here."
                } else {
                    "Pick another collection or clear the filters."
                },
                action = {
                    TextButton(onClick = onCaptureNew) {
                        Text(
                            text = "Blurt something",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        } else {
            // The iOS grouped card — every capture in one rounded surface.
            Surface(
                shape = RoundedCornerShape(com.blurt.app.ui.theme.BlurtRadii.m),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                LazyColumn(
                    state = LocalBlurtListState.current ?: rememberLazyListState(),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(captures, key = { _, it -> it.id }) { index, capture: Capture ->
                        CaptureListItem(
                            capture = capture,
                            onClick = { onOpenCapture(capture.id) },
                            onDelete = viewModel::delete,
                            onArchive = viewModel::setArchived,
                            modifier = Modifier.animateItem(),
                            showDivider = index > 0,
                        )
                    }
                }
            }
        }
    }
}

private fun emptyTitle(collection: LibraryCollection): String = when (collection) {
    LibraryCollection.ALL -> "No captures yet"
    LibraryCollection.REMINDERS -> "No reminders"
    LibraryCollection.TASKS -> "No tasks"
    LibraryCollection.IDEAS -> "No ideas"
    LibraryCollection.IMPORTANT -> "Nothing important yet"
    LibraryCollection.ARCHIVED -> "Nothing archived"
}

/** The main browse collections — All, then the automatically kept ones. */
@Composable
private fun CollectionFilterRow(
    selected: LibraryCollection,
    onSelect: (LibraryCollection) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(LibraryCollection.entries, key = { it.name }) { collection ->
            FilterChip(
                label = collection.label,
                selected = selected == collection,
            ) { onSelect(collection) }
        }
    }
}

/** Horizontal chip row: All + every category present in the library. */
@Composable
private fun CategoryFilterRow(
    captures: List<Capture>,
    selected: CaptureCategory?,
    onSelect: (CaptureCategory?) -> Unit,
) {
    val present = captures.mapNotNull { it.category }.distinct()
    if (present.isEmpty()) return

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(label = "Category · All", selected = selected == null) { onSelect(null) }
        }
        items(present, key = { it.name }) { category ->
            FilterChip(label = category.label, selected = selected == category) {
                onSelect(category)
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val source = rememberBlurtInteractionSource()
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(com.blurt.app.ui.theme.BlurtRadii.pill),
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.blurtPressScale(source),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
