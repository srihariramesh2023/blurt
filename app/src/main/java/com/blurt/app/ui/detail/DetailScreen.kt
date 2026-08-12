package com.blurt.app.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blurt.app.data.model.Capture
import com.blurt.app.data.model.CaptureType
import com.blurt.app.ui.components.BlurtIcons
import com.blurt.app.ui.components.BlurtTopBar
import com.blurt.app.ui.components.blurtPressScale
import com.blurt.app.ui.components.rememberBlurtInteractionSource
import com.blurt.app.ui.components.typeIcon
import com.blurt.app.util.TimeFormat
import com.blurt.app.util.normalizedHttpUrl

/**
 * Detail: full capture with inline edit, delete, and open-in-browser for links.
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

    var showDeleteDialog by remember { mutableStateOf(false) }

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
            .padding(horizontal = 20.dp),
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
                    val currentCapture = capture ?: return@BlurtTopBar
                    val starSource = rememberBlurtInteractionSource()
                    IconButton(
                        onClick = viewModel::toggleImportant,
                        interactionSource = starSource,
                        modifier = Modifier.blurtPressScale(starSource),
                    ) {
                        Icon(
                            imageVector = if (currentCapture.isImportant) BlurtIcons.Star else BlurtIcons.StarOutline,
                            contentDescription = if (currentCapture.isImportant) "Unmark important" else "Mark important",
                            tint = if (currentCapture.isImportant) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val archiveSource = rememberBlurtInteractionSource()
                    IconButton(
                        onClick = viewModel::archive,
                        interactionSource = archiveSource,
                        modifier = Modifier.blurtPressScale(archiveSource),
                    ) {
                        Icon(
                            imageVector = BlurtIcons.Archive,
                            contentDescription = "Archive",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val editSource = rememberBlurtInteractionSource()
                    IconButton(
                        onClick = viewModel::startEditing,
                        interactionSource = editSource,
                        modifier = Modifier.blurtPressScale(editSource),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val deleteSource = rememberBlurtInteractionSource()
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        interactionSource = deleteSource,
                        modifier = Modifier.blurtPressScale(deleteSource),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                        )
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
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
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
) {
    val context = LocalContext.current
    val saveSource = rememberBlurtInteractionSource()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 40.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = typeIcon(capture.type),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = capture.type.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            capture.intent?.let { intent ->
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = intent.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            capture.category?.let { category ->
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = category.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            if (capture.isImportant) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = BlurtIcons.Star,
                    contentDescription = "Important",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = TimeFormat.full(capture.createdAt.toEpochMilli()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        capture.reminderAt?.let { reminderAt ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Reminder · ${TimeFormat.full(reminderAt.toEpochMilli())}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(24.dp))

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
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
            ) {
                editError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onSaveEdit,
                interactionSource = saveSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .blurtPressScale(saveSource),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("Save changes", style = MaterialTheme.typography.labelLarge)
            }
        } else {
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
                    Spacer(Modifier.height(20.dp))
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
                        shape = RoundedCornerShape(16.dp),
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
        }
    }
}
