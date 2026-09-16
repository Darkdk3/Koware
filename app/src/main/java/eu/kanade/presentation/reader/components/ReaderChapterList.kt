package eu.kanade.presentation.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import kotlinx.coroutines.flow.distinctUntilChanged
import tachiyomi.domain.manga.model.Manga

@Composable
fun ReaderChapterList(
    manga: Manga?,
    chapters: List<ReaderChapter>?,
    currentChapter: ReaderChapter?,
    onDismiss: () -> Unit,
    onChapterClick: (ReaderChapter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var scrollToCurrent by remember { mutableStateOf(false) }

    val filteredChapters = remember(chapters, searchQuery) {
        if (searchQuery.isBlank()) chapters
        else chapters?.filter {
            it.chapter.name?.contains(searchQuery, ignoreCase = true) == true ||
                it.chapter.scanlator?.contains(searchQuery, ignoreCase = true) == true
        }
    }

    val readCount = chapters?.count { it.chapter.read } ?: 0
    val totalCount = chapters?.size ?: 0

    val currentIndex = chapters?.indexOfFirst { it == currentChapter }?.coerceAtLeast(0) ?: 0
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = currentIndex,
    )

    LaunchedEffect(scrollToCurrent) {
        if (scrollToCurrent) {
            snapshotFlow { state.layoutInfo }
                .distinctUntilChanged()
                .collect {
                    state.animateScrollToItem(currentIndex)
                    scrollToCurrent = false
                }
        }
    }

    AdaptiveSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 300.dp, max = 600.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Chapters",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "$readCount of $totalCount read",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Find a chapter") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                ),
            )

            // Sort + Jump to current row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Oldest → newest",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { scrollToCurrent = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Jump to current",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                CircleShape,
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.onPrimary, CircleShape)
                                .align(Alignment.Center),
                        )
                    }
                }
            }

            // Chapter list
            LazyColumn(
                state = state,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                items(
                    items = filteredChapters ?: emptyList(),
                    key = { chapter -> "chapter-${chapter.chapter.id}-${chapter.chapter.name.hashCode()}" },
                ) { readerChapter ->
                    val isCurrent = readerChapter == currentChapter
                    val isRead = readerChapter.chapter.read

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onChapterClick(readerChapter) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Chapter indicator
                        ChapterIndicator(isCurrent = isCurrent, isRead = isRead)

                        // Chapter info
                        Column(modifier = Modifier.weight(1f)) {
                            // Chapter number label
                            val chapterNum = readerChapter.chapter.name?.let { name ->
                                Regex("(?i)chapter\\s*(\\d+)", RegexOption.IGNORE_CASE)
                                    .find(name)
                                    ?.groupValues
                                    ?.getOrNull(1)
                            }
                            if (chapterNum != null) {
                                Text(
                                    text = "CHAPTER $chapterNum",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isCurrent) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }

                            // Chapter name
                            Text(
                                text = readerChapter.chapter.name ?: "Unknown",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isCurrent || !isRead) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                                color = when {
                                    isCurrent -> MaterialTheme.colorScheme.onSurface
                                    isRead -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                },
                                maxLines = 2,
                                lineHeight = 20.sp,
                            )
                        }

                        // Timestamp / status indicator
                        if (isCurrent) {
                            Text(
                                text = "now",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (isRead) {
                            Text(
                                text = "read",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterIndicator(isCurrent: Boolean, isRead: Boolean) {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isCurrent) {
            // Filled circle with dot — current chapter
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.onPrimary, CircleShape)
                        .align(Alignment.Center),
                )
            }
        } else if (isRead) {
            // Checkmark circle — read
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        CircleShape,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Read",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.Center),
                )
            }
        } else {
            // Empty circle — unread
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            )
        }
    }
}
