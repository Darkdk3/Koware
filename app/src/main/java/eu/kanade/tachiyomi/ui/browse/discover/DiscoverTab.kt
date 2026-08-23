// FILE: app/src/main/java/eu/kanade/tachiyomi/ui/browse/discover/DiscoverTab.kt

package eu.kanade.tachiyomi.ui.browse.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.novel.TDMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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

    LaunchedEffect(state.pendingMangaId) {
        val id = state.pendingMangaId
        if (id != null) {
            navigator.push(MangaScreen(mangaId = id))
            viewModel.consumePendingNavigation()
        }
    }

    return TabContent(
        titleRes = TDMR.strings.label_discover,
        searchEnabled = false,
        actions = listOf(
            AppBar.Action(
                title = "Refresh",
                icon = Icons.Outlined.Refresh,
                onClick = { viewModel.loadDiscoverFeed() },
            ),
        ),
        content = { contentPadding, _ ->
            DiscoverScreenContent(
                items = state.items,
                isLoading = state.isLoading,
                isLoadingMore = state.isLoadingMore,
                browseMode = state.browseMode,
                contentPadding = contentPadding,
                onMangaClick = viewModel::openEntry,
                onBrowseModeChange = viewModel::setBrowseMode,
                onLoadMore = viewModel::loadMore,
            )
        },
    )
}

@Composable
private fun DiscoverScreenContent(
    items: List<DiscoverEntry>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    browseMode: DiscoverBrowseMode,
    contentPadding: PaddingValues,
    onMangaClick: (DiscoverEntry) -> Unit,
    onBrowseModeChange: (DiscoverBrowseMode) -> Unit,
    onLoadMore: () -> Unit,
) {
    val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
    val portraitColumns by libraryPreferences.portraitColumns.collectAsState()
    val gridState = rememberLazyGridState()

    // Fire loadMore once the user scrolls near the bottom of what's currently loaded.
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            items.isNotEmpty() && lastVisible >= items.size - 6
        }
    }
    LaunchedEffect(shouldLoadMore, isLoading, isLoadingMore) {
        if (shouldLoadMore && !isLoading && !isLoadingMore) {
            onLoadMore()
        }
    }

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
                text = "No novel sources pinned yet — long-press a source in the Sources tab to pin it, and it'll start feeding Discover. Tap refresh above once you have.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
        BrowseModeToggle(
            selected = browseMode,
            onSelect = onBrowseModeChange,
        )

        LazyVerticalGrid(
            state = gridState,
            columns = if (portraitColumns > 0) {
                GridCells.Fixed(portraitColumns)
            } else {
                GridCells.Adaptive(minSize = 130.dp)
            },
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { "${it.source.id}-${it.manga.url}" }) { entry ->
                DiscoverMangaCard(entry = entry, onClick = { onMangaClick(entry) })
            }

            if (isLoadingMore) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseModeToggle(
    selected: DiscoverBrowseMode,
    onSelect: (DiscoverBrowseMode) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == DiscoverBrowseMode.LATEST,
            onClick = { onSelect(DiscoverBrowseMode.LATEST) },
            label = { Text("Latest") },
        )
        FilterChip(
            selected = selected == DiscoverBrowseMode.POPULAR,
            onClick = { onSelect(DiscoverBrowseMode.POPULAR) },
            label = { Text("Popular") },
        )
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
            maxLines = 3, // was 2 - long titles were getting cut off awkwardly
            modifier = Modifier.padding(8.dp),
        )
        Text(
            text = entry.source.name,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
