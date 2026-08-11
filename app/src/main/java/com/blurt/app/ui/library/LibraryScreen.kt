package com.blurt.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blurt.app.data.model.Capture
import com.blurt.app.data.model.CaptureType
import com.blurt.app.ui.components.BlurtIcons
import com.blurt.app.ui.components.CaptureListItem
import com.blurt.app.ui.components.EmptyState

/**
 * Library: every capture, newest first.
 */
@Composable
fun LibraryScreen(
    onOpenCapture: (Long) -> Unit,
    onCaptureNew: () -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val captures by viewModel.captures.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onCaptureNew) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "New blurt",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (captures.size == 1) "1 blurt" else "${captures.size} blurts",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        if (captures.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            EmptyState(
                icon = BlurtIcons.BlurtMark,
                title = "No captures yet",
                body = "Your blurts — text, ideas, links and images — will live here.",
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
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}
