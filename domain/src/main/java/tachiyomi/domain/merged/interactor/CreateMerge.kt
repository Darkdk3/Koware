package tachiyomi.domain.merged.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.merged.model.MergedMangaReference
import tachiyomi.domain.merged.repository.MergedMangaRepository
import java.util.UUID

/**
 * Creates a virtual merged manga and links the selected manga entries to it.
 *
 * The first selected manga becomes the information-primary manga.
 * Selection order determines chapter priority.
 *
 * Original manga entries are retained because the merged source relies on
 * them to retrieve chapters. Favorited originals are unfavorited only after
 * all merge references have been inserted successfully.
 */
class CreateMerge(
    private val mergedMangaRepository: MergedMangaRepository,
    private val networkToLocalManga: NetworkToLocalManga,
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(mangaToLink: List<Manga>): Result =
        withNonCancellableContext {
            try {
                validateMangaToLink(mangaToLink)

                val primary = mangaToLink.first()

                // Keep this ID synchronized with MergedSource.ID in the app module.
                val virtualManga = networkToLocalManga(
                    Manga.create().copy(
                        source = MERGED_SOURCE_ID,
                        url = "merged://${UUID.randomUUID()}",
                        title = primary.title,
                        thumbnailUrl = primary.thumbnailUrl,
                        initialized = true,
                        isNovel = primary.isNovel,
                    ),
                )

                // Add the virtual merged entry to the library.
                val addedToLibrary = mangaRepository.update(
                    MangaUpdate(
                        id = virtualManga.id,
                        favorite = true,
                    ),
                )

                if (!addedToLibrary) {
                    error(
                        "Could not add the merged manga to the library. " +
                            "Merged manga ID: ${virtualManga.id}",
                    )
                }

                // Create one reference for each selected original manga.
                // The first selected manga is the information-primary entry.
                val references = mangaToLink.mapIndexed { index, manga ->
                    MergedMangaReference(
                        id = 0L,
                        isInfoManga = index == 0,
                        getChapterUpdates = true,
                        chapterSortMode = 0,
                        chapterPriority = index,
                        downloadChapters = true,
                        mergeId = virtualManga.id,
                        mangaId = manga.id,
                        mangaUrl = manga.url,
                        mangaSourceId = manga.source,
                    )
                }

                // insertAll is transactional in the repository implementation.
                mergedMangaRepository.insertAll(references)

                // Only unfavorite the originals after references have been saved.
                val originalsToUnfavorite = mangaToLink
                    .filter { it.favorite }
                    .map { manga ->
                        MangaUpdate(
                            id = manga.id,
                            favorite = false,
                        )
                    }

                if (originalsToUnfavorite.isNotEmpty()) {
                    val updated = mangaRepository.updateAll(
                        originalsToUnfavorite,
                    )

                    if (!updated) {
                        error(
                            "Merge references were created, but the original " +
                                "manga could not be removed from the library.",
                        )
                    }
                }

                Result.Success(
                    mergedMangaId = virtualManga.id,
                )
            } catch (e: Exception) {
                logcat(
                    LogPriority.ERROR,
                    e,
                )

                Result.InternalError(e)
            }
        }

    /**
     * Rejects invalid selections before creating a virtual manga entry.
     */
    private fun validateMangaToLink(
        mangaToLink: List<Manga>,
    ) {
        require(mangaToLink.size >= 2) {
            "Select at least two manga to merge."
        }

        require(mangaToLink.map { it.id }.distinct().size == mangaToLink.size) {
            "The same manga cannot be selected more than once."
        }

        require(mangaToLink.all { it.id > 0L }) {
            "Every selected manga must already exist in the database."
        }

        require(mangaToLink.none { it.source == MERGED_SOURCE_ID }) {
            "A merged manga cannot be selected as an original manga."
        }

        require(mangaToLink.map { it.isNovel }.distinct().size == 1) {
            "Manga and novels cannot be merged together."
        }
    }

    sealed interface Result {

        data class Success(
            val mergedMangaId: Long,
        ) : Result

        data class InternalError(
            val error: Throwable,
        ) : Result
    }

    companion object {

        /**
         * Mirrors:
         * eu.kanade.tachiyomi.source.online.all.MergedSource.ID
         *
         * Keep this value synchronized with the app module.
         */
        const val MERGED_SOURCE_ID = 4577113990024601157L
    }
}