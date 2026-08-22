// FILE: app/src/main/java/eu/kanade/tachiyomi/ui/browse/discover/DiscoverTab.kt

package eu.kanade.tachiyomi.ui.browse.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen // TODO: confirm — see note below
import eu.kanade.tachiyomi.ui.manga.MangaScreen // TODO: confirm this is your real
                                                  // novel/manga details+reader entry screen
import tachiyomi.i18n.novel.TDMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * "Discover" tab, next to Sources/Extensions. Feed is driven by whichever novel
 * sources you've pinned in the Sources tab — long-press a source there to pin it.
 * Register by adding `discoverTab(discoverViewModel)` to BrowseTab's `tabs` list.
 */
@Composable
fun discoverTab(
    viewModel: DiscoverViewModel,
): TabContent {
    val state by viewModel.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow

    return TabContent(
        titleRes = TDMR.strings.label_discover,
        searchEnabled = false,
        content = { contentPadding, _ ->
            DiscoverScreenContent(
                items = state.items,
                isLoading = state.isLoading,
                contentPadding = contentPadding,
                onMangaClick = { entry ->
                    // Push the same details/reader screen the rest of the app uses when
                    // tapping a cover in Library/Extensions results. This assumes
                    // MangaScreen(mangaId, ...) or similar is how it's normally opened —
                    // confirm against how your Extensions search results navigate on tap,
                    // since that's the exact same action we want here.
                    navigator.push(
                        MangaScreen(
                            mangaId = 0L, // TODO: this entry isn't in the local DB yet since
                                          // it came straight from the source, not the library.
                                          // You likely need a "insert-or-get" step first —
                                          // e.g. call into your existing GetManga/NetworkToLocalManga
                                          // use-case with entry.source.id + entry.manga, the same
                                          // way BrowseSourceScreen's search results do when tapped,
                                          // then navigate with the resulting local ID.
                        ),
                    )
                },
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
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (items.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No novel sources pinned yet — long-press a source in the Sources tab to pin it, and it'll start feeding Discover.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    LazyVerticalGrid(
        // Adaptive: same idea as your Library grid — columns are computed from
        // available width instead of a fixed count, so the row count naturally
        // matches whatever grid density Library uses. 130dp is a reasonable
        // per-cover minimum width to start from; if your Library grid exposes
        // its own min-width/columns preference (many Mihon forks have a
        // "grid size" library setting), swap GridCells.Adaptive(130.dp) for
        // that same stored preference value here so the two screens agree exactly.
        columns = GridCells.Adaptive(minSize = 130.dp),
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
            model = manga.thumbnail_url,
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
