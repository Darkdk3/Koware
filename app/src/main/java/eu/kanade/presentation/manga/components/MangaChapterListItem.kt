package eu.kanade.presentation.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileDownloadOff
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.download.model.Download
import me.saket.swipe.SwipeableActionsBox
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.novel.TDMR
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.selectedBackground

@Composable
fun MangaChapterListItem(
    title: String,
    date: String?,
    readProgress: String?,
    scanlator: String?,
    read: Boolean,
    bookmark: Boolean,
    hasTranslation: Boolean,
    selected: Boolean,
    downloadIndicatorEnabled: Boolean,
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDownloadClick: ((ChapterDownloadAction) -> Unit)?,
    onChapterSwipe: (LibraryPreferences.ChapterSwipeAction) -> Unit,
    modifier: Modifier = Modifier,
    // --- new, all default so every existing (legacy) call site compiles unchanged ---
    /** Drives the redesigned badge/strikethrough look. False preserves the exact original layout. */
    modernStyle: Boolean = false,
    /** Bare chapter number (e.g. "16"), only used for the modern-style numbered badge. */
    chapterNumber: String? = null,
    /**
     * 0-100 read percentage. ONLY meaningful for novels - a manga chapter's lastPageRead is a
     * page index, not a percentage, so it can't drive an accurate bar without also knowing the
     * total page count. Leave this null for manga chapters (see MangaScreen.kt call site).
     */
    readProgressPercent: Int? = null,
) {
    val start = getSwipeAction(
        action = chapterSwipeStartAction,
        read = read,
        bookmark = bookmark,
        downloadState = downloadStateProvider(),
        background = MaterialTheme.colorScheme.primaryContainer,
        onSwipe = { onChapterSwipe(chapterSwipeStartAction) },
    )
    val end = getSwipeAction(
        action = chapterSwipeEndAction,
        read = read,
        bookmark = bookmark,
        downloadState = downloadStateProvider(),
        background = MaterialTheme.colorScheme.primaryContainer,
        onSwipe = { onChapterSwipe(chapterSwipeEndAction) },
    )

    SwipeableActionsBox(
        modifier = Modifier.clipToBounds(),
        startActions = listOfNotNull(start),
        endActions = listOfNotNull(end),
        swipeThreshold = swipeActionThreshold,
        backgroundUntilSwipeThreshold = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(
            modifier = modifier
                .selectedBackground(selected)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
        ) {
            if (modernStyle) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (!read) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = chapterNumber.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (!read) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            LocalContentColor.current.copy(alpha = DISABLED_ALPHA)
                        },
                    )
                }
                Spacer(Modifier.width(10.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    var textHeight by remember { mutableIntStateOf(0) }
                    // Modern style shows unread state via the badge + row tint above instead of this dot
                    if (!read && !modernStyle) {
                        Icon(
                            imageVector = Icons.Filled.Circle,
                            contentDescription = stringResource(MR.strings.unread),
                            modifier = Modifier
                                .height(8.dp)
                                .padding(end = 4.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (bookmark) {
                        Icon(
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = stringResource(MR.strings.action_filter_bookmarked),
                            modifier = Modifier
                                .sizeIn(maxHeight = with(LocalDensity.current) { textHeight.toDp() - 2.dp }),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (hasTranslation) {
                        Icon(
                            imageVector = Icons.Outlined.Translate,
                            contentDescription = stringResource(TDMR.strings.action_translate),
                            modifier = Modifier
                                .sizeIn(maxHeight = with(LocalDensity.current) { textHeight.toDp() - 2.dp }),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.let {
                            if (modernStyle && read) {
                                it.copy(textDecoration = TextDecoration.LineThrough)
                            } else {
                                it
                            }
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { textHeight = it.size.height },
                        color = LocalContentColor.current.copy(alpha = if (read) DISABLED_ALPHA else 1f),
                    )
                }

                Row {
                    val subtitleStyle = MaterialTheme.typography.bodySmall
                        .merge(
                            color = LocalContentColor.current
                                .copy(alpha = if (read) DISABLED_ALPHA else SECONDARY_ALPHA),
                        )
                    ProvideTextStyle(value = subtitleStyle) {
                        if (date != null) {
                            Text(
                                text = date,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (readProgress != null || scanlator != null) DotSeparatorText()
                        }
                        if (readProgress != null) {
                            Text(
                                text = readProgress,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = LocalContentColor.current.copy(alpha = DISABLED_ALPHA),
                            )
                            if (scanlator != null) DotSeparatorText()
                        }
                        if (scanlator != null) {
                            Text(
                                text = scanlator,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                if (modernStyle && readProgressPercent != null) {
                    LinearProgressIndicator(
                        progress = { (readProgressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .width(48.dp)
                            .height(3.dp)
                            .clip(MaterialTheme.shapes.extraSmall),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }

            ChapterDownloadIndicator(
                enabled = downloadIndicatorEnabled,
                modifier = Modifier.padding(start = 4.dp),
                downloadStateProvider = downloadStateProvider,
                downloadProgressProvider = downloadProgressProvider,
                onClick = { onDownloadClick?.invoke(it) },
            )
        }
    }
}

private fun getSwipeAction(
    action: LibraryPreferences.ChapterSwipeAction,
    read: Boolean,
    bookmark: Boolean,
    downloadState: Download.State,
    background: Color,
    onSwipe: () -> Unit,
): me.saket.swipe.SwipeAction? {
    return when (action) {
        LibraryPreferences.ChapterSwipeAction.ToggleRead -> swipeAction(
            icon = if (!read) Icons.Outlined.Done else Icons.Outlined.RemoveDone,
            background = background,
            isUndo = read,
            onSwipe = onSwipe,
        )
        LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> swipeAction(
            icon = if (!bookmark) Icons.Outlined.BookmarkAdd else Icons.Outlined.BookmarkRemove,
            background = background,
            isUndo = bookmark,
            onSwipe = onSwipe,
        )
        LibraryPreferences.ChapterSwipeAction.Download -> swipeAction(
            icon = when (downloadState) {
                Download.State.NOT_DOWNLOADED, Download.State.ERROR -> Icons.Outlined.Download
                Download.State.QUEUE, Download.State.DOWNLOADING -> Icons.Outlined.FileDownloadOff
                Download.State.DOWNLOADED -> Icons.Outlined.Delete
            },
            background = background,
            onSwipe = onSwipe,
        )
        LibraryPreferences.ChapterSwipeAction.Disabled -> null
    }
}

private fun swipeAction(
    onSwipe: () -> Unit,
    icon: ImageVector,
    background: Color,
    isUndo: Boolean = false,
): me.saket.swipe.SwipeAction {
    return me.saket.swipe.SwipeAction(
        icon = {
            Icon(
                modifier = Modifier.padding(16.dp),
                imageVector = icon,
                tint = contentColorFor(background),
                contentDescription = null,
            )
        },
        background = background,
        onSwipe = onSwipe,
        isUndo = isUndo,
    )
}

private val swipeActionThreshold = 56.dp
