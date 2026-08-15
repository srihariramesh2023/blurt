package com.blurt.app.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blurt.app.auth.AuthUser
import com.blurt.app.data.model.Capture
import com.blurt.app.data.model.CaptureCategory
import com.blurt.app.ui.components.BlurtIcons
import com.blurt.app.ui.components.BlurtListSkeleton
import com.blurt.app.ui.components.CaptureListItem
import com.blurt.app.ui.components.EmptyState
import com.blurt.app.ui.components.LocalBlurtListState
import com.blurt.app.ui.components.LocalTabBarInset
import com.blurt.app.ui.components.blurtPressScale
import com.blurt.app.ui.components.rememberBlurtInteractionSource
import com.blurt.app.ui.theme.BlurtRadii
import com.blurt.app.ui.theme.BlurtSpacing
import com.blurt.app.ui.theme.rememberReduceMotion
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Library — the board's screen 08: a greeting header, the filter pills
 * (All / Tasks / Reminders / Notes), and every capture as an individual
 * card grouped under Today / Yesterday. Tasks carry the radio check; notes
 * carry their type tile; reminders carry the bell.
 */
@Composable
fun LibraryScreen(
    user: AuthUser,
    onOpenCapture: (Long) -> Unit,
    onCaptureNew: () -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val captures by viewModel.captures.collectAsStateWithLifecycle()
    val allCaptures by viewModel.allCaptures.collectAsStateWithLifecycle()
    val selectedCollection by viewModel.selectedCollection.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = BlurtSpacing.grouped),
    ) {
        Spacer(Modifier.height(BlurtSpacing.m))
        LibraryHeader(
            user = user,
            count = allCaptures.size,
            onCaptureNew = onCaptureNew,
            onOpenProfile = onOpenProfile,
        )
        Spacer(Modifier.height(BlurtSpacing.l))

        CollectionFilterRow(
            selected = selectedCollection,
            onSelect = viewModel::selectCollection,
        )
        Spacer(Modifier.height(BlurtSpacing.l))

        when {
            // The skeleton covers the frame before the first list arrives,
            // so the empty state never flashes before real data.
            isLoading -> BlurtListSkeleton()

            captures.isEmpty() -> {
                Spacer(Modifier.height(24.dp))
                EmptyState(
                    icon = BlurtIcons.BlurtMark,
                    title = emptyTitle(selectedCollection),
                    body = if (allCaptures.isEmpty()) {
                        "Your blurts — text, ideas, tasks and reminders — will live here."
                    } else {
                        "Pick another filter to see more."
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
            }

            else -> DayGroupedList(
                captures = captures,
                onOpenCapture = onOpenCapture,
                onDelete = viewModel::delete,
                onArchive = viewModel::setArchived,
                onToggleComplete = viewModel::toggleCompleted,
            )
        }
        // Air below the list / empty state so nothing sits under the glass pill.
        Spacer(Modifier.height(LocalTabBarInset.current + BlurtSpacing.s))
    }
}

/** The greeting header — "Good morning, Srihari / All your captured things." */
@Composable
private fun LibraryHeader(
    user: AuthUser,
    count: Int,
    onCaptureNew: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(
                text = greetingFor(user.displayName),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (count == 0) "All your captured things." else "$count captured things.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        // The single primary action — a filled round violet add.
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
        Spacer(Modifier.width(BlurtSpacing.s))
        // The avatar — one tap into Settings.
        val source = rememberBlurtInteractionSource()
        Surface(
            onClick = onOpenProfile,
            interactionSource = source,
            shape = CircleShape,
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
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
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

/** "Good morning / afternoon / evening, <first name>". */
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

/**
 * The captures grouped under day headers — Today, Yesterday, then date
 * lines — the board's sectioned list. Each item is an individual card.
 */
@Composable
private fun DayGroupedList(
    captures: List<Capture>,
    onOpenCapture: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onArchive: (Long, Boolean) -> Unit,
    onToggleComplete: (Long) -> Unit,
) {
    val groups = captures.groupBy { dayLabel(it.createdAt.toEpochMilli()) }
    val today = LocalDate.now(ZoneId.systemDefault())
    // The shell's shared list state, so the frosted copy under the tab bar
    // scrolls in lockstep with the sharp layer.
    val listState = LocalBlurtListState.current ?: rememberLazyListState()
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(BlurtSpacing.s),
        contentPadding = PaddingValues(bottom = LocalTabBarInset.current + BlurtSpacing.s),
    ) {
        groups.forEach { (label, group) ->
            item(key = "header-$label") {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp),
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = BlurtSpacing.s),
                )
            }
            items(group, key = { it.id }) { capture ->
                CaptureListItem(
                    capture = capture,
                    onClick = { onOpenCapture(capture.id) },
                    onDelete = onDelete,
                    onArchive = onArchive,
                    onToggleComplete = onToggleComplete,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/** "Today" / "Yesterday" / "MMM d" — the board's section headers. */
private fun dayLabel(epochMillis: Long): String {
    val date = java.time.Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now(ZoneId.systemDefault())
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    }
}

private fun emptyTitle(collection: LibraryCollection): String = when (collection) {
    LibraryCollection.ALL -> "No captures yet"
    LibraryCollection.TASKS -> "No tasks"
    LibraryCollection.REMINDERS -> "No reminders"
    LibraryCollection.NOTES -> "No notes"
    LibraryCollection.IDEAS -> "No ideas"
    LibraryCollection.IMPORTANT -> "Nothing important yet"
    LibraryCollection.ARCHIVED -> "Nothing archived"
}

/**
 * The board's filter pills — one rounded container with a single violet
 * capsule that glides between pills, exactly like the tab bar. The capsule is
 * measured from each pill's real geometry (x, y, width, height), so it always
 * sits perfectly centered on the pill it marks — and because it tracks the
 * live positions, it follows the row when the pills scroll.
 */
@Composable
private fun CollectionFilterRow(
    selected: LibraryCollection,
    onSelect: (LibraryCollection) -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val density = LocalDensity.current
    // Live geometry of every pill, relative to the row container.
    var containerPos by remember { mutableStateOf(Offset.Zero) }
    val slots = remember { mutableStateMapOf<LibraryCollection, PillSlot>() }
    // The capsule appears with a snap on first layout; only selection changes
    // after that glide. `measured` flips one frame later (LaunchedEffect) so
    // the very first appearance never animates from the corner.
    var measured by remember { mutableStateOf(false) }

    val target = slots[selected]
    LaunchedEffect(target) {
        if (target != null) measured = true
    }
    // Snap to the first measured position; glide on every selection after.
    val capsuleSpec: androidx.compose.animation.core.FiniteAnimationSpec<Dp> =
        if (measured && !reduceMotion) {
            spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)
        } else {
            tween(0)
        }
    val capsuleX by animateDpAsState(target?.x ?: 0.dp, capsuleSpec, label = "filterCapsuleX")
    val capsuleY by animateDpAsState(target?.y ?: 0.dp, capsuleSpec, label = "filterCapsuleY")
    val capsuleW by animateDpAsState(target?.width ?: 0.dp, capsuleSpec, label = "filterCapsuleW")
    val capsuleH by animateDpAsState(target?.height ?: 0.dp, capsuleSpec, label = "filterCapsuleH")

    Surface(
        shape = RoundedCornerShape(BlurtRadii.pill),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { containerPos = it.positionInRoot() },
        ) {
            // The one moving highlight, drawn under the pills.
            if (measured) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = capsuleX, y = capsuleY)
                        .size(capsuleW, capsuleH)
                        .clip(RoundedCornerShape(BlurtRadii.pill))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(3.dp),
            ) {
                items(LibraryCollection.entries, key = { it.name }) { collection ->
                    val selectedSegment = selected == collection
                    val source = rememberBlurtInteractionSource()
                    Row(
                        modifier = Modifier
                            .onGloballyPositioned { coords ->
                                val rel = coords.positionInRoot() - containerPos
                                slots[collection] = PillSlot(
                                    x = with(density) { rel.x.toDp() },
                                    y = with(density) { rel.y.toDp() },
                                    width = with(density) { coords.size.width.toDp() },
                                    height = with(density) { coords.size.height.toDp() },
                                )
                            }
                            .clip(RoundedCornerShape(BlurtRadii.pill))
                            .clickable(onClick = { onSelect(collection) }, interactionSource = source, indication = null)
                            .defaultMinSize(minHeight = 38.dp)
                            .blurtPressScale(source)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = collection.icon,
                            contentDescription = null,
                            tint = if (selectedSegment) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = collection.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selectedSegment) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** A pill's position and size within the filter row — the capsule's target. */
private data class PillSlot(
    val x: Dp,
    val y: Dp,
    val width: Dp,
    val height: Dp,
)

/** A small icon per collection, so the pill row reads at a glance. */
private val LibraryCollection.icon: ImageVector
    get() = when (this) {
        LibraryCollection.ALL -> BlurtIcons.BlurtMark
        LibraryCollection.TASKS -> BlurtIcons.Check
        LibraryCollection.REMINDERS -> BlurtIcons.Bell
        LibraryCollection.NOTES -> BlurtIcons.Quote
        LibraryCollection.IDEAS -> BlurtIcons.Idea
        LibraryCollection.IMPORTANT -> BlurtIcons.Star
        LibraryCollection.ARCHIVED -> BlurtIcons.Archive
    }
