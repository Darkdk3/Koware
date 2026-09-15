package eu.kanade.tachiyomi.ui.manga.recommendations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.getNameForMangaInfo
import eu.kanade.tachiyomi.source.isNovelSource
import eu.kanade.tachiyomi.ui.browse.discover.AiRecommendationResult
import eu.kanade.tachiyomi.ui.browse.discover.GetAiRecommendations
import eu.kanade.tachiyomi.ui.browse.discover.RecommendableItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Wraps a [Manga] for the generic AI recommendation pool.
 */
private class MangaRecommendable(
    private val _manga: Manga,
    private val _sourceName: String,
) : RecommendableItem {
    val manga: Manga get() = _manga
    override val sourceName: String get() = _sourceName
    override val mangaTitle: String get() = _manga.title
    override val mangaGenre: List<String>? get() = _manga.genre
    override val mangaAuthor: String? get() = _manga.author
}

sealed interface MangaRecommendationsUiState {
    data object Loading : MangaRecommendationsUiState

    data class Success(
        val manga: Manga,
        val sourceName: String,
        val sourceSuggestions: List<Manga>,
        val aiPicks: List<Manga>,
        val aiScores: List<Int?>,
        val aiMessage: String?,
        val trackedOn: List<String>,
    ) : MangaRecommendationsUiState
}

class MangaRecommendationsViewModel(
    private val mangaId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow<MangaRecommendationsUiState>(MangaRecommendationsUiState.Loading)
    val state: StateFlow<MangaRecommendationsUiState> = _state.asStateFlow()

    private val getManga: GetManga = Injekt.get()
    private val sourceManager: SourceManager = Injekt.get()
    private val trackerManager: TrackerManager = Injekt.get()
    private val getAiRecommendations: GetAiRecommendations = GetAiRecommendations()
    private val translationPreferences: tachiyomi.domain.translation.service.TranslationPreferences = Injekt.get()
    private val networkToLocalManga: tachiyomi.domain.manga.interactor.NetworkToLocalManga = Injekt.get()
    private val libraryPreferences: tachiyomi.domain.library.service.LibraryPreferences = Injekt.get()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val manga = getManga.await(mangaId) ?: run {
                _state.value = MangaRecommendationsUiState.Loading
                return@launch
            }
            val source = sourceManager.getOrStub(manga.source)
            val sourceName = source.getNameForMangaInfo()

            val suggestions = loadSourceSuggestions(source as? CatalogueSource, manga)

            val aiCandidatePool = loadAiCandidatePool(source as? CatalogueSource, manga)

            val aiResult = if (
                translationPreferences.aiFeaturesEnabled().get() &&
                translationPreferences.aiRecommendationsMangaDetailEnabled().get() &&
                aiCandidatePool.isNotEmpty()
            ) {
                runCatching {
                    getAiRecommendations.await(aiCandidatePool)
                }.getOrElse {
                    logcat(LogPriority.ERROR, it) { "AI recs failed" }
                    AiRecommendationResult.Failed(it.message ?: "AI request failed")
                }
            } else {
                AiRecommendationResult.NoCandidates
            }

            val picks = when (aiResult) {
                is AiRecommendationResult.Success ->
                    aiResult.recommendations.map { (it as MangaRecommendable).manga }
                else -> emptyList()
            }
            val scores = when (aiResult) {
                is AiRecommendationResult.Success -> aiResult.scores
                else -> emptyList()
            }
            val aiMessage = when (aiResult) {
                is AiRecommendationResult.Failed -> aiResult.message
                AiRecommendationResult.Disabled -> "AI features are turned off in Settings → AI."
                AiRecommendationResult.NoEngine -> "Set up an AI engine in Settings → AI."
                AiRecommendationResult.NoReadingHistory -> "Keep reading so the AI can learn your taste."
                is AiRecommendationResult.Success -> null
                else -> null
            }

            val trackedOn = trackerManager.loggedInTrackers()
                .filter { it.id != TrackerManager.NOTION }
                .map { it.name }

            _state.value = MangaRecommendationsUiState.Success(
                manga = manga,
                sourceName = sourceName,
                sourceSuggestions = suggestions,
                aiPicks = picks,
                aiScores = scores,
                aiMessage = aiMessage,
                trackedOn = trackedOn,
            )
        }
    }

    private suspend fun loadSourceSuggestions(source: CatalogueSource?, manga: Manga): List<Manga> {
        if (source == null) return emptyList()

        val useSourceRelated = libraryPreferences.useSourceRelatedMangas.get()
        val disableSearchFallback = libraryPreferences.disableRelatedMangasBySearch.get()

        val results: List<eu.kanade.tachiyomi.source.model.SManga> = buildList {
            // 1. Source provides its own related/recommended list
            if (useSourceRelated && source.supportsRelatedMangas) {
                val related = runCatching {
                    source.fetchRelatedMangaList(
                        eu.kanade.tachiyomi.source.model.SManga.create().apply {
                            title = manga.title
                            url = manga.url
                            thumbnail_url = manga.thumbnail_url
                        },
                    )
                }.getOrElse {
                    logcat(LogPriority.ERROR, it) { "Failed to load source related mangas" }
                    emptyList()
                }
                addAll(related)
            }

            // 2. If the source-website list was empty (or skipped), try smart-search
            if (isEmpty() && !disableSearchFallback) {
                val searched = runCatching {
                    source.getSearchManga(
                        page = 1,
                        query = manga.title,
                        filters = eu.kanade.tachiyomi.source.model.FilterList(),
                    )?.mangas.orEmpty()
                }.getOrElse {
                    logcat(LogPriority.ERROR, it) { "Failed to load source suggestions" }
                    emptyList()
                }
                addAll(searched)
            }
        }

        return results
            .filter { it.url != manga.url }
            .take(25)
            .map { networkToLocalManga(it.toDomainManga(sourceId = source.id, isNovel = manga.isNovel)) }
    }

    private suspend fun loadAiCandidatePool(
        currentSource: CatalogueSource?,
        manga: Manga,
    ): List<MangaRecommendable> {
        return coroutineScope {
            val otherSources = sourceManager.getOnlineSources()
                .filterIsInstance<CatalogueSource>()
                .filter { it.id != currentSource?.id && it.isNovelSource() == manga.isNovel }
                .shuffled()
                .take(6)

            val allSources = listOfNotNull(currentSource) + otherSources

            allSources.map { source ->
                async {
                    val sourceLabel = source.getNameForMangaInfo()
                    runCatching {
                        source.getSearchManga(
                            page = 1,
                            query = manga.title,
                            filters = eu.kanade.tachiyomi.source.model.FilterList(),
                        )?.mangas.orEmpty()
                    }.getOrElse {
                        logcat(LogPriority.ERROR) { "Failed to search ${source.name}" }
                        emptyList()
                    }
                        .filter { it.url != manga.url }
                        .take(15)
                        .map { it.toDomainManga(sourceId = source.id, isNovel = manga.isNovel) }
                        .map { MangaRecommendable(it, sourceLabel) }
                }
            }.awaitAll().flatten()
                .distinctBy { it.mangaTitle }
        }
    }

    class Factory(private val mangaId: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MangaRecommendationsViewModel(mangaId) as T
    }
}
