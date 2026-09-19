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
import kotlinx.coroutines.delay
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
        val trackerMessage: String? = null,
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
                AiRecommendationResult.NoCandidates -> "No other manga found to compare against yet."
                AiRecommendationResult.NoMatches -> "No close matches found for this title."
                is AiRecommendationResult.Success -> null
            }

            val tracks = getTracks.await(mangaId)
            val trackedOn = tracks.mapNotNull { track ->
                trackerManager.get(track.trackerId)?.name
            }

            val trackerResult = runCatching {
                loadTrackerSuggestions(tracks, manga, source as? CatalogueSource)
            }.getOrElse {
                logcat(LogPriority.ERROR, it) { "Failed to load tracker suggestions" }
                TrackerSuggestionResult(emptyList(), "Something went wrong fetching tracker recommendations.")
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
                trackerSuggestions = trackerResult.suggestions,
                trackerMessage = trackerResult.message,
            )
        }
    }

    // --- Tracker recommendations (AniList + MyAnimeList) --------------------

    private data class TrackerSuggestionResult(
        val suggestions: List<Manga>,
        val message: String?,
    )

    /**
     * Names of trackers we know how to fetch recommendations from.
     * NovelUpdates and Notion (and anything else) have no public
     * recommendations API, so they're intentionally excluded rather
     * than silently failing.
     */
    private enum class SupportedTracker { ANILIST, MYANIMELIST }

    private suspend fun loadTrackerSuggestions(
        tracks: List<Track>,
        manga: Manga,
        currentSource: CatalogueSource?,
    ): TrackerSuggestionResult = coroutineScope {
        if (currentSource == null) {
            return@coroutineScope TrackerSuggestionResult(
                emptyList(),
                "Could not search for recommendations — the source failed to load for this session.",
            )
        }
        if (tracks.isEmpty()) {
            return@coroutineScope TrackerSuggestionResult(emptyList(), null)
        }

        // VERIFY: property names for these trackers on your TrackerManager.
        // Common names in Tachiyomi/Mihon forks: `aniList`/`anilist`, `myAnimeList`/`myanimelist`.
        val aniListId = runCatching { trackerManager.aniList.id }.getOrNull()
        val malId = runCatching { trackerManager.myAnimeList.id }.getOrNull()

        val supportedTracks = tracks.mapNotNull { track ->
            when (track.trackerId) {
                aniListId -> SupportedTracker.ANILIST to track
                malId -> SupportedTracker.MYANIMELIST to track
                else -> null
            }
        }

        logcat(LogPriority.DEBUG) {
            "Recs: aniListId=$aniListId malId=$malId trackIds=${tracks.map { it.trackerId }} " +
                "remoteIds=${tracks.map { it.remoteId }} supported=${supportedTracks.map { it.first }}"
        }

        if (supportedTracks.isEmpty()) {
            val linkedNames = tracks.mapNotNull { trackerManager.get(it.trackerId)?.name }
            return@coroutineScope TrackerSuggestionResult(
                emptyList(),
                if (linkedNames.isNotEmpty()) {
                    "${linkedNames.joinToString(", ")} doesn't support fetching recommendations."
                } else {
                    null
                },
            )
        }

        val titleResults = supportedTracks.map { (tracker, track) ->
            async {
                runCatching {
                    when (tracker) {
                        SupportedTracker.ANILIST -> fetchWithRetry {
                            fetchAniListRecommendationTitles(track.remoteId)
                        }
                        SupportedTracker.MYANIMELIST -> fetchWithRetry {
                            fetchMalRecommendationTitles(track.remoteId)
                        }
                    }
                }.getOrElse {
                    logcat(LogPriority.ERROR, it) { "Recs: fetch failed for $tracker remoteId=${track.remoteId}" }
                    emptyList()
                }
            }
        }.awaitAll().flatten().distinct()

        logcat(LogPriority.DEBUG) { "Recs: titleResults=$titleResults" }

        if (titleResults.isEmpty()) {
            return@coroutineScope TrackerSuggestionResult(
                emptyList(),
                "No recommendations returned by your linked trackers yet.",
            )
        }

        val resolvedManga = titleResults.take(20).map { title ->
            async {
                runCatching {
                    currentSource.getSearchManga(
                        page = 1,
                        query = title,
                        filters = eu.kanade.tachiyomi.source.model.FilterList(),
                    )?.mangas?.firstOrNull()
                }.getOrElse {
                    logcat(LogPriority.ERROR, it) { "Recs: search failed for title='$title'" }
                    null
                }
            }
        }.awaitAll()

        logcat(LogPriority.DEBUG) {
            "Recs: resolved ${resolvedManga.count { it != null }}/${resolvedManga.size} titles against source"
        }

        val filtered = resolvedManga.filterNotNull()
            .filter { it.url != manga.url }
            .distinctBy { it.url }
            .map { networkToLocalManga(it.toDomainManga(sourceId = currentSource.id, isNovel = manga.isNovel)) }

        TrackerSuggestionResult(
            suggestions = filtered,
            message = if (filtered.isEmpty()) {
                "Found tracker recommendations, but none matched this source's catalogue."
            } else {
                null
            },
        )
    }

    /** Retries a flaky network call once after a short delay before giving up. */
    private suspend fun <T> fetchWithRetry(block: () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Tracker fetch failed once, retrying" }
            delay(600)
            block()
        }
    }

    private fun fetchAniListRecommendationTitles(aniListMediaId: Long): List<String> {
        logcat(LogPriority.DEBUG) { "Recs: querying AniList media id=$aniListMediaId" }

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
            if (!response.isSuccessful) {
                val errorBody = runCatching { response.body?.string() }.getOrNull()
                logcat(LogPriority.DEBUG) { "Recs: AniList HTTP ${response.code} body=$errorBody" }
                throw IllegalStateException("AniList returned HTTP ${response.code}")
            }
            val body = response.body?.string() ?: return emptyList()
            logcat(LogPriority.DEBUG) { "Recs: AniList raw response=$body" }

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

    private fun fetchMalRecommendationTitles(malMangaId: Long): List<String> {
        // VERIFY: your fork's MAL API client-id constant. It's used elsewhere for
        // MAL OAuth/public calls — search the project for an existing "X-MAL-CLIENT-ID"
        // usage (often in a MyAnimeListApi class) and reuse that same constant here
        // instead of hardcoding a new one.
        val malClientId = MAL_CLIENT_ID

        val request = Request.Builder()
            .url("https://api.myanimelist.net/v2/manga/$malMangaId?fields=recommendations")
            .header("X-MAL-CLIENT-ID", malClientId)
            .get()
            .build()

        networkHelper.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                logcat(LogPriority.DEBUG) { "Recs: MAL HTTP ${response.code}" }
                throw IllegalStateException("MAL returned HTTP ${response.code}")
            }
            val body = response.body?.string() ?: return emptyList()
            val recs = JSONObject(body).optJSONArray("recommendations") ?: return emptyList()

            return buildList {
                for (i in 0 until recs.length()) {
                    val title = recs.getJSONObject(i)
                        .optJSONObject("manga")
                        ?.optString("title")
                    if (!title.isNullOrBlank()) add(title)
                }
            }
        }
    }

    // --- Source / AI logic ---------------------------------------------------

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

    companion object {
        // VERIFY: replace with your fork's actual MAL client ID constant/lookup.
        private const val MAL_CLIENT_ID = "YOUR_MAL_CLIENT_ID_HERE"
    }
}