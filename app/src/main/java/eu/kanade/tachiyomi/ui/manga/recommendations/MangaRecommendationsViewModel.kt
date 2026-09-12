package eu.kanade.tachiyomi.ui.manga.recommendations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.getNameForMangaInfo
import eu.kanade.tachiyomi.ui.browse.discover.AiRecommendationResult
import eu.kanade.tachiyomi.ui.browse.discover.GetAiRecommendations
import eu.kanade.tachiyomi.ui.browse.discover.RecommendableItem
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

            val aiResult = if (suggestions.isNotEmpty()) {
                runCatching {
                    getAiRecommendations.await(
                        suggestions.map { MangaRecommendable(it, sourceName) }
                    )
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
        return runCatching {
            source.getSearchManga(
                page = 1,
                query = manga.title,
                filters = eu.kanade.tachiyomi.source.model.FilterList(),
            )?.mangas.orEmpty()
        }.getOrElse {
            logcat(LogPriority.ERROR, it) { "Failed to load source suggestions" }
            emptyList()
        }
            .filter { it.url != manga.url }
            .take(10)
            .map { it.toDomainManga(sourceId = source.id, isNovel = manga.isNovel) }
    }

    class Factory(private val mangaId: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MangaRecommendationsViewModel(mangaId) as T
    }
}
