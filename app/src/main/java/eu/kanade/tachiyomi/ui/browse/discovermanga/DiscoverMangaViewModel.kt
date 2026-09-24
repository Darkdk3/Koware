package eu.kanade.tachiyomi.ui.browse.discovermanga

import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.jsplugin.JsPluginManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.isNovelSource
import eu.kanade.tachiyomi.ui.browse.discover.AiRecommendationResult
import eu.kanade.tachiyomi.ui.browse.discover.DiscoverSourceOption
import eu.kanade.tachiyomi.ui.browse.discover.GetAiRecommendations
import eu.kanade.tachiyomi.ui.browse.discover.RecommendableItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
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
) : RecommendableItem {
    override val sourceName: String get() = source.name
    override val mangaTitle: String get() = manga.title
    override val mangaGenre: List<String>? get() = manga.genre
    override val mangaAuthor: String? get() = manga.author
}

data class DiscoverMangaScreenState(
    val items: List<DiscoverMangaEntry> = emptyList(),
    val recommendations: List<DiscoverMangaEntry> = emptyList(),
    val recommendationTopGenres: List<String> = emptyList(),
    val recommendationScores: Map<Long, Int> = emptyMap(),
    val aiRecommendationMessage: String? = null,
    val isLoadingRecommendations: Boolean = false,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasPinnedNovelSources: Boolean = true,
    val browseMode: DiscoverMangaBrowseMode = DiscoverMangaBrowseMode.LATEST,
    val pendingMangaId: Long? = null,

    // Every manga source that can feed Discover (pinned ones first) and the ids currently feeding it.
    // Drives the "Sources" picker.
    val sourceOptions: List<DiscoverSourceOption> = emptyList(),
    val selectedSourceIds: Set<Long> = emptySet(),
)

class DiscoverMangaViewModel(
    private val sourceManager: SourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val jsPluginManager: JsPluginManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getAiRecommendations: GetAiRecommendations = GetAiRecommendations(),
) : StateViewModel<DiscoverMangaScreenState>(DiscoverMangaScreenState()) {

    private data class SourcePageCursor(val nextPage: Int, val hasNextPage: Boolean)
    private val pageCursors = ConcurrentHashMap<Long, SourcePageCursor>()

    // Tracked so a refresh / mode switch cancels in-flight work. Without this, a stale loadMore()
    // result could be appended onto a fresh list and duplicate items (=> duplicate LazyGrid keys => crash).
    private var feedJob: Job? = null
    private var moreJob: Job? = null
    private var aiJob: Job? = null

    init {
        loadDiscoverFeed()
    }

    fun setBrowseMode(mode: DiscoverMangaBrowseMode) {
        if (mode == state.value.browseMode) return
        mutableState.update { it.copy(browseMode = mode) }
        loadDiscoverFeed()
    }

    /** Every manga (non-novel) source that could feed Discover. */
    private fun allMangaSources(): List<CatalogueSource> {
        return (sourceManager.getOnlineSources() + jsPluginManager.jsSources.value)
            .filterIsInstance<CatalogueSource>()
            .distinctBy { it.id }
            .filter { !it.isNovelSource() }
            .filter { it.supportsLatest }
    }

    /**
     * The sources that actually feed Discover: the ones picked in the Sources picker, or - until
     * a selection has been applied for the first time - the pinned ones (the old behaviour).
     */
    private fun feedSources(): List<CatalogueSource> {
        val chosenKeys = if (sourcePreferences.discoverMangaSourcesCustomized.get()) {
            sourcePreferences.discoverMangaSourceIds.get()
        } else {
            sourcePreferences.pinnedSources.get()
        }
        return allMangaSources().filter { it.id.toString() in chosenKeys }
    }

    /** Rebuilds the picker data. Also called when the picker opens so it's never stale. */
    fun refreshSourceOptions() {
        val pinnedKeys = sourcePreferences.pinnedSources.get()
        val options = allMangaSources()
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
        sourcePreferences.discoverMangaSourceIds.set(ids.map { it.toString() }.toSet())
        sourcePreferences.discoverMangaSourcesCustomized.set(true)
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
                async {
                    val page = fetchPageOrNull(source, mode, page = 1)
                    pageCursors[source.id] = SourcePageCursor(
                        nextPage = 2,
                        hasNextPage = page?.hasNextPage == true,
                    )
                    (page?.mangas ?: emptyList()).map { sManga ->
                        val localManga = networkToLocalManga(sManga.toDomainManga(source.id, isNovel = false))
                        DiscoverMangaEntry(source, localManga)
                    }
                }
            }.awaitAll()
            ensureActive()

            // A source can return the same entry twice; never let duplicate ids reach the grid.
            val merged = interleave(perSourceLists).distinctBy { it.manga.id }

            mutableState.update {
                it.copy(items = merged, isLoading = false, hasPinnedNovelSources = sources.isNotEmpty())
            }

            loadAiRecommendations()
        }
    }

    private fun loadAiRecommendations() {
        aiJob?.cancel()
        aiJob = viewModelScope.launchIO {
            mutableState.update { it.copy(isLoadingRecommendations = true, aiRecommendationMessage = null) }
            val result = runCatching { getAiRecommendations.await(state.value.items) }
                .getOrElse { AiRecommendationResult.Failed(it.message ?: "AI request failed") }
            ensureActive()
            mutableState.update { state ->
                when (result) {
                    is AiRecommendationResult.Success -> state.copy(
                        recommendations = result.recommendations,
                        recommendationTopGenres = result.topGenres,
                        recommendationScores = result.recommendations.mapIndexedNotNull { index, entry ->
                            result.scores.getOrNull(index)?.let { entry.manga.id to it }
                        }.toMap(),
                        isLoadingRecommendations = false,
                        aiRecommendationMessage = null,
                    )
                    AiRecommendationResult.Disabled -> state.copy(
                        recommendations = emptyList(),
                        recommendationTopGenres = emptyList(),
                        recommendationScores = emptyMap(),
                        isLoadingRecommendations = false,
                        aiRecommendationMessage = null,
                    )
                    AiRecommendationResult.NoEngine -> state.copy(
                        recommendations = emptyList(),
                        recommendationTopGenres = emptyList(),
                        recommendationScores = emptyMap(),
                        isLoadingRecommendations = false,
                        aiRecommendationMessage = "Set up an AI engine in Settings \u2192 AI to get personalized recommendations.",
                    )
                    AiRecommendationResult.NoReadingHistory -> state.copy(
                        recommendations = emptyList(),
                        recommendationTopGenres = emptyList(),
                        recommendationScores = emptyMap(),
                        isLoadingRecommendations = false,
                        aiRecommendationMessage = "Keep reading so the AI can learn your taste - recommendations will appear here.",
                    )
                    AiRecommendationResult.NoCandidates -> state.copy(
                        recommendations = emptyList(),
                        recommendationTopGenres = emptyList(),
                        recommendationScores = emptyMap(),
                        isLoadingRecommendations = false,
                        aiRecommendationMessage = "Pick some manga sources first (tap Sources) - AI recommendations will appear here once the feed has titles.",
                    )
                    AiRecommendationResult.NoMatches -> state.copy(
                        recommendations = emptyList(),
                        recommendationTopGenres = emptyList(),
                        recommendationScores = emptyMap(),
                        isLoadingRecommendations = false,
                        aiRecommendationMessage = "The AI didn't find a good match this time - pull down to refresh and try again.",
                    )
                    is AiRecommendationResult.Failed -> state.copy(
                        recommendations = emptyList(),
                        recommendationTopGenres = emptyList(),
                        recommendationScores = emptyMap(),
                        isLoadingRecommendations = false,
                        aiRecommendationMessage = "AI recommendations failed: ${result.message}",
                    )
                }
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
                async {
                    val cursor = pageCursors[source.id] ?: return@async emptyList<DiscoverMangaEntry>()
                    val page = fetchPageOrNull(source, mode, cursor.nextPage)
                    pageCursors[source.id] = SourcePageCursor(
                        nextPage = cursor.nextPage + 1,
                        hasNextPage = page?.hasNextPage == true,
                    )
                    (page?.mangas ?: emptyList()).map { sManga ->
                        val localManga = networkToLocalManga(sManga.toDomainManga(source.id, isNovel = false))
                        DiscoverMangaEntry(source, localManga)
                    }
                }
            }.awaitAll()
            ensureActive()
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
        mode: DiscoverMangaBrowseMode,
        page: Int,
    ) = when (mode) {
        DiscoverMangaBrowseMode.LATEST -> source.getLatestUpdates(page)
        DiscoverMangaBrowseMode.POPULAR -> source.getPopularManga(page)
    }

    // Like runCatching { ... }.getOrNull(), but lets cancellation through so cancelled jobs
    // actually stop instead of carrying on with stale data.
    private suspend fun fetchPageOrNull(
        source: CatalogueSource,
        mode: DiscoverMangaBrowseMode,
        page: Int,
    ) = try {
        fetchPage(source, mode, page)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
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
