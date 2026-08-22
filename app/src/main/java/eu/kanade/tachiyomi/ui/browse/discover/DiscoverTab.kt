
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import eu.kanade.tachiyomi.ui.manga.MangaScreen // TODO: confirm this is your real
                                                  // novel/manga details+reader entry screen,
                                                  // and confirm its constructor parameter
                                                  // name really is `mangaId` (a Long)
import tachiyomi.domain.library.service.LibraryPreferences // TODO: confirm this exact
                                                              // package/name — standard
                                                              // Tachiyomi/Mihon location for
                                                              // the "columns per row" library
                                                              // display setting
import tachiyomi.i18n.novel.TDMR
import tachiyomi.presentation.core.i18n.stringResource
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

    // Once openEntry() resolves a tapped card to a real local manga id, navigate
    // to it, then clear the pending id so re-composition doesn't navigate again.
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
                contentPadding = contentPadding,
                onMangaClick = viewModel::openEntry,
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
    // Mirrors your Library's "items per row" setting. 0 = auto/adaptive in the
    // standard Tachiyomi library-columns preference, matching that convention here.
    val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
    val portraitColumns by libraryPreferences.portraitColumns().changes()
        .collectAsState(initial = libraryPreferences.portraitColumns().get())

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

    LazyVerticalGrid(
        columns = if (portraitColumns > 0) {
            GridCells.Fixed(portraitColumns)
        } else {
            GridCells.Adaptive(minSize = 130.dp)
        },
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
