package com.blurt.app.ui.capture

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.blurt.app.data.model.CaptureType
import com.blurt.app.ui.components.BlurtIcons
import com.blurt.app.ui.components.BlurtTopBar
import com.blurt.app.ui.components.blurtPressScale
import com.blurt.app.ui.components.rememberBlurtInteractionSource
import com.blurt.app.ui.components.typeIcon
import com.blurt.app.util.isHttpUrl
import com.blurt.app.util.normalizedHttpUrl
import com.blurt.app.util.urlDomain

/**
 * The fast capture composer. One screen, four types, switchable at the top.
 * Editors cross-fade between types, and the save button reflects validity.
 */
@Composable
fun CaptureScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CaptureViewModel = viewModel(factory = CaptureViewModel.Factory),
) {
    val type by viewModel.type.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val imageUri by viewModel.imageUri.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()

    LaunchedEffect(saved) {
        saved?.let {
            viewModel.onSavedHandled()
            onSaved()
        }
    }

    val canSave = when (type) {
        CaptureType.TEXT, CaptureType.IDEA -> content.isNotBlank()
        CaptureType.LINK -> content.isNotBlank() && content.normalizedHttpUrl().isHttpUrl()
        CaptureType.IMAGE -> imageUri != null
    }
    val saveSource = rememberBlurtInteractionSource()

    // Autofocus the text editor once on open; don't fight the user on later type switches.
    val textFocusRequester = remember { FocusRequester() }
    var autofocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if ((type == CaptureType.TEXT || type == CaptureType.IDEA) && !autofocused) {
            textFocusRequester.requestFocus()
            autofocused = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 20.dp),
    ) {
        BlurtTopBar(title = "New Blurt", onBack = onBack)
        Spacer(Modifier.height(14.dp))
        TypeSelector(selected = type, onSelect = viewModel::onTypeSelected)
        Spacer(Modifier.height(20.dp))

        AnimatedContent(
            targetState = type,
            transitionSpec = {
                (fadeIn(tween(200)) + slideInVertically(tween(260)) { it / 10 })
                    .togetherWith(fadeOut(tween(120)))
            },
            label = "typeEditor",
        ) { currentType ->
            when (currentType) {
                CaptureType.TEXT, CaptureType.IDEA -> TextEditor(
                    value = content,
                    hint = if (currentType == CaptureType.TEXT) "What's on your mind?" else "A fleeting idea…",
                    onValueChange = viewModel::onContentChange,
                    focusRequester = textFocusRequester,
                )

                CaptureType.LINK -> LinkEditor(
                    value = content,
                    onValueChange = viewModel::onContentChange,
                )

                CaptureType.IMAGE -> ImageEditor(
                    imageUri = imageUri,
                    caption = content,
                    onCaptionChange = viewModel::onContentChange,
                    onPickImage = viewModel::onImagePicked,
                )
            }
        }

        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
        ) {
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = viewModel::save,
            enabled = canSave,
            interactionSource = saveSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .blurtPressScale(saveSource),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Text("Save Blurt", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** Segmented Text / Idea / Link / Image selector with spring color transitions. */
@Composable
private fun TypeSelector(selected: CaptureType, onSelect: (CaptureType) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CaptureType.entries.forEach { type ->
            val isSelected = type == selected
            val background by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = tween(200),
                label = "typeBg",
            )
            val foreground by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(200),
                label = "typeFg",
            )
            Surface(
                onClick = { onSelect(type) },
                shape = RoundedCornerShape(12.dp),
                color = background,
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = typeIcon(type),
                        contentDescription = null,
                        tint = foreground,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = type.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = foreground,
                    )
                }
            }
        }
    }
}

@Composable
private fun TextEditor(
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp)
            .focusRequester(focusRequester),
        placeholder = { Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
}

@Composable
private fun LinkEditor(value: String, onValueChange: (String) -> Unit) {
    val normalized = value.normalizedHttpUrl()
    val isValid = value.isNotBlank() && normalized.isHttpUrl()
    val helperColor = when {
        value.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
        isValid -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }

    Column {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Paste a link", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
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
        Spacer(Modifier.height(10.dp))
        Text(
            text = when {
                value.isBlank() -> "Paste a link to save it as a blurt."
                isValid -> "Opens ${normalized.urlDomain()}"
                else -> "That doesn't look like a valid link yet."
            },
            style = MaterialTheme.typography.bodySmall,
            color = helperColor,
        )
    }
}

@Composable
private fun ImageEditor(
    imageUri: android.net.Uri?,
    caption: String,
    onCaptionChange: (String) -> Unit,
    onPickImage: (android.net.Uri) -> Unit,
) {
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { onPickImage(it) }
    }

    Column {
        if (imageUri == null) {
            Surface(
                onClick = {
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = BlurtIcons.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Choose image",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "From your photos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = imageUri != null,
            enter = fadeIn(tween(240)) + scaleIn(tween(240)),
        ) {
            Column {
                Box {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 300.dp)
                            .clip(RoundedCornerShape(20.dp)),
                    )
                    IconButton(
                        onClick = {
                            pickImage.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Change image",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextField(
                    value = caption,
                    onValueChange = onCaptionChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("Add a caption (optional)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
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
            }
        }
    }
}
