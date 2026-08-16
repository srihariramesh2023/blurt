package com.blurt.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import com.blurt.app.data.model.CaptureIntent
import com.blurt.app.data.model.CaptureType
import com.blurt.app.ui.theme.BlurtRadii
import com.blurt.app.ui.theme.BlurtSpacing
import com.blurt.app.ui.theme.successColor
import com.blurt.app.util.TimeFormat
import com.blurt.app.util.urlDomain

/**
 * A single capture as the board's individual card (screen 08): a rounded
 * surface on the canvas, a task radio circle that toggles completion (green
 * check when done), a quiet type tile for notes, the title and a date/time
 * line, a bell for scheduled reminders, and an overflow menu.
 */
@Composable
fun CaptureListItem(
    capture: Capture,
    onClick: () -> Unit,
    onDelete: (Long) -> Unit,
    onArchive: ((Long, Boolean) -> Unit)? = null,
    onToggleComplete: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isTask = capture.intent == CaptureIntent.TASK
    Surface(
        shape = RoundedCornerShape(BlurtRadii.m),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = BlurtSpacing.m, vertical = BlurtSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isTask) {
                // The task radio — tap to complete, no navigation.
                TaskCheck(capture = capture, onToggle = onToggleComplete)
                Spacer(Modifier.width(14.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = typeIcon(capture.type),
                        contentDescription = capture.type.label,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = previewText(capture),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (capture.completedAt != null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(BlurtSpacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = subtitle(capture),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (capture.reminderAt != null && capture.completedAt == null &&
                        capture.reminderAt.toEpochMilli() > System.currentTimeMillis()
                    ) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            imageVector = BlurtIcons.Bell,
                            contentDescription = "Reminder set",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    if (capture.isImportant) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            imageVector = BlurtIcons.Star,
                            contentDescription = "Important",
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

/** The task radio — empty circle, or a green check when done. */
@Composable
private fun TaskCheck(capture: Capture, onToggle: ((Long) -> Unit)?) {
    val done = capture.completedAt != null
    val source = rememberBlurtInteractionSource()
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = { onToggle?.invoke(capture.id) }, interactionSource = source, indication = null)
            .blurtPressScale(source),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(successColor()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = BlurtIcons.Check,
                    contentDescription = "Completed",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .then(
                        Modifier.border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                    ),
            )
        }
    }
}

/** Small muted topic tag — the AI's category. */
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
        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(44.dp)) {
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
            shape = RoundedCornerShape(BlurtRadii.m),
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
            shape = RoundedCornerShape(BlurtRadii.xl),
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

/** The board's subtitle — "Today, 5:00 PM" · "Note · Today" · "Tomorrow". */
private fun subtitle(capture: Capture): String {
    val whenDone = if (capture.completedAt != null) {
        "Done · ${TimeFormat.dayTime(capture.completedAt.toEpochMilli())}"
    } else {
        TimeFormat.dayTime(capture.createdAt.toEpochMilli())
    }
    return when (capture.intent) {
        CaptureIntent.TASK, CaptureIntent.REMINDER -> recurringSubtitle(capture, whenDone)
        else -> "${capture.type.label} · ${whenDone}"
    }
}

/** \"Every day · Tomorrow, 9:00 PM\" for a repeating future reminder. */
private fun recurringSubtitle(capture: Capture, fallback: String): String {
    val at = capture.reminderAt?.toEpochMilli() ?: return fallback
    if (capture.completedAt != null || at <= System.currentTimeMillis()) return fallback
    val label = TimeFormat.recurrenceLabel(capture.recurrence, at) ?: return fallback
    return "$label · $fallback"
}
