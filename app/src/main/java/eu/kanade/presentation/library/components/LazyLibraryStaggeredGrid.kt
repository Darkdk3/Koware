package eu.kanade.presentation.library.components

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.presentation.core.util.plus

/**
 * Masonry-style counterpart to [LazyLibraryGrid], used only when freeform cover ratios are on and
 * the user has opted into the staggered layout - see LibraryPreferences.freeformCoverGridStaggered.
 *
 * Unlike [LazyLibraryGrid] (backed by FastScrollLazyVerticalGrid), this has no fast-scroll thumb:
 * LazyVerticalStaggeredGrid is a different scope type than LazyVerticalGrid, and FastScrollLazyVerticalGrid's
 * source wasn't available to build a staggered-compatible equivalent against.
 */
@Composable
internal fun LazyLibraryStaggeredGrid(
    modifier: Modifier = Modifier,
    columns: Int,
    contentPadding: PaddingValues,
    content: LazyStaggeredGridScope.() -> Unit,
) {
    LazyVerticalStaggeredGrid(
        columns = if (columns == 0) StaggeredGridCells.Adaptive(128.dp) else StaggeredGridCells.Fixed(columns),
        modifier = modifier,
        contentPadding = contentPadding + PaddingValues(8.dp),
        verticalItemSpacing = CommonMangaItemDefaults.GridVerticalSpacer,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
            CommonMangaItemDefaults.GridHorizontalSpacer,
        ),
        content = content,
    )
}

/**
 * Staggered-grid equivalent of [loadMoreSentinel] for LazyGridScope.
 */
internal fun LazyStaggeredGridScope.loadMoreSentinel(loadKey: Long, onLoadMore: (() -> Unit)?) {
    if (onLoadMore == null) return
    item(
        span = StaggeredGridItemSpan.FullLine,
        contentType = "library_load_more",
    ) {
        LaunchedEffect(loadKey) { onLoadMore() }
    }
}

/**
 * Staggered-grid equivalent of [globalSearchItem] for LazyGridScope.
 */
internal fun LazyStaggeredGridScope.globalSearchItem(
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    if (!searchQuery.isNullOrEmpty()) {
        item(
            span = StaggeredGridItemSpan.FullLine,
            contentType = "library_global_search_item",
        ) {
            GlobalSearchItem(
                searchQuery = searchQuery,
                onClick = onGlobalSearchClicked,
            )
        }
    }
}
