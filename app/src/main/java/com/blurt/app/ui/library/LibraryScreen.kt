package com.blurt.app.ui.library

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blurt.app.data.model.Capture
import com.blurt.app.data.model.CaptureCategory
import com.blurt.app.ui.components.BlurtIcons
import com.blurt.app.ui.components.CaptureListItem
import com.blurt.app.ui.components.EmptyState
import com.blurt.app.ui.theme.BlurtSpacing

/**
 * Library: every capture, newest first, with AI-category filter chips.
 */
@Composable
fun LibraryScreen(
    onOpenCapture: (Long) -> Unit,
    onCaptureNew: () -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val captures by viewModel.captures.collectAsStateWithLifecycle()
    val allCaptures by viewModel.allCaptures.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(BlurtSpacing.m))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onCaptureNew, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "New blurt",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
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

        CategoryFilterRow(
            captures = allCaptures,
            selected = selectedCategory,
            onSelect = viewModel::selectCategory,
        )
        Spacer(Modifier.height(BlurtSpacing.l))

        if (captures.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            EmptyState(
                icon = BlurtIcons.BlurtMark,
                title = if (allCaptures.isEmpty()) "No captures yet" else "Nothing in ${selectedCategory?.label.orEmpty()}",
                body = if (allCaptures.isEmpty()) {
                    "Your blurts — text, ideas and links — will live here."
                } else {
                    "Pick another category or clear the filter."
                },
            )
            TextButton(onClick = onCaptureNew, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(
                    text = "Blurt something",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(captures, key = { it.id }) { capture: Capture ->
                    CaptureListItem(
                        capture = capture,
                        onClick = { onOpenCapture(capture.id) },
                        onDelete = viewModel::delete,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
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
            FilterChip(label = "All", selected = selected == null) { onSelect(null) }
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
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(BlurtSpacing.l),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
