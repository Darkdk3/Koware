package eu.kanade.presentation.reader.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
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
    val state = rememberLazyListState(
        chapters?.indexOfFirst { it == currentChapter }?.coerceAtLeast(0) ?: 0,
    )

    AdaptiveSheet(
        onDismissRequest = onDismiss,
    ) {
        LazyColumn(
            state = state,
            modifier = Modifier.heightIn(min = 200.dp, max = 500.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            items(
                items = chapters ?: emptyList(),
                key = { chapter -> "chapter-${chapter.chapter.id}-${chapter.chapter.name.hashCode()}" },
            ) { readerChapter ->
                val isCurrent = readerChapter == currentChapter
                val isRead = readerChapter.chapter.read

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 1.dp),
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Chapter indicator dot
                        val indicatorColor = when {
                            isCurrent -> MaterialTheme.colorScheme.primary
                            isRead -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        }
                        androidx.compose.material3.Surface(
                            modifier = Modifier.size(10.dp),
                            color = indicatorColor,
                            shape = androidx.compose.foundation.shape.CircleShape,
                        )
                        Spacer(modifier = Modifier.width(12.dp))

                        // Chapter info
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = readerChapter.chapter.name ?: "Unknown",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isRead) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                            )
                            readerChapter.chapter.scanlator?.let { scanlator ->
                                Text(
                                    text = scanlator,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    maxLines = 1,
                                )
                            }
                        }

                        // Current badge
                        if (isCurrent) {
                            Text(
                                text = "Current",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else if (isRead) {
                            Icon(
                                imageVector = Icons.Outlined.Bookmark,
                                contentDescription = "Read",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
