package eu.kanade.presentation.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.library.components.MangaComfortableGridItem
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover as MangaCoverModel

/**
 * "More from this source" section on the manga details screen.
 *
 * - `suggestions == null`  -> loading: shows a labeled skeleton row.
 * - `suggestions` empty    -> loaded, nothing found: the section hides completely.
 *
 * IMPORTANT: callers must pass the nullable value straight through. Passing
 * `state.sourceSuggestions.orEmpty()` turns "loading" into "empty" and makes the
 * whole section disappear until the fetch finishes.
 *
 * @param currentManga when set, the manga being viewed is removed from its own suggestions
 * (matched by source + url, since suggestion ids may not match the library entry).
 */
@Composable
fun SourceSuggestionsRow(
    suggestions: List<Manga>?,
    onSuggestionClick: (Manga) -> Unit,
    title: String = "More from this source",
    onMoreClicked: (() -> Unit)? = null,
    suggestionCount: Int = 0,
    currentManga: Manga? = null,
) {
    // Loading state: show skeleton row while suggestions are being fetched
    if (suggestions == null) {
        SourceSuggestionsLoadingRow(title = title)
        return
    }

    val visible = remember(suggestions, currentManga) {
        if (currentManga == null) {
            suggestions
        } else {
            suggestions.filterNot {
                it.source == currentManga.source && it.url == currentManga.url
            }
        }
    }

    // Empty state: loaded but nothing found — hide the section entirely
    if (visible.isEmpty()) return

    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 12.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (suggestionCount > 0) {
                Text(
                    text = "${suggestionCount.coerceAtMost(visible.size)} titles",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onMoreClicked != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            RoundedCornerShape(20.dp),
                        )
                        .clickable(onClick = onMoreClicked)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "More",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(12.dp),
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(visible, key = { "${it.source}_${it.id}_${it.url}" }) { manga ->
                Box(modifier = Modifier.width(110.dp)) {
                    MangaComfortableGridItem(
                        isSelected = false,
                        title = manga.title,
                        coverData = MangaCoverModel(
                            mangaId = manga.id,
                            sourceId = manga.source,
                            isMangaFavorite = manga.favorite,
                            url = manga.thumbnailUrl,
                            lastModified = manga.coverLastModified,
                        ),
                        coverBadgeStart = {},
                        coverBadgeEnd = {},
                        onLongClick = {},
                        onClick = { onSuggestionClick(manga) },
                        onClickContinueReading = null,
                        titleMaxLines = 2,
                    )
                }
            }
        }
    }
}

/**
 * Shown while the source suggestions are being fetched. Renders the same header + a row
 * of placeholder cover rectangles so the section doesn't pop in abruptly.
 */
@Composable
private fun SourceSuggestionsLoadingRow(title: String = "More from this source") {
    Column {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            CircularProgressIndicator(
                modifier = Modifier
                    .width(14.dp)
                    .height(14.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Finding titles…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(5) {
                Column(modifier = Modifier.width(110.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(MangaCover.Book.ratio)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .height(12.dp)
                            .width(70.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    )
                }
            }
        }
    }
}
