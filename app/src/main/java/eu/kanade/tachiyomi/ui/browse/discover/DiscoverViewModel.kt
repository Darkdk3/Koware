package eu.kanade.tachiyomi.ui.browse.discover

import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.jsplugin.JsPluginManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.isNovelSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.update
import mihon.core.viewmodel.StateViewModel
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

enum class DiscoverBrowseMode { LATEST, POPULAR }

/**
 * The manga here is already a real local database entry (converted via NetworkToLocalManga
 * as soon as it's fetched, not only when tapped) - same as how BrowseSourceScreen already
 * handles source listings. This gives Discover a real id for cover caching/consistent card
 * rendering, and makes tap-to-navigate trivial since the id is already known.
 */
data class DiscoverEntry(
    val source: CatalogueSource,
    val manga: Manga,
)

/** One row in the Discover "Sources" sheet. */
data class DiscoverSourceOption(
    val id: Long,
    val name: String,
    val isPinned: Boolean,
)

data class DiscoverScreenState(
    val items: List<DiscoverEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasPinnedNovelSources: Boolean = true,
    val browseMode: DiscoverBrowseMode = DiscoverBrowseMode.LATEST,
    val pendingMangaId: Long? = null,

    // Every novel source that can feed Discover (pinned ones first) and the ids currently feeding it.
    // Drives the "Sources" sheet.
    val sourceOptions: List<DiscoverSourceOption> = emptyList(),
    val selectedSourceIds: Set<Long> = emptySet(),

    // AI recommendations shelf. DiscoverTab reads these five fields and only shows the shelf when
    // the AI features + Discover shelf preferences are both enabled. Nothing populates them yet,
    // so the shelf currently shows its empty-state message.
    val recommendations: List<DiscoverEntry> = emptyList(),
    val recommendationTopGenres: List<String> = emptyList(),
    // manga id -> match percentage (0-100). The tab falls back to a genre heuristic when absent.
    val recommendationScores: Map<Long, Int> = emptyMap(),
    val aiRecommendationMessage: String? = null,
    val isLoadingRecommendations: Boolean = false,
)

class DiscoverViewModel(
    private val sourceManager: SourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val jsPluginManager: JsPluginManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
) : StateViewModel<DiscoverScreenState>(DiscoverScreenState()) {

    private data class SourcePageCursor(val nextPage: Int, val hasNextPage: Boolean)

    private val pageCursors = mutableMapOf<Long, SourcePageCursor>()

    // Tracked so a refresh / mode switch can cancel in-flight work. Without this, a stale
    // loadMore() result could be appended onto a fresh list and duplicate items (=> duplicate
    // LazyGrid keys => crash).
    private var feedJob: Job? = null
    private var moreJob: Job? = null

    init {
        loadDiscoverFeed()
    }

    fun setBrowseMode(mode: DiscoverBrowseMode) {
        if (mode == state.value.browseMode) return
        mutableState.update { it.copy(browseMode = mode) }
        loadDiscoverFeed()
    }

    /** Every novel source that could feed Discover. */
    private fun allNovelSources(): List<CatalogueSource> {
        return (sourceManager.getOnlineSources() + jsPluginManager.jsSources.value)
            .filterIsInstance<CatalogueSource>()
            .distinctBy { it.id }
            .filter { it.isNovelSource() }
            .filter { it.supportsLatest }
    }

    /**
     * The sources that actually feed Discover: the ones picked in the Sources sheet, or - until
     * a selection has been applied for the first time - the pinned ones (the old behaviour).
     */
    private fun feedSources(): List<CatalogueSource> {
        val chosenKeys = if (sourcePreferences.discoverSourcesCustomized.get()) {
            sourcePreferences.discoverSourceIds.get()
        } else {
            sourcePreferences.pinnedSources.get()
        }
        return allNovelSources().filter { it.id.toString() in chosenKeys }
    }

    /** Rebuilds the Sources sheet data. Also called when the sheet opens so it's never stale. */
    fun refreshSourceOptions() {
        val pinnedKeys = sourcePreferences.pinnedSources.get()
        val options = allNovelSources()
            .map { DiscoverSourceOption(id = it.id, name = it.name, isPinned = it.id.toString() in pinnedKeys) }
            .sortedWith(
                compareByDescending<DiscoverSourceOption> { it.isPinned }
                    .thenBy { it.name.lowercase() },
            )
        val selected = feedSources().map { it.id }.toSet()
        mutableState.update { it.copy(sourceOptions = options, selectedSourceIds = selected) }
    }

    /** Saves the picked sources and reloads the feed, but only if the selection actually changed. */
    fun applySources(ids: Set<Long>) {
        val changed = ids != state.value.selectedSourceIds
        sourcePreferences.discoverSourceIds.set(ids.map { it.toString() }.toSet())
        sourcePreferences.discoverSourcesCustomized.set(true)
        if (changed) {
            loadDiscoverFeed()
        }
    }

    fun loadDiscoverFeed() {
        moreJob?.cancel()
        feedJob?.cancel()
        refreshSourceOptions()
        feedJob = viewModelScope.launchIO {
            mutableState.update { it.copy(isLoading = true, isLoadingMore = false) }
            pageCursors.clear()

            val sources = feedSources()
            val mode = state.value.browseMode

            val perSourceLists = sources.map { source ->
                val page = fetchPageOrNull(source, mode, page = 1)
                ensureActive()
                pageCursors[source.id] = SourcePageCursor(
                    nextPage = 2,
                    hasNextPage = page?.hasNextPage == true,
                )
                (page?.mangas ?: emptyList()).map { sManga ->
                    val localManga = networkToLocalManga(sManga.toDomainManga(source.id, isNovel = true))
                    DiscoverEntry(source, localManga)
                }
            }

            // A source can return the same entry twice; never let duplicate ids reach the grid.
            val merged = interleave(perSourceLists).distinctBy { it.manga.id }
            mutableState.update {
                it.copy(items = merged, isLoading = false, hasPinnedNovelSources = sources.isNotEmpty())
            }
        }
    }

    fun loadMore() {
        if (state.value.isLoading || moreJob?.isActive == true) return
        val sources = feedSources().filter { pageCursors[it.id]?.hasNextPage == true }
        if (sources.isEmpty()) return

        moreJob = viewModelScope.launchIO {
            mutableState.update { it.copy(isLoadingMore = true) }
            val mode = state.value.browseMode

            val newLists = sources.map { source ->
                val cursor = pageCursors[source.id] ?: return@map emptyList<DiscoverEntry>()
                val page = fetchPageOrNull(source, mode, cursor.nextPage)
                ensureActive()
                pageCursors[source.id] = SourcePageCursor(
                    nextPage = cursor.nextPage + 1,
                    hasNextPage = page?.hasNextPage == true,
                )
                (page?.mangas ?: emptyList()).map { sManga ->
                    val localManga = networkToLocalManga(sManga.toDomainManga(source.id, isNovel = true))
                    DiscoverEntry(source, localManga)
                }
            }

            val appended = interleave(newLists)
            mutableState.update { s ->
                // Overlapping pages: skip anything already in the list (and dupes within the batch).
                val seen = s.items.mapTo(HashSet()) { it.manga.id }
                s.copy(
                    items = s.items + appended.filter { seen.add(it.manga.id) },
                    isLoadingMore = false,
                )
            }
        }
    }

    private suspend fun fetchPage(
        source: CatalogueSource,
        mode: DiscoverBrowseMode,
        page: Int,
    ) = when (mode) {
        DiscoverBrowseMode.LATEST -> source.getLatestUpdates(page)
        DiscoverBrowseMode.POPULAR -> source.getPopularManga(page)
    }

    // Like runCatching { ... }.getOrNull(), but lets cancellation through so cancelled jobs
    // actually stop instead of carrying on with stale data.
    private suspend fun fetchPageOrNull(
        source: CatalogueSource,
        mode: DiscoverBrowseMode,
        page: Int,
    ) = try {
        fetchPage(source, mode, page)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** Trivial now - the manga is already a real local entry with a known id by fetch time. */
    fun openEntry(entry: DiscoverEntry) {
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
