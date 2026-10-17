
package tachiyomi.domain.merged.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.interactor.UpdateManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.merged.model.MergedMangaReference
import tachiyomi.domain.merged.repository.MergedMangaRepository
import java.util.UUID

/**
 * Creates a virtual manga entry and links the selected original manga to it.
 *
 * The first selected manga becomes the information-primary entry and receives
 * chapter priority zero. Remaining entries retain their selection order.
 *
 * Original manga rows are retained because the merged source needs them to
 * retrieve chapters. Originally favourited manga are unfavourited after the
 * merge references have been inserted.
 */
class CreateMerge(
    private val mergedMangaRepository: MergedMangaRepository,
    private val networkToLocalManga: NetworkToLocalManga,
    private val updateManga: UpdateManga,
) {

    suspend fun await(mangaToLink: List<Manga>): Result =
        withNonCancellableContext {
            try {
                validateMangaToLink(mangaToLink)

                val primary = mangaToLink.first()

                // Keep this ID in sync with MergedSource.ID in the app module.
                // The domain module cannot import the app module without
                // introducing a dependency cycle.
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

                updateManga.awaitUpdateFavorite(
                    virtualManga.id,
                    true,
                )

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

                mergedMangaRepository.insertAll(references)

                // Only hide the original entries after their merge references
                // have been saved successfully.
                mangaToLink
                    .filter { it.favorite }
                    .forEach { manga ->
                        updateManga.awaitUpdateFavorite(
                            manga.id,
                            false,
                        )
                    }

                Result.Success(
                    mergedMangaId = virtualManga.id,
                )
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                Result.InternalError(e)
            }
        }

    private fun validateMangaToLink(
        mangaToLink: List<Manga>,
    ) {
        require(mangaToLink.size >= 2) {
            "Need at least 2 manga to merge"
        }

        require(mangaToLink.map { it.id }.distinct().size == mangaToLink.size) {
            "The same manga cannot be selected more than once"
        }

        require(mangaToLink.all { it.id > 0L }) {
            "Every selected manga must already exist in the database"
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
         * Mirrors eu.kanade.tachiyomi.source.online.all.MergedSource.ID.
         *
         * Keep this value synchronized with the app module's MergedSource.
         */
        const val MERGED_SOURCE_ID = 4577113990024601157L
    }
}
