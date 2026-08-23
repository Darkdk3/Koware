// FILE: app/src/main/java/eu/kanade/tachiyomi/ui/browse/discover/DiscoverTab.kt

package eu.kanade.tachiyomi.ui.browse.discover

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.library.components.MangaComfortableGridItem
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.MangaCover
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

    val columns = if (portraitColumns > 0) {
        GridCells.Fixed(portraitColumns)
    } else {
        GridCells.Adaptive(minSize = 130.dp)
    }

    // Toggle stays visible in every state - loading, empty, or populated - so switching
    // modes is always available rather than disappearing while content loads.
    Column(modifier = Modifier.fillMaxSize()) {
        BrowseModeToggle(selected = browseMode, onSelect = onBrowseModeChange)

        when {
            isLoading -> DiscoverLoadingGrid(columns = columns, contentPadding = contentPadding)

            items.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No novel sources pinned yet — long-press a source in the Sources tab to pin it, and it'll start feeding Discover. Tap refresh above once you have.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            else -> LazyVerticalGrid(
                state = gridState,
                columns = columns,
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.manga.id }) { entry ->
                    MangaComfortableGridItem(
                        isSelected = false,
                        title = entry.manga.title,
                        coverData = MangaCover(
                            mangaId = entry.manga.id,
                            sourceId = entry.manga.source,
                            isMangaFavorite = entry.manga.favorite,
                            url = entry.manga.thumbnailUrl,
                            lastModified = entry.manga.coverLastModified,
                        ),
                        coverBadgeStart = {},
                        coverBadgeEnd = {},
                        onLongClick = {},
                        onClick = { onMangaClick(entry) },
                        onClickContinueReading = null,
                        titleMaxLines = 3,
                    )
                }

                if (isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
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
}

@Composable
private fun BrowseModeToggle(
    selected: DiscoverBrowseMode,
    onSelect: (DiscoverBrowseMode) -> Unit,
) {
    Row(
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

/**
 * Animated shimmer brush - a soft highlight band that sweeps diagonally across
 * placeholder shapes on a loop, the standard "skeleton loading" effect.
 */
@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translate - 300f, translate - 300f),
        end = Offset(translate, translate),
    )
}

@Composable
private fun DiscoverLoadingGrid(
    columns: GridCells,
    contentPadding: PaddingValues,
) {
    val brush = shimmerBrush()

    LazyVerticalGrid(
        columns = columns,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false,
    ) {
        items(18) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(brush),
                )
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth(0.8f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush),
                )
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth(0.5f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush),
                )
            }
        }
    }
}