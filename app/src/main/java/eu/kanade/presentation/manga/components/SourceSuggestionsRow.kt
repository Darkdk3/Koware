// FILE: app/src/main/java/eu/kanade/presentation/manga/components/SourceSuggestionsRow.kt
// (new file, alongside MangaActionRow.kt, ExpandableMangaDescription.kt, etc.)

package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.source.model.SManga

/**
 * "More from this source" row on the novel details screen — other listings pulled
 * straight from the current novel's source (getLatestUpdates/getPopularManga),
 * not yet inserted into the local library. Tapping one triggers insert-then-navigate,
 * same as the Discover tab's card tap.
 */
@Composable
fun SourceSuggestionsRow(
    suggestions: List<SManga>,
    onSuggestionClick: (SManga) -> Unit,
) {
    if (suggestions.isEmpty()) return

    Text(
        text = "More from this source", // plain string - no new string resource needed
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(suggestions, key = { it.url }) { manga ->
            SourceSuggestionCard(manga = manga, onClick = { onSuggestionClick(manga) })
        }
    }
}

@Composable
private fun SourceSuggestionCard(
    manga: SManga,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(112.dp),
    ) {
        AsyncImage(
            model = manga.thumbnail_url,
            contentDescription = manga.title,
            modifier = Modifier.aspectRatio(2f / 3f),
            contentScale = ContentScale.Crop,
        )
        Text(
            text = manga.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(6.dp),
        )
    }
}
