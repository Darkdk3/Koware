// FILE: app/src/main/java/eu/kanade/tachiyomi/ui/browse/discover/DiscoverTab.kt

package eu.kanade.tachiyomi.ui.browse.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.TabContent
import tachiyomi.i18n.novel.TDMR // TODO: add a "label_discover" string res here (or wherever
                                  // label_novel_extensions lives) — using it directly below
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Matches the novelExtensionsTab / extensionsTab factory pattern used in BrowseTab.kt.
 * Register by adding `discoverTab(discoverViewModel)` to BrowseTab's `tabs` list.
 */
@Composable
fun discoverTab(
    viewModel: DiscoverViewModel,
): TabContent {
    val state by viewModel.state.collectAsState()

    return TabContent(
        titleRes = TDMR.strings.label_novel_extensions, // TODO: swap for a real
                                                          // "label_discover" string res
        searchEnabled = false,
        content = { contentPadding, _ ->
            DiscoverScreenContent(
                items = state.items,
                isLoading = state.isLoading,
                contentPadding = contentPadding,
                onMangaClick = { entry -> /* TODO: navigate to MangaScreen for entry.manga/entry.source */ },
            )
        },
    )
}

@Composable
private fun DiscoverScreenContent(
    items: List<DiscoverEntry>,
    isLoading: Boolean,
    contentPadding: PaddingValues,
    onMangaClick: (DiscoverEntry) -> Unit,
) {
    if (isLoading) {
        // TODO: reuse your existing LoadingScreen composable
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.manga.url }) { entry ->
            DiscoverMangaCard(entry = entry, onClick = { onMangaClick(entry) })
        }
    }
}

@Composable
private fun DiscoverMangaCard(
    entry: DiscoverEntry,
    onClick: () -> Unit,
) {
    val manga = entry.manga

    Card(
        onClick = onClick,
        modifier = Modifier.padding(2.dp),
    ) {
        AsyncImage(
            model = manga.thumbnail_url, // real cover art, same field Library/Extensions use
            contentDescription = manga.title,
            modifier = Modifier.aspectRatio(2f / 3f),
            contentScale = ContentScale.Crop,
        )
        Text(
            text = manga.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            modifier = Modifier.padding(8.dp),
        )
        Text(
            text = entry.source.name,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
