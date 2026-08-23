// FILE: app/src/main/java/eu/kanade/presentation/manga/components/SourceSuggestionsRow.kt

package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.library.components.MangaComfortableGridItem
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover

/**
 * "More from this source" row on the novel details screen - novels found by searching
 * the source with the current novel's title, for real similarity rather than just
 * generic popular titles. Uses the same MangaComfortableGridItem card as Library/Sources/
 * Discover for visual consistency. Renders nothing while loading or if empty - secondary
 * content, not worth a loading spinner blocking the rest of the screen.
 */
@Composable
fun SourceSuggestionsRow(
    suggestions: List<Manga>?,
    onSuggestionClick: (Manga) -> Unit,
) {
    if (suggestions.isNullOrEmpty()) return

    Text(
        text = "More from this source",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(suggestions, key = { it.id }) { manga ->
            androidx.compose.foundation.layout.Box(modifier = Modifier.width(110.dp)) {
                MangaComfortableGridItem(
                    isSelected = false,
                    title = manga.title,
                    coverData = MangaCover(
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
