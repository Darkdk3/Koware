package eu.kanade.presentation.reader.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                key = { chapter -> chapter.chapter.id ?: 0L },
            ) { readerChapter ->
                val isCurrent = readerChapter == currentChapter
                val readColor = if (readerChapter.chapter.read) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                val bgColor = if (isCurrent) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .clickable { onChapterClick(readerChapter) },
                    color = bgColor,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = readerChapter.chapter.name ?: "Unknown",
                                style = MaterialTheme.typography.bodyLarge,
                                color = readColor,
                                maxLines = 2,
                            )
                            readerChapter.chapter.scanlator?.let { scanlator ->
                                Text(
                                    text = scanlator,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    maxLines = 1,
                                )
                            }
                        }
                        if (isCurrent) {
                            Text(
                                text = "Current",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else if (readerChapter.chapter.bookmark) {
                            Icon(
                                imageVector = Icons.Outlined.Bookmark,
                                contentDescription = "Bookmarked",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
