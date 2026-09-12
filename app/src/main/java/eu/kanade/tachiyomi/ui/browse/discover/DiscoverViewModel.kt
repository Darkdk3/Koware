package eu.kanade.tachiyomi.ui.browse.discover

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

enum class DiscoverBrowseMode { LATEST, POPULAR }

data class DiscoverEntry(
    val source: CatalogueSource,
    val manga: Manga,
) : RecommendableItem {
    override val sourceName: String get() = source.name
    override val mangaTitle: String get() = manga.title
    override val mangaGenre: List<String>? get() = manga.genre
    override val mangaAuthor: String? get() = manga.author
}

data class DiscoverScreenState(
    val items: List<DiscoverEntry> = emptyList(),
    val recommendations: List<DiscoverEntry> = emptyList(),
    val recommendationTopGenres: List<String> = emptyList(),
    val recommendationScores: Map<Long, Int> = emptyMap(),
    val aiRecommendationMessage: String? = null,
    val isLoadingRecommendations: Boolean = false,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasPinnedNovelSources: Boolean = true,
    val browseMode: DiscoverBrowseMode = DiscoverBrowseMode.LATEST,
    val pendingMangaId: Long? = null,
)

class DiscoverViewModel(
    private val sourceManager: SourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val jsPluginManager: JsPluginManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getAiRecommendations: GetAiRecommendations = GetAiRecommendations(),
) : StateViewModel<DiscoverScreenState>(DiscoverScreenState()) {

    private data class SourcePageCursor(val nextPage: Int, val hasNextPage: Boolean)

    private val pageCursors = mutableMapOf<Long, SourcePageCursor>()

    init {
        loadDiscoverFeed()
    }

    fun setBrowseMode(mode: DiscoverBrowseMode) {
        if (mode == state.value.browseMode) return
        mutableState.update { it.copy(browseMode = mode) }
        loadDiscoverFeed()
    }

    private fun pinnedNovelSources(): List<CatalogueSource> {
        val pinnedKeys = sourcePreferences.pinnedSources.get()
        return (sourceManager.getOnlineSources() + jsPluginManager.jsSources.value)
            .filterIsInstance<CatalogueSource>()
            .distinctBy { it.id }
            .filter { it.isNovelSource() }
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
                    val localManga = networkToLocalManga(sManga.toDomainManga(source.id, isNovel = true))
                    DiscoverEntry(source, localManga)
                }
            }

            val merged = interleave(perSourceLists)
            mutableState.update {
                it.copy(items = merged, isLoading = false, hasPinnedNovelSources = sources.isNotEmpty())
            }

            loadAiRecommendations()
        }
    }

    private fun loadAiRecommendations() {
        viewModelScope.launchIO {
            mutableState.update { it.copy(isLoadingRecommendations = true, aiRecommendationMessage = null) }
            val result = runCatching { getAiRecommendations.await(state.value.items) }
                .getOrElse { AiRecommendationResult.Failed(it.message ?: "AI request failed") }
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
                        aiRecommendationMessage = "Keep reading novels so the AI can learn your taste - recommendations will appear here.",
                    )
                    AiRecommendationResult.NoCandidates -> state.copy(
                        recommendations = emptyList(),
                        recommendationTopGenres = emptyList(),
                        recommendationScores = emptyMap(),
                        isLoadingRecommendations = false,
                        aiRecommendationMessage = "Pin some novel sources first - AI recommendations will appear here once the feed has titles.",
                    )
                    AiRecommendationResult.NoMatches -> state.copy(
                        recommendations = emptyList(),
                        recommendationTopGenres = emptyList(),
                        recommendationScores = emptyMap(),
                        isLoadingRecommendations = false,
                        aiRecommendationMessage = "The AI didn't find a good match this time - tap refresh to try again.",
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
                    val localManga = networkToLocalManga(sManga.toDomainManga(source.id, isNovel = true))
                    DiscoverEntry(source, localManga)
                }
            }

            val appended = interleave(newLists)
            mutableState.update { it.copy(items = it.items + appended, isLoadingMore = false) }
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
