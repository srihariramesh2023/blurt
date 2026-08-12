package com.blurt.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blurt.app.data.model.Capture
import com.blurt.app.data.model.CaptureType
import com.blurt.app.ui.theme.BlurtSpacing
import com.blurt.app.util.TimeFormat
import com.blurt.app.util.urlDomain

/**
 * A single capture row used across Home, Library and Search: a muted type
 * tile, the preview and timestamp (typography carries the hierarchy), quiet
 * gold markers for important blurts and scheduled reminders, and an overflow
 * menu for archiving and deleting.
 */
@Composable
fun CaptureListItem(
    capture: Capture,
    onClick: () -> Unit,
    onDelete: (Long) -> Unit,
    onArchive: ((Long, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(BlurtSpacing.l),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = BlurtSpacing.l, vertical = BlurtSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(BlurtSpacing.s))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = typeIcon(capture.type),
                    contentDescription = capture.type.label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(BlurtSpacing.m))
            Column(Modifier.weight(1f)) {
                Text(
                    text = previewText(capture),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${capture.type.label} · ${TimeFormat.relative(capture.createdAt.toEpochMilli())}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (capture.isImportant) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            imageVector = BlurtIcons.Star,
                            contentDescription = "Important",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    if (capture.reminderAt != null && capture.reminderAt.toEpochMilli() > System.currentTimeMillis()) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            imageVector = BlurtIcons.Bell,
                            contentDescription = "Reminder set",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    capture.category?.let { category ->
                        Spacer(Modifier.width(6.dp))
                        CategoryPill(label = category.label)
                    }
                }
            }
            OverflowMenu(capture = capture, onDelete = onDelete, onArchive = onArchive)
        }
    }
}

/** Small muted topic tag — the AI's category, shown quietly next to the meta. */
@Composable
private fun CategoryPill(label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun OverflowMenu(
    capture: Capture,
    onDelete: (Long) -> Unit,
    onArchive: ((Long, Boolean) -> Unit)?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "More",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(BlurtSpacing.m),
        ) {
            onArchive?.let { archive ->
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = BlurtIcons.Archive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    text = { Text(if (capture.isArchived) "Unarchive" else "Archive") },
                    onClick = {
                        menuOpen = false
                        archive(capture.id, !capture.isArchived)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menuOpen = false
                    confirmDelete = true
                },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(BlurtSpacing.xl),
            title = { Text("Delete this blurt?") },
            text = {
                Text(
                    text = "This can't be undone.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(capture.id)
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }
}

private fun previewText(capture: Capture): String = when {
    capture.type == CaptureType.LINK -> capture.content.urlDomain()
    else -> capture.content
}
