package com.blurt.app.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blurt.app.data.model.Capture
import com.blurt.app.data.model.CaptureIntent
import com.blurt.app.data.model.CaptureType
import com.blurt.app.ui.components.BlurtIcons
import com.blurt.app.ui.components.BlurtTopBar
import com.blurt.app.ui.components.blurtPressScale
import com.blurt.app.ui.components.rememberBlurtInteractionSource
import com.blurt.app.ui.components.typeIcon
import com.blurt.app.ui.theme.BlurtMotion
import com.blurt.app.ui.theme.BlurtRadii
import com.blurt.app.ui.theme.BlurtSpacing
import com.blurt.app.ui.theme.rememberReduceMotion
import com.blurt.app.ui.theme.successColor
import com.blurt.app.util.TimeFormat
import com.blurt.app.util.normalizedHttpUrl

/**
 * Detail — the board's screen 09: a task-style card with the checkbox and
 * due date, a Details section, Created from / Created / Status rows, a
 * primary "Mark as Complete" action, and a red Delete below. Editing stays
 * inline; share and archive live in the overflow menu.
 */
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    viewModel: DetailViewModel = viewModel(factory = DetailViewModel.Factory),
) {
    val capture by viewModel.capture.collectAsStateWithLifecycle()
    val isEditing by viewModel.isEditing.collectAsStateWithLifecycle()
    val editText by viewModel.editText.collectAsStateWithLifecycle()
    val editError by viewModel.editError.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showDeleteDialog by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(deleted) {
        if (deleted) {
            viewModel.onDeletedHandled()
            onBack()
        }
    }

    LaunchedEffect(archived) {
        if (archived) {
            viewModel.onArchivedHandled()
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = BlurtSpacing.grouped),
    ) {
        BlurtTopBar(
            title = "",
            onBack = onBack,
            actions = {
                if (isEditing) {
                    TextButton(onClick = viewModel::cancelEditing) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    // The board's top-right: menu dots with all the actions.
                    val menuSource = rememberBlurtInteractionSource()
                    IconButton(
                        onClick = { menuOpen = true },
                        interactionSource = menuSource,
                        modifier = Modifier.blurtPressScale(menuSource),
                    ) {
                        Icon(
                            imageVector = BlurtIcons.Tune,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(BlurtRadii.m),
                    ) {
                        val currentCapture = capture
                        if (currentCapture != null) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                text = { Text("Edit") },
                                onClick = { menuOpen = false; viewModel.startEditing() },
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (currentCapture.isImportant) BlurtIcons.Star else BlurtIcons.StarOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                text = { Text(if (currentCapture.isImportant) "Unmark Important" else "Mark Important") },
                                onClick = { menuOpen = false; viewModel.toggleImportant() },
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(BlurtIcons.Share, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                text = { Text("Share") },
                                onClick = {
                                    menuOpen = false
                                    runCatching {
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, currentCapture.content)
                                        }
                                        context.startActivity(Intent.createChooser(send, "Share blurt"))
                                    }
                                },
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(BlurtIcons.Archive, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                text = { Text("Archive") },
                                onClick = { menuOpen = false; viewModel.archive() },
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                                },
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; showDeleteDialog = true },
                            )
                        }
                    }
                }
            },
        )

        val current = capture
        if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            DetailContent(
                capture = current,
                isEditing = isEditing,
                editText = editText,
                editError = editError,
                onEditTextChange = viewModel::onEditTextChange,
                onSaveEdit = viewModel::saveEdit,
                onToggleComplete = viewModel::toggleCompleted,
                onRequestDelete = { showDeleteDialog = true },
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
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
                    showDeleteDialog = false
                    viewModel.delete()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }
}

@Composable
private fun DetailContent(
    capture: Capture,
    isEditing: Boolean,
    editText: String,
    editError: String?,
    onEditTextChange: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onToggleComplete: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val context = LocalContext.current
    val isTask = capture.intent == CaptureIntent.TASK || capture.completedAt != null
    val done = capture.completedAt != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = BlurtSpacing.xxl),
    ) {
        // The task card — checkbox, title, due line (board screen 09).
        Surface(
            shape = RoundedCornerShape(BlurtRadii.m),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(BlurtSpacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val checkSource = rememberBlurtInteractionSource()
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleComplete, interactionSource = checkSource, indication = null)
                        .blurtPressScale(checkSource),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(successColor()),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = BlurtIcons.Check,
                                contentDescription = "Completed",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
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
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = capture.content,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onBackground,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (capture.reminderAt != null) BlurtIcons.Bell else BlurtIcons.Quote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = capture.reminderAt?.let {
                                "Today, ${TimeFormat.dayTime(it.toEpochMilli())}"
                            } ?: capture.type.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(BlurtSpacing.xl))

        if (isEditing) {
            TextField(
                value = editText,
                onValueChange = onEditTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
            AnimatedVisibility(
                visible = editError != null,
                enter = if (rememberReduceMotion()) {
                    fadeIn(androidx.compose.animation.core.tween(BlurtMotion.FADE_MS)) +
                        expandVertically(androidx.compose.animation.core.tween(BlurtMotion.FADE_MS))
                } else {
                    fadeIn(BlurtMotion.micro()) + expandVertically(BlurtMotion.micro())
                },
            ) {
                editError?.let {
                    Spacer(Modifier.height(BlurtSpacing.s))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(BlurtSpacing.m))
            val saveSource = rememberBlurtInteractionSource()
            Button(
                onClick = onSaveEdit,
                interactionSource = saveSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .blurtPressScale(saveSource),
                shape = RoundedCornerShape(BlurtRadii.l),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("Save changes", style = MaterialTheme.typography.labelLarge)
            }
            return@Column
        }

        // Details — the body, or the link with its Open action.
        Text(
            text = "Details",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp),
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(BlurtSpacing.s))
        when {
            capture.type == CaptureType.LINK -> {
                Text(
                    text = capture.content,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(BlurtSpacing.m))
                val linkSource = rememberBlurtInteractionSource()
                Button(
                    onClick = {
                        val url = capture.content.normalizedHttpUrl()
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                    interactionSource = linkSource,
                    modifier = Modifier.blurtPressScale(linkSource),
                    shape = RoundedCornerShape(BlurtRadii.l),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = typeIcon(CaptureType.LINK),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Open link", style = MaterialTheme.typography.labelLarge)
                }
            }

            capture.content.isNotBlank() -> {
                Text(
                    text = capture.content,
                    style = if (capture.content.length <= 60) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        // The meta rows — Created from / Created / Status.
        Spacer(Modifier.height(BlurtSpacing.xl))
        Surface(
            shape = RoundedCornerShape(BlurtRadii.m),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                MetaRow(
                    label = "Created",
                    value = TimeFormat.full(capture.createdAt.toEpochMilli()),
                    icon = BlurtIcons.Quote,
                )
                if (capture.reminderAt != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = BlurtSpacing.m, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = BlurtIcons.Bell,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(BlurtSpacing.m))
                        Text(
                            text = "Reminder",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (done) "Done" else TimeFormat.dayTime(capture.reminderAt.toEpochMilli()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (done) successColor() else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (isTask) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = BlurtSpacing.m, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = BlurtIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(BlurtSpacing.m))
                        Text(
                            text = "Status",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (done) "Completed" else "Pending",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (done) successColor() else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(BlurtSpacing.xl))

        // The primary action — Mark as Complete, or Done / Reopen.
        if (isTask) {
            val completeSource = rememberBlurtInteractionSource()
            Button(
                onClick = onToggleComplete,
                interactionSource = completeSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .blurtPressScale(completeSource),
                shape = RoundedCornerShape(BlurtRadii.l),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = if (done) "Reopen" else "Mark as Complete",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Spacer(Modifier.height(BlurtSpacing.m))

        // Delete — the quiet red text action at the bottom (board screen 09).
        val deleteSource = rememberBlurtInteractionSource()
        Text(
            text = "Delete",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BlurtRadii.s))
                .clickable(onClick = onRequestDelete, interactionSource = deleteSource, indication = null)
                .blurtPressScale(deleteSource)
                .padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun MetaRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BlurtSpacing.m, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(BlurtSpacing.m))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
