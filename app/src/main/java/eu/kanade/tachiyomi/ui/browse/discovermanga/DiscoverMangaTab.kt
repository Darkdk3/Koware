// FILE: app/src/main/java/eu/kanade/tachiyomi/ui/browse/discovermanga/DiscoverMangaTab.kt

package eu.kanade.tachiyomi.ui.browse.discovermanga

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
fun discoverMangaTab(
    viewModel: DiscoverMangaViewModel,
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
        titleRes = TDMR.strings.label_discover_manga,
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
                recommendations = state.recommendations,
                recommendationTopGenres = state.recommendationTopGenres,
                recommendationScores = state.recommendationScores,
                aiRecommendationMessage = state.aiRecommendationMessage,
                isLoadingRecommendations = state.isLoadingRecommendations,
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
    items: List<DiscoverMangaEntry>,
    recommendations: List<DiscoverMangaEntry>,
    recommendationTopGenres: List<String>,
    recommendationScores: Map<Long, Int>,
    aiRecommendationMessage: String?,
    isLoadingRecommendations: Boolean,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    browseMode: DiscoverMangaBrowseMode,
    contentPadding: PaddingValues,
    onMangaClick: (DiscoverMangaEntry) -> Unit,
    onBrowseModeChange: (DiscoverMangaBrowseMode) -> Unit,
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
        AiRecommendationsShelf(
            recommendations = recommendations,
            topGenres = recommendationTopGenres,
            scores = recommendationScores,
            isLoading = isLoadingRecommendations,
            message = aiRecommendationMessage,
            onMangaClick = onMangaClick,
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        BrowseModeToggle(selected = browseMode, onSelect = onBrowseModeChange)

        when {
            isLoading -> DiscoverLoadingGrid(columns = columns, contentPadding = contentPadding)

            items.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No manga sources pinned yet — long-press a source in the Sources tab to pin it, and it'll start feeding Discover. Tap refresh above once you have.",
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
                    Column {
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
                        Text(
                            text = entry.source.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
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
    selected: DiscoverMangaBrowseMode,
    onSelect: (DiscoverMangaBrowseMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == DiscoverMangaBrowseMode.LATEST,
            onClick = { onSelect(DiscoverMangaBrowseMode.LATEST) },
            label = { Text("Latest") },
        )
        FilterChip(
            selected = selected == DiscoverMangaBrowseMode.POPULAR,
            onClick = { onSelect(DiscoverMangaBrowseMode.POPULAR) },
            label = { Text("Popular") },
        )
    }
}

@Composable
private fun AiRecommendationsShelf(
    recommendations: List<DiscoverMangaEntry>,
    topGenres: List<String>,
    scores: Map<Long, Int>,
    isLoading: Boolean,
    message: String?,
    onMangaClick: (DiscoverMangaEntry) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = "AI recommendations",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "based on what you read most",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isLoading) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        when {
            isLoading && recommendations.isEmpty() -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(6) {
                        Column(modifier = Modifier.width(92.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(shimmerBrush()),
                            )
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .fillMaxWidth(0.9f)
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(shimmerBrush()),
                            )
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .fillMaxWidth(0.6f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(shimmerBrush()),
                            )
                        }
                    }
                }
            }
            recommendations.isNotEmpty() -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(recommendations, key = { "rec_${it.manga.id}" }) { entry ->
                        val heuristicMatch = remember(entry.manga.id, topGenres) {
                            matchScore(entry.manga.genre.orEmpty(), topGenres)
                        }
                        val match = scores[entry.manga.id] ?: heuristicMatch
                        Column(modifier = Modifier.width(92.dp)) {
                            Box {
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
                                    titleMaxLines = 2,
                                )
                                if (match != null) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        MaterialTheme.colorScheme.primary,
                                                        MaterialTheme.colorScheme.tertiary,
                                                    ),
                                                ),
                                            )
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = "$match%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                }
                            }
                            Text(
                                text = entry.source.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
            }
            else -> {
                Text(
                    text = message ?: "No AI recommendations right now - try refreshing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private fun matchScore(genres: List<String>, topGenres: List<String>): Int? {
    if (genres.isEmpty() || topGenres.isEmpty()) return null
    val profile = topGenres.toSet()
    val matched = genres.count { it in profile }
    if (matched == 0) return null
    return (matched * 200 / (genres.size + profile.size)).coerceIn(1, 100)
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
