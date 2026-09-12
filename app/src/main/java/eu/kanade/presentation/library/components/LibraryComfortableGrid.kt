package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.domain.ui.model.UiStyle
import eu.kanade.tachiyomi.ui.library.LibraryItem
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.MangaCover

@Composable
internal fun LibraryComfortableGrid(
    items: List<LibraryItem>,
    columns: Int,
    contentPadding: PaddingValues,
    selection: Set<Long>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
    titleMaxLines: Int = 2,
    showAuthorArtistSubtitle: Boolean = false,
    freeformCoverGrid: Boolean = false,
    freeformCoverGridStaggered: Boolean = false,
    uiStyle: UiStyle = UiStyle.LEGACY,
    onLoadMore: (() -> Unit)? = null,
    loadMoreKey: Long = 0,
) {
    // Staggered mode only makes sense - and is only offered in settings - when freeform is on.
    val useStaggeredGrid = freeformCoverGrid && freeformCoverGridStaggered

    if (useStaggeredGrid) {
        LazyLibraryStaggeredGrid(
            modifier = Modifier.fillMaxSize(),
            columns = columns,
            contentPadding = contentPadding,
            uiStyle = uiStyle,
        ) {
            globalSearchItem(searchQuery, onGlobalSearchClicked)

            items(
                items = items,
                contentType = { "library_comfortable_grid_item" },
            ) { libraryItem ->
                LibraryComfortableGridCell(
                    libraryItem = libraryItem,
                    selection = selection,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onClickContinueReading = onClickContinueReading,
                    titleMaxLines = titleMaxLines,
                    showAuthorArtistSubtitle = showAuthorArtistSubtitle,
                    freeformCoverGrid = freeformCoverGrid,
                    uiStyle = uiStyle,
                )
            }

            loadMoreSentinel(loadMoreKey, onLoadMore)
        }
    } else {
        LazyLibraryGrid(
            modifier = Modifier.fillMaxSize(),
            columns = columns,
            contentPadding = contentPadding,
            uiStyle = uiStyle,
        ) {
            globalSearchItem(searchQuery, onGlobalSearchClicked)

            items(
                items = items,
                contentType = { "library_comfortable_grid_item" },
            ) { libraryItem ->
                LibraryComfortableGridCell(
                    libraryItem = libraryItem,
                    selection = selection,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onClickContinueReading = onClickContinueReading,
                    titleMaxLines = titleMaxLines,
                    showAuthorArtistSubtitle = showAuthorArtistSubtitle,
                    freeformCoverGrid = freeformCoverGrid,
                    uiStyle = uiStyle,
                )
            }

            loadMoreSentinel(loadMoreKey, onLoadMore)
        }
    }
}

/**
 * The actual per-manga cell, shared between the standard and staggered grid branches above so the
 * two scopes (LazyGridScope vs LazyStaggeredGridScope) don't duplicate this logic.
 */
@Composable
private fun LibraryComfortableGridCell(
    libraryItem: LibraryItem,
    selection: Set<Long>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    titleMaxLines: Int,
    showAuthorArtistSubtitle: Boolean,
    freeformCoverGrid: Boolean,
    uiStyle: UiStyle,
) {
    val manga = libraryItem.libraryManga.manga
    // Author/artist are deduped when identical (common for doujin/self-published works) so the
    // subtitle doesn't repeat the same name twice.
    val authorArtist = if (showAuthorArtistSubtitle) {
        if (manga.author == manga.artist || manga.artist.isNullOrBlank()) {
            manga.author?.trim().orEmpty()
        } else {
            listOfNotNull(
                manga.author?.trim()?.takeIf { it.isNotBlank() },
                manga.artist?.trim()?.takeIf { it.isNotBlank() },
            ).joinToString(", ")
        }
    } else {
        null
    }
    val freeformCoverRatio = rememberCoverRatio(manga = manga, enabled = freeformCoverGrid)
    val coverData = MangaCover(
        mangaId = manga.id,
        sourceId = manga.source,
        isMangaFavorite = manga.favorite,
        url = manga.thumbnailUrl,
        lastModified = manga.coverLastModified,
    )
    val onClickContinue = if (onClickContinueReading != null && libraryItem.unreadCount > 0) {
        { onClickContinueReading(libraryItem.libraryManga) }
    } else {
        null
    }

    if (uiStyle == UiStyle.MODERN) {
        MangaModernGridItem(
            isSelected = manga.id in selection,
            title = manga.title,
            coverData = coverData,
            coverBadgeStart = {
                DownloadsBadge(count = libraryItem.badges.downloadCount)
            },
            coverBadgeEnd = {
                LanguageBadge(
                    isLocal = libraryItem.badges.isLocal,
                    sourceLanguage = libraryItem.badges.sourceLanguage,
                )
            },
            unreadCount = libraryItem.badges.unreadCount,
            onLongClick = { onLongClick(libraryItem.libraryManga) },
            onClick = { onClick(libraryItem.libraryManga) },
            onClickContinueReading = onClickContinue,
            titleMaxLines = titleMaxLines,
            authorArtist = authorArtist,
            showAuthorArtistSubtitle = showAuthorArtistSubtitle,
            freeformCoverRatio = freeformCoverRatio,
        )
    } else {
        MangaComfortableGridItem(
            isSelected = manga.id in selection,
            title = manga.title,
            coverData = coverData,
            coverBadgeStart = {
                DownloadsBadge(count = libraryItem.badges.downloadCount)
                UnreadBadge(count = libraryItem.badges.unreadCount)
            },
            coverBadgeEnd = {
                LanguageBadge(
                    isLocal = libraryItem.badges.isLocal,
                    sourceLanguage = libraryItem.badges.sourceLanguage,
                )
            },
            onLongClick = { onLongClick(libraryItem.libraryManga) },
            onClick = { onClick(libraryItem.libraryManga) },
            onClickContinueReading = onClickContinue,
            titleMaxLines = titleMaxLines,
            authorArtist = authorArtist,
            showAuthorArtistSubtitle = showAuthorArtistSubtitle,
            freeformCoverRatio = freeformCoverRatio,
        )
    }
}
