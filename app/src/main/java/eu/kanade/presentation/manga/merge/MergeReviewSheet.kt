package eu.kanade.presentation.manga.merge

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

/**
 * Overlay for the merge review. While the user is selecting it shows only a slim collapsed bar.
 * Tapping the bar expands a glass sheet upwards with the chapter comparison and the confirm action.
 * The sheet closes by dragging the handle down, tapping the scrim, or pressing Back.
 */
@Composable
internal fun MergeReviewHost(
    state: MergeScreenState,
    hazeState: HazeState,
    colors: GlassColors,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPrimarySelected: (String) -> Unit,
    onRemoveResult: (MergeCardItem) -> Unit,
    onConfirmMerge: () -> Unit,
    cover: @Composable (MergeCardItem, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = expanded) { onExpandedChange(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxSheetHeight = maxHeight * 0.88f

        AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onExpandedChange(false) },
                    ),
            )
        }

        AnimatedVisibility(
            visible = state.selectedResults.isNotEmpty() && !expanded,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            CollapsedBar(
                state = state,
                hazeState = hazeState,
                colors = colors,
                onExpand = { onExpandedChange(true) },
                cover = cover,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            ReviewSheet(
                state = state,
                hazeState = hazeState,
                colors = colors,
                maxHeight = maxSheetHeight,
                onCollapse = { onExpandedChange(false) },
                onPrimarySelected = onPrimarySelected,
                onRemoveResult = onRemoveResult,
                onConfirmMerge = onConfirmMerge,
                cover = cover,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Collapsed bar
// ---------------------------------------------------------------------------

@Composable
private fun CollapsedBar(
    state: MergeScreenState,
    hazeState: HazeState,
    colors: GlassColors,
    onExpand: () -> Unit,
    cover: @Composable (MergeCardItem, Modifier) -> Unit,
) {
    val thumbs = state.selectedResults.take(3)
    val thumbShape = RoundedCornerShape(6.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .glass(hazeState, colors, RoundedCornerShape(24.dp))
            .clickable(onClickLabel = MergeText.Expand, onClick = onExpand)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.width((34 + (thumbs.size - 1) * 18).dp)) {
            thumbs.forEachIndexed { index, item ->
                cover(
                    item,
                    Modifier
                        .padding(start = (index * 18).dp)
                        .width(34.dp)
                        .aspectRatio(2f / 3f)
                        .clip(thumbShape)
                        .border(1.dp, colors.border, thumbShape),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = MergeText.selectedCount(state.selectedResults.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = MergeText.ReviewChapters,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---------------------------------------------------------------------------
// Expanded sheet
// ---------------------------------------------------------------------------

@Composable
private fun ReviewSheet(
    state: MergeScreenState,
    hazeState: HazeState,
    colors: GlassColors,
    maxHeight: Dp,
    onCollapse: () -> Unit,
    onPrimarySelected: (String) -> Unit,
    onRemoveResult: (MergeCardItem) -> Unit,
    onConfirmMerge: () -> Unit,
    cover: @Composable (MergeCardItem, Modifier) -> Unit,
) {
    val group = state.groupItems
    val maxTotal = remember(group) { group.maxOfOrNull { it.totalChapters ?: 0 } ?: 0 }
    val difference = remember(group) { chapterDifference(group) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .glass(
                state = hazeState,
                colors = colors,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                strong = true,
            ),
    ) {
        DragHandle(onCollapse = onCollapse, colors = colors)

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = MergeText.ChapterComparison,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = MergeText.StarHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            group.forEach { item ->
                val isOriginal = item.key == state.original.key
                ReviewEntry(
                    item = item,
                    isOriginal = isOriginal,
                    isPrimary = item.key == state.primaryKey,
                    maxTotal = maxTotal,
                    colors = colors,
                    onSelectPrimary = { onPrimarySelected(item.key) },
                    onRemove = if (isOriginal) null else ({ onRemoveResult(item) }),
                    cover = cover,
                )
            }

            if (difference != null) {
                Text(
                    text = difference,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.tint)
                        .border(0.5.dp, colors.border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            Button(
                onClick = onConfirmMerge,
                enabled = state.canConfirm,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isConfirming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(MergeText.Confirm)
            }
        }
    }
}

@Composable
private fun DragHandle(onCollapse: () -> Unit, colors: GlassColors) {
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(onCollapse) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > threshold) onCollapse() },
                    onDragCancel = { total = 0f },
                    onVerticalDrag = { _, dy -> total += dy },
                )
            }
            .clickable(onClickLabel = MergeText.Collapse, onClick = onCollapse)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(colors.border),
        )
    }
}

@Composable
private fun ReviewEntry(
    item: MergeCardItem,
    isOriginal: Boolean,
    isPrimary: Boolean,
    maxTotal: Int,
    colors: GlassColors,
    onSelectPrimary: () -> Unit,
    onRemove: (() -> Unit)?,
    cover: @Composable (MergeCardItem, Modifier) -> Unit,
) {
    val coverShape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.width(76.dp)) {
            cover(
                item,
                Modifier
                    .width(76.dp)
                    .aspectRatio(2f / 3f)
                    .clip(coverShape)
                    .border(
                        width = if (isPrimary) 2.dp else 0.5.dp,
                        color = if (isPrimary) MaterialTheme.colorScheme.onSurface else colors.border,
                        shape = coverShape,
                    ),
            )
            StarBadge(
                isPrimary = isPrimary,
                onClick = onSelectPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (isOriginal) "${item.sourceName} · ${MergeText.Original}" else item.sourceName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            MetaLine(label = MergeText.Author, value = item.author)
            MetaLine(label = MergeText.Artist, value = item.artist)
            Spacer(Modifier.height(4.dp))
            Text(
                text = MergeText.chapterStats(item.readChapters, item.totalChapters),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ChapterBar(read = item.readChapters, total = item.totalChapters, maxTotal = maxTotal)
        }

        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Clear, contentDescription = MergeText.Remove)
            }
        }
    }
}

@Composable
private fun StarBadge(isPrimary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val container = if (isPrimary) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f)
    val content = if (isPrimary) MaterialTheme.colorScheme.onPrimary else Color.White
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.RadioButton, onClickLabel = MergeText.SetAsInfo, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = MergeText.SetAsInfo,
            tint = content,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun MetaLine(label: String, value: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: "—",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** Bright part is read progress, dim part is total, both scaled to the largest total in the group. */
@Composable
private fun ChapterBar(read: Int?, total: Int?, maxTotal: Int) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val totalFraction = fraction(total, maxTotal)
    val readFraction = fraction(read, maxTotal)

    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(onSurface.copy(alpha = 0.12f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(totalFraction)
                .background(onSurface.copy(alpha = 0.30f)),
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(readFraction)
                .background(onSurface.copy(alpha = 0.95f)),
        )
    }
}

private fun fraction(value: Int?, max: Int): Float =
    if (value == null || max <= 0) 0f else (value.toFloat() / max).coerceIn(0f, 1f)

private fun chapterDifference(items: List<MergeCardItem>): String? {
    val counted = items.filter { it.totalChapters != null }
    if (counted.size < 2) return null
    val most = counted.maxBy { it.totalChapters ?: 0 }
    val least = counted.minBy { it.totalChapters ?: 0 }
    val diff = (most.totalChapters ?: 0) - (least.totalChapters ?: 0)
    return if (diff == 0) {
        MergeText.SameChapterCount
    } else {
        MergeText.moreChapters(most.sourceName, diff, least.sourceName)
    }
}