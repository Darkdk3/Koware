
// FILE: app/src/main/java/eu/kanade/tachiyomi/ui/browse/discovermanga/DiscoverMangaViewModel.kt

package eu.kanade.tachiyomi.ui.browse.discovermanga

import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.jsplugin.JsPluginManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.isNovelSource
import kotlinx.coroutines.flow.update
import mihon.core.viewmodel.StateViewModel
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

enum class DiscoverMangaBrowseMode { LATEST, POPULAR }

/**
 * The manga here is already a real local database entry (converted via NetworkToLocalManga
 * as soon as it's fetched, not only when tapped) - same as how BrowseSourceScreen already
 * handles source listings. This gives Discover a real id for cover caching/consistent card
 * rendering, and makes tap-to-navigate trivial since the id is already known.
 */
data class DiscoverMangaEntry(
    val source: CatalogueSource,
    val manga: Manga,
)

data class DiscoverMangaScreenState(
    val items: List<DiscoverMangaEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasPinnedNovelSources: Boolean = true,
    val browseMode: DiscoverMangaBrowseMode = DiscoverMangaBrowseMode.LATEST,
    val pendingMangaId: Long? = null,
)

class DiscoverMangaViewModel(
    private val sourceManager: SourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val jsPluginManager: JsPluginManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
) : StateViewModel<DiscoverMangaScreenState>(DiscoverMangaScreenState()) {

    private data class SourcePageCursor(val nextPage: Int, val hasNextPage: Boolean)
    private val pageCursors = mutableMapOf<Long, SourcePageCursor>()

    init {
        loadDiscoverFeed()
    }

    fun setBrowseMode(mode: DiscoverMangaBrowseMode) {
        if (mode == state.value.browseMode) return
        mutableState.update { it.copy(browseMode = mode) }
        loadDiscoverFeed()
    }

    private fun pinnedNovelSources(): List<CatalogueSource> {
        val pinnedKeys = sourcePreferences.pinnedSources.get()
        return (sourceManager.getOnlineSources() + jsPluginManager.jsSources.value)
            .filterIsInstance<CatalogueSource>()
            .distinctBy { it.id }
            .filter { !it.isNovelSource() }
            .filter { it.supportsLatest }
            .filter { it.id.toString() in pinnedKeys }
    }

    fun loadDiscoverFeed() {
        viewModelScope.launchIO {
            mutableState.update { it.copy(isLoading = true) }
            pageCursors.clear()

            val sources = pinnedNovelSources()
            val mode = state.value.browseMode

            val perSourceLists = sources.map { source ->
                val page = runCatching { fetchPage(source, mode, page = 1) }.getOrNull()
                pageCursors[source.id] = SourcePageCursor(
                    nextPage = 2,
                    hasNextPage = page?.hasNextPage == true,
                )
                (page?.mangas ?: emptyList()).map { sManga ->
                    val localManga = networkToLocalManga(sManga.toDomainManga(source.id, isNovel = false))
                    DiscoverMangaEntry(source, localManga)
                }
            }
            val merged = interleave(perSourceLists)

            mutableState.update {
                it.copy(items = merged, isLoading = false, hasPinnedNovelSources = sources.isNotEmpty())
            }
        }
    }

    fun loadMore() {
        if (state.value.isLoading || state.value.isLoadingMore) return
        val sources = pinnedNovelSources().filter { pageCursors[it.id]?.hasNextPage == true }
        if (sources.isEmpty()) return

        viewModelScope.launchIO {
            mutableState.update { it.copy(isLoadingMore = true) }
            val mode = state.value.browseMode

            val newLists = sources.map { source ->
                val cursor = pageCursors[source.id]!!
                val page = runCatching { fetchPage(source, mode, cursor.nextPage) }.getOrNull()
                pageCursors[source.id] = SourcePageCursor(
                    nextPage = cursor.nextPage + 1,
                    hasNextPage = page?.hasNextPage == true,
                )
                (page?.mangas ?: emptyList()).map { sManga ->
                    val localManga = networkToLocalManga(sManga.toDomainManga(source.id, isNovel = false))
                    DiscoverMangaEntry(source, localManga)
                }
            }
            val appended = interleave(newLists)

            mutableState.update { it.copy(items = it.items + appended, isLoadingMore = false) }
        }
    }

    private suspend fun fetchPage(
        source: CatalogueSource,
        mode: DiscoverMangaBrowseMode,
        page: Int,
    ) = when (mode) {
        DiscoverMangaBrowseMode.LATEST -> source.getLatestUpdates(page)
        DiscoverMangaBrowseMode.POPULAR -> source.getPopularManga(page)
    }

    /** Trivial now - the manga is already a real local entry with a known id by fetch time. */
    fun openEntry(entry: DiscoverMangaEntry) {
        mutableState.update { it.copy(pendingMangaId = entry.manga.id) }
    }

    fun consumePendingNavigation() {
        mutableState.update { it.copy(pendingMangaId = null) }
    }

    private fun <T> interleave(lists: List<List<T>>): List<T> {
        val result = mutableListOf<T>()
        var i = 0
        while (lists.any { it.size > i }) {
            lists.forEach { list -> list.getOrNull(i)?.let(result::add) }
            i++
        }
        return result
    }
}
