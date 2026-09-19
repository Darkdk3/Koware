package eu.kanade.tachiyomi.ui.manga.recommendations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.NetworkHelper
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
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.domain.manga.model.toDomainManga
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.model.Track
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
        val groupedSourceSuggestions: Map<String, List<Manga>> = emptyMap(),
        val aiPicks: List<Manga>,
        val aiScores: List<Int?>,
        val aiMessage: String?,
        val trackedOn: List<String>,
        val trackerSuggestions: List<Manga> = emptyList(),
    ) : MangaRecommendationsUiState
}

class MangaRecommendationsViewModel(
    private val mangaId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow<MangaRecommendationsUiState>(MangaRecommendationsUiState.Loading)
    val state: StateFlow<MangaRecommendationsUiState> = _state.asStateFlow()

    private val getManga: GetManga = Injekt.get()
    private val sourceManager: SourceManager = Injekt.get()
    private val getTracks: GetTracks = Injekt.get()
    private val trackerManager: TrackerManager = Injekt.get()
    private val getAiRecommendations: GetAiRecommendations = GetAiRecommendations()
    private val translationPreferences: tachiyomi.domain.translation.service.TranslationPreferences = Injekt.get()
    private val networkToLocalManga: tachiyomi.domain.manga.interactor.NetworkToLocalManga = Injekt.get()
    private val libraryPreferences: tachiyomi.domain.library.service.LibraryPreferences = Injekt.get()
    private val networkHelper: NetworkHelper = Injekt.get()

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
            val groupedSuggestions = loadGroupedSourceSuggestions(source as? CatalogueSource, manga, suggestions)

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

            val tracks = getTracks.await(mangaId)
            val trackedOn = tracks.mapNotNull { track ->
                trackerManager.get(track.trackerId)?.name
            }

            val trackerSuggestions = runCatching {
                loadTrackerSuggestions(tracks, manga, source as? CatalogueSource)
            }.getOrElse {
                logcat(LogPriority.ERROR, it) { "Failed to load tracker suggestions" }
                emptyList()
            }

            _state.value = MangaRecommendationsUiState.Success(
                manga = manga,
                sourceName = sourceName,
                sourceSuggestions = suggestions,
                groupedSourceSuggestions = groupedSuggestions,
                aiPicks = picks,
                aiScores = scores,
                aiMessage = aiMessage,
                trackedOn = trackedOn,
                trackerSuggestions = trackerSuggestions,
            )
        }
    }

    // --- Tracker (AniList) recommendations ---------------------------------

    private suspend fun loadTrackerSuggestions(
        tracks: List<Track>,
        manga: Manga,
        currentSource: CatalogueSource?,
    ): List<Manga> = coroutineScope {
        if (currentSource == null) return@coroutineScope emptyList()

        // VERIFY: property name for the AniList tracker on your TrackerManager.
        // Common names in Tachiyomi/Mihon forks: `aniList`, `anilist`, or `ANILIST`.
        val aniListTrackerId = trackerManager.aniList.id

        // VERIFY: field name on your Track model for the tracker's remote media id.
        // Common names: `remoteId`, `remote_id`, `mediaId`.
        val aniListTrack = tracks.firstOrNull { it.trackerId == aniListTrackerId } ?: return@coroutineScope emptyList()
        val remoteId = aniListTrack.remoteId

        val recommendedTitles = runCatching {
            fetchAniListRecommendationTitles(remoteId)
        }.getOrElse {
            logcat(LogPriority.ERROR, it) { "AniList recommendations request failed" }
            emptyList()
        }

        if (recommendedTitles.isEmpty()) return@coroutineScope emptyList()

        recommendedTitles.take(15).map { title ->
            async {
                runCatching {
                    currentSource.getSearchManga(
                        page = 1,
                        query = title,
                        filters = eu.kanade.tachiyomi.source.model.FilterList(),
                    )?.mangas?.firstOrNull()
                }.getOrNull()
            }
        }.awaitAll()
            .filterNotNull()
            .filter { it.url != manga.url }
            .distinctBy { it.url }
            .map { networkToLocalManga(it.toDomainManga(sourceId = currentSource.id, isNovel = manga.isNovel)) }
    }

    private fun fetchAniListRecommendationTitles(aniListMediaId: Long): List<String> {
        val query = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: MANGA) {
                recommendations(sort: RATING_DESC, perPage: 15) {
                  nodes {
                    mediaRecommendation {
                      title { romaji english }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val payload = JSONObject().apply {
            put("query", query)
            put("variables", JSONObject().put("id", aniListMediaId.toInt()))
        }

        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        networkHelper.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val nodes = JSONObject(body)
                .optJSONObject("data")
                ?.optJSONObject("Media")
                ?.optJSONObject("recommendations")
                ?.optJSONArray("nodes")
                ?: return emptyList()

            return buildList {
                for (i in 0 until nodes.length()) {
                    val rec = nodes.getJSONObject(i).optJSONObject("mediaRecommendation") ?: continue
                    val titleObj = rec.optJSONObject("title") ?: continue
                    val title = titleObj.optString("romaji").ifBlank { titleObj.optString("english") }
                    if (title.isNotBlank()) add(title)
                }
            }
        }
    }

    // --- Existing source / AI logic (unchanged) -----------------------------

    private suspend fun loadGroupedSourceSuggestions(
        source: CatalogueSource?,
        manga: Manga,
        primarySuggestions: List<Manga>,
    ): Map<String, List<Manga>> = coroutineScope {
        val groupedResults = mutableMapOf<String, List<Manga>>()
        if (source != null && primarySuggestions.isNotEmpty()) {
            groupedResults[source.getNameForMangaInfo()] = primarySuggestions
        }

        val otherSources = sourceManager.getOnlineSources()
            .filterIsInstance<CatalogueSource>()
            .filter { it.id != source?.id && it.isNovelSource == manga.isNovel }
            .shuffled()
            .take(4)

        otherSources.map { other ->
            async {
                val otherSourceName = other.getNameForMangaInfo()
                val otherResults = runCatching {
                    other.getSearchManga(
                        page = 1,
                        query = manga.title,
                        filters = eu.kanade.tachiyomi.source.model.FilterList(),
                    )?.mangas.orEmpty()
                }.getOrNull().orEmpty()
                    .filter { it.url != manga.url }
                    .take(10)
                    .map { sManga ->
                        networkToLocalManga(
                            sManga.toDomainManga(sourceId = other.id, isNovel = manga.isNovel),
                        )
                    }
                otherSourceName to otherResults
            }
        }.awaitAll().forEach { (sourceName, results) ->
            if (results.isNotEmpty()) {
                groupedResults[sourceName] = results
            }
        }

        groupedResults
    }

    private suspend fun loadSourceSuggestions(source: CatalogueSource?, manga: Manga): List<Manga> {
        if (source == null) return emptyList()

        val useSourceRelated = libraryPreferences.useSourceRelatedMangas.get()
        val disableSearchFallback = libraryPreferences.disableRelatedMangasBySearch.get()

        val results: List<eu.kanade.tachiyomi.source.model.SManga> = buildList {
            if (useSourceRelated && source.supportsRelatedMangas) {
                val related = runCatching {
                    source.fetchRelatedMangaList(
                        eu.kanade.tachiyomi.source.model.SManga.create().apply {
                            title = manga.title
                            url = manga.url
                            thumbnail_url = manga.thumbnailUrl
                        },
                    )
                }.getOrElse {
                    logcat(LogPriority.ERROR, it) { "Failed to load source related mangas" }
                    emptyList()
                }
                addAll(related)
            }

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