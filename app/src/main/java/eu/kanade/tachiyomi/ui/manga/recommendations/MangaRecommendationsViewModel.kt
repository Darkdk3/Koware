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
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.domain.manga.model.toDomainManga
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.model.Track
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * MAL public client ID, used for the unauthenticated recommendations call.
 * Kept local because MyAnimeListApi.CLIENT_ID is private.
 */
private const val MAL_CLIENT_ID = "d0464a21fd4f1ee11cd033ed3b476946"
private const val NOVELUPDATES_BASE_URL = "https://www.novelupdates.com"

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

/**
 * One "More like this" row: the keyword (genre / author) it was built from
 * and the titles the current source returned for it.
 */
data class RelatedGroup(
    val keyword: String,
    val mangas: List<Manga>,
)

sealed interface MangaRecommendationsUiState {
    data object Loading : MangaRecommendationsUiState

    data class Success(
        val manga: Manga,
        val sourceName: String,
        val sourceSuggestions: List<Manga> = emptyList(),
        val sourceSuggestionsLoading: Boolean = false,
        val groupedSourceSuggestions: Map<String, List<Manga>> = emptyMap(),
        val groupedSourceSuggestionsLoading: Boolean = false,
        val relatedGroups: List<RelatedGroup> = emptyList(),
        val relatedGroupsLoading: Boolean = false,
        val aiPicks: List<Manga> = emptyList(),
        val aiScores: List<Int?> = emptyList(),
        val aiMessage: String? = null,
        val aiLoading: Boolean = false,
        val trackedOn: List<String> = emptyList(),
        val trackerSuggestions: List<Manga> = emptyList(),
        val trackerMessage: String? = null,
        val trackerLoading: Boolean = false,
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
    private val preferenceStore: PreferenceStore = Injekt.get()

    /**
     * When on, the source-suggestion sections search across other catalogue
     * sources in addition to the current one. When off, only the current
     * source is searched. Toggled from the Recommendations screen settings
     * dropdown; defaults to true to preserve existing behaviour.
     */
    private val searchOtherSourcesPref: tachiyomi.core.common.preference.Preference<Boolean> =
        preferenceStore.getBoolean("rec_search_other_sources", true)

    init { load() }

    /**
     * Applies [transform] to the current [MangaRecommendationsUiState.Success] atomically.
     *
     * Each recommendation section updates the state independently, so a plain
     * read-modify-write would let two concurrent jobs clobber each other's
     * results. This keeps every update atomic and leaves the state untouched
     * while the manga itself is still loading.
     */
    private fun updateSuccess(
        transform: (MangaRecommendationsUiState.Success) -> MangaRecommendationsUiState.Success,
    ) {
        _state.update { current ->
            if (current is MangaRecommendationsUiState.Success) {
                transform(current)
            } else {
                current
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val manga = getManga.await(mangaId) ?: run {
                _state.value = MangaRecommendationsUiState.Loading
                return@launch
            }
            val source = sourceManager.getOrStub(manga.source)
            val sourceName = source.getNameForMangaInfo()
            val catalogueSource = source as? CatalogueSource

            // Publish the hero right away so the screen never waits on the
            // recommendation network calls; every section fills itself in.
            // The write is atomic and happens before any section job starts,
            // so no job can be clobbered by it.
            _state.update {
                MangaRecommendationsUiState.Success(
                    manga = manga,
                    sourceName = sourceName,
                    // Every section starts empty with its loading flag set, then
                    // fills itself in independently.
                    sourceSuggestions = emptyList(),
                    sourceSuggestionsLoading = true,
                    groupedSourceSuggestions = emptyMap(),
                    groupedSourceSuggestionsLoading = true,
                    relatedGroups = emptyList(),
                    relatedGroupsLoading = true,
                    aiPicks = emptyList(),
                    aiScores = emptyList(),
                    aiMessage = null,
                    aiLoading = true,
                    trackedOn = emptyList(),
                    trackerSuggestions = emptyList(),
                    trackerMessage = null,
                    trackerLoading = true,
                )
            }

            // Fetch the current source's own suggestions once and share the
            // result so the "More from this source" and "More from sources"
            // sections agree without hitting the source twice. The deferred is
            // cancelled together with the ViewModel.
            val currentSourceSuggestions = viewModelScope.async {
                loadSourceSuggestions(catalogueSource, manga)
            }

            // Each section gets its own job so a slow or failing one cannot hold
            // back the others, and none of them can reset the screen to Loading.
            loadSourceSuggestionsSection(currentSourceSuggestions)
            loadGroupedSourceSuggestionsSection(catalogueSource, manga, currentSourceSuggestions)
            loadRelatedGroupsSection(catalogueSource, manga)
            loadAiSection(catalogueSource, manga)
            loadTrackerSection(catalogueSource, manga)
        }
    }

    /** "More from this source" — the open entry's own source. */
    private fun loadSourceSuggestionsSection(
        currentSourceSuggestions: kotlinx.coroutines.Deferred<List<Manga>>,
    ) {
        viewModelScope.launch {
            val results = runCatching { currentSourceSuggestions.await() }
                .getOrElse {
                    logcat(LogPriority.ERROR, it) { "Failed to load source suggestions" }
                    emptyList()
                }
            updateSuccess { current ->
                current.copy(
                    sourceSuggestions = results,
                    sourceSuggestionsLoading = false,
                )
            }
        }
    }

    /** "More from sources" — one row per catalogue source, including other sources. */
    private fun loadGroupedSourceSuggestionsSection(
        source: CatalogueSource?,
        manga: Manga,
        currentSourceSuggestions: kotlinx.coroutines.Deferred<List<Manga>>,
    ) {
        viewModelScope.launch {
            val results = runCatching {
                // The current source's own results always seed the grouped rows,
                // so turning the "search other sources" toggle off still shows them.
                loadGroupedSourceSuggestions(source, manga, currentSourceSuggestions.await())
            }.getOrElse {
                logcat(LogPriority.ERROR, it) { "Failed to load grouped source suggestions" }
                emptyMap()
            }
            updateSuccess { current ->
                current.copy(
                    groupedSourceSuggestions = results,
                    groupedSourceSuggestionsLoading = false,
                )
            }
        }
    }

    /** "More like this" rows (keyword-grouped, Komikku-style). */
    private fun loadRelatedGroupsSection(source: CatalogueSource?, manga: Manga) {
        viewModelScope.launch {
            val results = runCatching {
                loadRelatedGroups(source = source, manga = manga, alreadyShown = emptyList())
            }.getOrElse {
                logcat(LogPriority.ERROR, it) { "Failed to load related groups" }
                emptyList()
            }
            updateSuccess { current ->
                current.copy(
                    relatedGroups = results,
                    relatedGroupsLoading = false,
                )
            }
        }
    }

    /** "AI picks" — candidate pool plus the AI ranking step. */
    private fun loadAiSection(source: CatalogueSource?, manga: Manga) {
        viewModelScope.launch {
            val aiCandidatePool = runCatching { loadAiCandidatePool(source, manga) }
                .getOrElse {
                    logcat(LogPriority.ERROR, it) { "Failed to build AI candidate pool" }
                    emptyList()
                }

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

            updateSuccess { current ->
                current.copy(
                    aiPicks = picks,
                    aiScores = scores,
                    aiMessage = aiMessage,
                    aiLoading = false,
                )
            }
        }
    }

    /** "Tracker recommendations" — AniList, MyAnimeList and NovelUpdates. */
    private fun loadTrackerSection(source: CatalogueSource?, manga: Manga) {
        viewModelScope.launch {
            val tracks = runCatching { getTracks.await(mangaId) }.getOrElse {
                logcat(LogPriority.ERROR, it) { "Failed to load tracks" }
                emptyList()
            }
            val trackedOn = tracks.mapNotNull { track ->
                trackerManager.get(track.trackerId)?.name
            }

            val trackerResult = runCatching {
                loadTrackerSuggestions(tracks, manga, source)
            }.getOrElse {
                logcat(LogPriority.ERROR, it) { "Failed to load tracker suggestions" }
                TrackerSuggestionResult(emptyList(), "Unexpected error: ${it.message}")
            }

            updateSuccess { current ->
                current.copy(
                    trackedOn = trackedOn,
                    trackerSuggestions = trackerResult.suggestions,
                    trackerMessage = trackerResult.message,
                    trackerLoading = false,
                )
            }
        }
    }

    // --- Tracker recommendations (AniList + MyAnimeList + NovelUpdates) ------

    private data class TrackerSuggestionResult(
        val suggestions: List<Manga>,
        val message: String?,
    )

    /**
     * Names of trackers we know how to fetch recommendations from.
     * AniList and MyAnimeList expose a public recommendations API; NovelUpdates
     * has none, so its recommendations are scraped from the series page using
     * its recommendation section. Notion (and anything else) is intentionally
     * excluded rather than silently failing.
     */
    private enum class SupportedTracker { ANILIST, MYANIMELIST, NOVELUPDATES }

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

        val aniListId = runCatching { trackerManager.aniList.id }.getOrNull()
        val malId = runCatching { trackerManager.myAnimeList.id }.getOrNull()
        val novelUpdatesId = runCatching { trackerManager.novelUpdates.id }.getOrNull()

        val supportedTracks = tracks.mapNotNull { track ->
            when (track.trackerId) {
                aniListId -> SupportedTracker.ANILIST to track
                malId -> SupportedTracker.MYANIMELIST to track
                novelUpdatesId -> SupportedTracker.NOVELUPDATES to track
                else -> null
            }
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

        // Keep the full Result per tracker (instead of collapsing failures to
        // emptyList()) so a real error can be shown on-screen for diagnosis.
        val fetchOutcomes = supportedTracks.map { (tracker, track) ->
            async {
                tracker to runCatching {
                    when (tracker) {
                        SupportedTracker.ANILIST -> fetchWithRetry {
                            fetchAniListRecommendationTitles(track.remoteId)
                        }
                        SupportedTracker.MYANIMELIST -> fetchWithRetry {
                            fetchMalRecommendationTitles(track.remoteId)
                        }
                        SupportedTracker.NOVELUPDATES -> fetchWithRetry {
                            fetchNovelUpdatesRecommendationTitles(track.remoteId)
                        }
                    }
                }
            }
        }.awaitAll()

        fetchOutcomes.forEach { (tracker, result) ->
            result.exceptionOrNull()?.let {
                logcat(LogPriority.ERROR, it) { "Recs: fetch failed for $tracker" }
            }
        }

        val firstError = fetchOutcomes.firstNotNullOfOrNull { (tracker, result) ->
            result.exceptionOrNull()?.let { "$tracker error: ${it.message}" }
        }

        val titleResults = fetchOutcomes
            .flatMap { (_, result) -> result.getOrElse { emptyList() } }
            .distinct()

        if (titleResults.isEmpty()) {
            return@coroutineScope TrackerSuggestionResult(
                emptyList(),
                firstError ?: "No recommendations returned by your linked trackers yet.",
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
    private suspend fun <T> fetchWithRetry(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Tracker fetch failed once, retrying" }
            delay(600)
            block()
        }
    }

    private suspend fun fetchAniListRecommendationTitles(aniListMediaId: Long): List<String> =
        withContext(Dispatchers.IO) {
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
                    val errorBody = runCatching { response.body?.string() }.getOrNull().orEmpty()
                    throw IllegalStateException("AniList HTTP ${response.code}: ${errorBody.take(200)}")
                }
                val body = response.body?.string() ?: return@withContext emptyList()
                val nodes = JSONObject(body)
                    .optJSONObject("data")
                    ?.optJSONObject("Media")
                    ?.optJSONObject("recommendations")
                    ?.optJSONArray("nodes")
                    ?: return@withContext emptyList()
                buildList {
                    for (i in 0 until nodes.length()) {
                        val rec = nodes.getJSONObject(i).optJSONObject("mediaRecommendation") ?: continue
                        val titleObj = rec.optJSONObject("title") ?: continue
                        val title = titleObj.optString("romaji").ifBlank { titleObj.optString("english") }
                        if (title.isNotBlank()) add(title)
                    }
                }
            }
        }

    private suspend fun fetchMalRecommendationTitles(malMangaId: Long): List<String> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://api.myanimelist.net/v2/manga/$malMangaId?fields=recommendations")
                .header("X-MAL-CLIENT-ID", MAL_CLIENT_ID)
                .get()
                .build()

            networkHelper.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("MAL HTTP ${response.code}")
                }
                val body = response.body?.string() ?: return@withContext emptyList()
                val recs = JSONObject(body).optJSONArray("recommendations") ?: return@withContext emptyList()
                buildList {
                    for (i in 0 until recs.length()) {
                        // MAL returns each recommendation as {"node": {...}, "num_recommendations": n}
                        val title = recs.getJSONObject(i)
                            .optJSONObject("node")
                            ?.optString("title")
                        if (!title.isNullOrBlank()) add(title)
                    }
                }
            }
        }

    /**
     * Fetches NovelUpdates recommendations using the series page.
     * NovelUpdates has no official recommendations API; this scrapes the
     * "Related Series" section from the series page.
     * Returns a list of recommended series titles.
     */
    private suspend fun fetchNovelUpdatesRecommendationTitles(remoteId: Long): List<String> =
        withContext(Dispatchers.IO) {
            val seriesUrl = "$NOVELUPDATES_BASE_URL/series/?p=$remoteId"
            val request = Request.Builder()
                .url(seriesUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:141.0) Gecko/20100101 Firefox/141.0")
                .header("Referer", "$NOVELUPDATES_BASE_URL/")
                .get()
                .build()

            try {
                networkHelper.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        logcat(LogPriority.WARN) { "NovelUpdates recs: HTTP ${response.code}" }
                        return@use emptyList()
                    }
                    val document = response.asJsoup()

                    // Extract "Related Series" from the series page
                    // The related series links are typically in .related_series_box or similar
                    val currentTitle = document.select("h1.entry-title, div.seriestitle, h1.seriestitle").text()
                    val relatedTitles = document.select("a[href*=/series/]")
                        .mapNotNull { element ->
                            val href = element.attr("href")
                            val title = element.text().trim()
                            if (href.startsWith("http") || title.isBlank() || title == currentTitle) {
                                null
                            } else {
                                title
                            }
                        }
                        .distinct()
                        .take(15)

                    relatedTitles
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "NovelUpdates recs: failed to fetch recommendations" }
                emptyList()
            }
        }

    // --- Source / AI logic ---------------------------------------------------

    /**
     * "More like this": one row per keyword (top genres + author) of the open entry,
     * each filled by searching the entry's own source. Lookups run at most 2 at a time
     * so rate-limited sources don't choke, and results are de-duplicated across rows
     * and against everything already shown on the screen.
     */
    private suspend fun loadRelatedGroups(
        source: CatalogueSource?,
        manga: Manga,
        alreadyShown: List<Manga>,
    ): List<RelatedGroup> = coroutineScope {
        if (source == null) return@coroutineScope emptyList()
        // Keyword rows are search-based, so honour the existing "no related search" setting.
        if (libraryPreferences.disableRelatedMangasBySearch.get()) return@coroutineScope emptyList()

        val keywords = (
            manga.genre.orEmpty().map { it.trim() }.take(3) +
                listOfNotNull(manga.author?.trim())
            )
            .filter { it.isNotBlank() && !it.equals(manga.title, ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .take(4)

        if (keywords.isEmpty()) return@coroutineScope emptyList()

        val gate = Semaphore(2)

        val found = keywords.map { keyword ->
            async {
                keyword to gate.withPermit {
                    runCatching {
                        source.getSearchManga(
                            page = 1,
                            query = keyword,
                            filters = eu.kanade.tachiyomi.source.model.FilterList(),
                        )?.mangas.orEmpty()
                    }.getOrElse {
                        logcat(LogPriority.ERROR, it) { "Related search failed for keyword='$keyword'" }
                        emptyList()
                    }
                }
            }
        }.awaitAll()

        val seen = HashSet<String>()
        seen.add(manga.url)
        alreadyShown.forEach { seen.add(it.url) }

        found.mapNotNull { (keyword, results) ->
            // seen.add() returns false for repeats, so this also drops dupes inside a row.
            val fresh = results.filter { seen.add(it.url) }.take(12)
            if (fresh.isEmpty()) {
                null
            } else {
                RelatedGroup(
                    keyword = keyword,
                    mangas = fresh.map {
                        networkToLocalManga(it.toDomainManga(sourceId = source.id, isNovel = manga.isNovel))
                    },
                )
            }
        }
    }

    private suspend fun loadGroupedSourceSuggestions(
        source: CatalogueSource?,
        manga: Manga,
        primarySuggestions: List<Manga>,
    ): Map<String, List<Manga>> = coroutineScope {
        val groupedResults = mutableMapOf<String, List<Manga>>()

        if (source != null && primarySuggestions.isNotEmpty()) {
            groupedResults[source.getNameForMangaInfo()] = primarySuggestions
        }

        // Honor the "search other sources" toggle: when off, only the current source
        // contributes to the "More from sources" grouped rows.
        val otherSources = if (searchOtherSourcesPref.get()) {
            sourceManager.getOnlineSources()
                .filterIsInstance<CatalogueSource>()
                .filter { it.id != source?.id && it.isNovelSource == manga.isNovel }
                .shuffled()
                .take(4)
        } else {
            emptyList()
        }

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
                    .distinctBy { it.url }
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
            .distinctBy { it.url }
            .take(25)
            .map { networkToLocalManga(it.toDomainManga(sourceId = source.id, isNovel = manga.isNovel)) }
    }

    private suspend fun loadAiCandidatePool(
        currentSource: CatalogueSource?,
        manga: Manga,
    ): List<MangaRecommendable> {
        return coroutineScope {
            // Honor the "search other sources" toggle for the AI candidate pool.
            // When off, only use the current source for candidates.
            val searchOther = searchOtherSourcesPref.get()
            val otherSources = if (searchOther) {
                sourceManager.getOnlineSources()
                    .filterIsInstance<CatalogueSource>()
                    .filter { it.id != currentSource?.id && it.isNovelSource() == manga.isNovel }
                    .shuffled()
                    .take(6)
            } else {
                emptyList()
            }

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
