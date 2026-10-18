
package eu.kanade.tachiyomi.source.online.all

import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.copy
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import mihon.domain.chapter.interactor.FilterChaptersForDownload
import mihon.domain.source.interactor.UpdateMangaFromRemote
import okhttp3.Response
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.merged.interactor.GetMergedReferences
import tachiyomi.domain.merged.model.MergedMangaReference
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.injectLazy

/**
 * Virtual source for merged manga library entries.
 *
 * It combines references to manga from real sources. Its own chapter and
 * image endpoints are intentionally unsupported; chapter updates are
 * collected from the referenced sources instead.
 */
class MergedSource : HttpSource() {

    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId by injectLazy()
    private val getMergedReferences: GetMergedReferences by injectLazy()
    private val networkToLocalManga: NetworkToLocalManga by injectLazy()
    private val updateMangaFromRemote: UpdateMangaFromRemote by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val downloadManager: DownloadManager by injectLazy()
    private val filterChaptersForDownload: FilterChaptersForDownload by injectLazy()

    override val id: Long = ID
    override val baseUrl: String = ""
    override val lang: String = "all"
    override val supportsLatest: Boolean = false
    override val name: String = "MergedSource"

    @Deprecated(
        "Use the combined suspend API instead",
        replaceWith = ReplaceWith("getMangaUpdate"),
    )
    override fun fetchChapterList(manga: SManga) =
        throw UnsupportedOperationException()

    override suspend fun getImage(page: Page): Response =
        throw UnsupportedOperationException()

    @Deprecated(
        "Use the suspend API instead",
        replaceWith = ReplaceWith("getImageUrl"),
    )
    override fun fetchImageUrl(page: Page) =
        throw UnsupportedOperationException()

    override suspend fun getImageUrl(page: Page) =
        throw UnsupportedOperationException()

    @Deprecated(
        "Use the suspend API instead",
        replaceWith = ReplaceWith("getPageList"),
    )
    override fun fetchPageList(chapter: SChapter) =
        throw UnsupportedOperationException()

    override suspend fun getPageList(chapter: SChapter) =
        throw UnsupportedOperationException()

    @Deprecated(
        "Use the suspend API instead",
        replaceWith = ReplaceWith("getLatestUpdates"),
    )
    override fun fetchLatestUpdates(page: Int) =
        throw UnsupportedOperationException()

    @Deprecated(
        "Use the suspend API instead",
        replaceWith = ReplaceWith("getPopularManga"),
    )
    override fun fetchPopularManga(page: Int) =
        throw UnsupportedOperationException()

    @Deprecated(
        "Use the suspend API instead",
        replaceWith = ReplaceWith("getPopularManga"),
    )
    override suspend fun getPopularManga(page: Int) =
        throw UnsupportedOperationException()

    override suspend fun getLatestUpdates(page: Int) =
        throw UnsupportedOperationException()

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedManga = if (fetchDetails) {
            getMangaDetails(manga)
        } else {
            null
        }

        return SMangaUpdate(updatedManga ?: manga, chapters)
    }

    /**
     * Gets the information manga for the merged entry.
     *
     * The explicitly selected info reference takes priority. If none is
     * selected, the first reference that is not the virtual merge entry
     * is used as a fallback.
     */
    private suspend fun getMangaDetails(manga: SManga): SManga =
        withIOContext {
            val mergedManga = requireNotNull(
                getMangaByUrlAndSourceId.await(manga.url, id),
            ) {
                "Merged manga not found in the database"
            }

            val references = getMergedReferences.await(mergedManga.id)

            require(references.isNotEmpty()) {
                "Merged manga references are empty; the merge may be corrupted"
            }

            require(
                !(references.size == 1 && references.first().mangaSourceId == ID),
            ) {
                "Merged manga contains only its virtual reference"
            }

            val infoReference = references.firstOrNull {
                it.isInfoManga
            } ?: references.firstOrNull {
                it.mangaId != it.mergeId
            }

            val infoManga = infoReference?.let { reference ->
                getMangaByUrlAndSourceId
                    .await(reference.mangaUrl, reference.mangaSourceId)
                    ?.toSManga()
            }

            (infoManga ?: mergedManga.toSManga()).copy(
                url = manga.url,
            )
        }

    /**
     * Fetches chapter updates from the real sources referenced by a merged
     * manga. At most five source groups are processed concurrently.
     *
     * References with getChapterUpdates disabled are skipped. When
     * downloadChapters is enabled, each reference's downloadChapters
     * setting also controls whether its new chapters are downloaded.
     */
    suspend fun fetchChaptersAndSync(
        manga: Manga,
        downloadChapters: Boolean = true,
    ): List<Chapter> {
        val references = getMergedReferences.await(manga.id)

        require(references.isNotEmpty()) {
            "Merged manga references are empty; the merge may be corrupted"
        }

        val semaphore = Semaphore(5)

        return supervisorScope {
            val results = references
                .groupBy(MergedMangaReference::mangaSourceId)
                .minus(ID)
                .map { (_, sourceReferences) ->
                    async {
                        semaphore.withPermit {
                            sourceReferences.flatMap { reference ->
                                try {
                                    val (source, loadedManga, settings) =
                                        reference.load()

                                    if (
                                        loadedManga != null &&
                                        settings.getChapterUpdates
                                    ) {
                                        val newChapters = updateMangaFromRemote(
                                            source = source,
                                            manga = loadedManga,
                                            fetchChapters = true,
                                        ).getOrThrow().newChapters

                                        if (
                                            downloadChapters &&
                                            settings.downloadChapters
                                        ) {
                                            val chaptersToDownload =
                                                filterChaptersForDownload.await(
                                                    manga,
                                                    newChapters,
                                                )

                                            if (chaptersToDownload.isNotEmpty()) {
                                                downloadManager.downloadChapters(
                                                    loadedManga,
                                                    chaptersToDownload,
                                                )
                                            }
                                        }

                                        newChapters
                                    } else {
                                        emptyList()
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    // A failing source should not prevent other
                                    // sources from being processed.
                                    throw SourceUpdateException(e)
                                }
                            }
                        }
                    }
                }

            // awaitAll propagates a failed child even within supervisorScope.
            // Each source group therefore needs to capture failures if we want
            // other source groups to finish successfully.
            val completed = results.map { deferred ->
                try {
                    deferred.await() to null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList<Chapter>() to e
                }
            }

            val chapters = completed.flatMap { it.first }
            val failure = completed.firstNotNullOfOrNull { it.second }

            if (failure != null) {
                throw failure
            }

            chapters
        }
    }

    /**
     * Loads the local manga referenced by this record, creating and fetching
     * it from its real source if it does not yet exist locally.
     */
    private suspend fun MergedMangaReference.load(): LoadedMangaSource {
        var manga = getMangaByUrlAndSourceId.await(
            mangaUrl,
            mangaSourceId,
        )

        val source = sourceManager.getOrStub(
            manga?.source ?: mangaSourceId,
        )

        if (manga == null) {
            val newManga = networkToLocalManga(
                Manga.create().copy(
                    source = mangaSourceId,
                    url = mangaUrl,
                ),
            )

            manga = updateMangaFromRemote(
                source = source,
                manga = newManga,
                fetchDetails = true,
            ).getOrThrow().manga
        }

        return LoadedMangaSource(
            source = source,
            manga = manga,
            reference = this,
        )
    }

    private data class LoadedMangaSource(
        val source: Source,
        val manga: Manga?,
        val reference: MergedMangaReference,
    )

    private class SourceUpdateException(
        cause: Throwable,
    ) : Exception("Failed to update a merged manga source", cause)

    companion object {
        /**
         * Reserved identifier for the virtual merged-manga source.
         *
         * This value must be used consistently by the merge creation logic,
         * source registration, and database references.
         */
        const val ID: Long = 4577113990024601157L
    }
}
