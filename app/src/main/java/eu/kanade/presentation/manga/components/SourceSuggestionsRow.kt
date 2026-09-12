// FILE: app/src/main/java/eu/kanade/presentation/manga/components/SourceSuggestionsRow.kt

package eu.kanade.presentation.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.UiStyle
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * "More from this source" row on the novel details screen - novels found by searching
 * the source with the current novel's title, for real similarity rather than just
 * generic popular titles. Uses the same MangaComfortableGridItem card as Library/Sources/
 * Discover for visual consistency. Renders nothing while loading or if empty - secondary
 * content, not worth a loading spinner blocking the rest of the screen.
 *
 * In [UiStyle.MODERN] a "More ›" pill is shown at the header's trailing edge that navigates
 * to the full recommendations screen where source suggestions, AI picks, and tracker
 * recommendations are stacked on one scrollable page.
 */
@Composable
fun SourceSuggestionsRow(
    suggestions: List<Manga>?,
    onSuggestionClick: (Manga) -> Unit,
    onMoreClicked: () -> Unit = {},
    suggestionCount: Int = 0,
) {
    if (suggestions.isNullOrEmpty()) return

    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val uiStyle by uiPreferences.uiStyle.collectAsState()

    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "More from this source",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )

        if (uiStyle == UiStyle.MODERN && suggestionCount > 0) {
            Text(
                text = "${suggestionCount.coerceAtMost(suggestions.size)} novels",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (uiStyle == UiStyle.MODERN) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        RoundedCornerShape(20.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(20.dp),
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
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(14.dp),
                )
            }
        }
    }

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
