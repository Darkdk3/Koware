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
 * Creates a merged manga or adds a source to an existing merge.
 *
 * If one of the selected entries is already a merged manga, the other
 * entry is added to that merge instead of creating another virtual entry.
 */
class CreateMerge(
    private val mergedMangaRepository: MergedMangaRepository,
    private val networkToLocalManga: NetworkToLocalManga,
    private val mangaRepository: MangaRepository,
    private val addMergeSource: AddMergeSource,
) {

    suspend fun await(mangaToLink: List<Manga>): Result =
        withNonCancellableContext {
            try {
                validateSelection(mangaToLink)

                val mergedEntries = mangaToLink.filter {
                    it.source == MERGED_SOURCE_ID
                }

                // Existing merge selected: add the other manga to it.
                if (mergedEntries.isNotEmpty()) {
                    require(mergedEntries.size == 1) {
                        "Select only one existing merged manga."
                    }

                    val existingMerge = mergedEntries.single()
                    val mangaToAdd = mangaToLink.first {
                        it.id != existingMerge.id
                    }

                    val addResult = addMergeSource.await(
                        mergeId = existingMerge.id,
                        manga = mangaToAdd,
                    )

                    return@withNonCancellableContext when (addResult) {
                        AddMergeSource.Result.Success -> {
                            Result.Success(existingMerge.id)
                        }

                        is AddMergeSource.Result.InternalError -> {
                            Result.InternalError(addResult.error)
                        }
                    }
                }

                // No existing merge selected: create a new one.
                createNewMerge(mangaToLink)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                Result.InternalError(e)
            }
        }

    private suspend fun createNewMerge(
        mangaToLink: List<Manga>,
    ): Result {
        val primary = mangaToLink.first()

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

        // Persist all source references before favoriting the virtual entry.
        mergedMangaRepository.insertAll(references)

        val addedToLibrary = mangaRepository.update(
            MangaUpdate(
                id = virtualManga.id,
                favorite = true,
            ),
        )

        check(addedToLibrary) {
            "Could not add the merged manga to the library."
        }

        // Remove the originals from the library only after the merge exists.
        val originalsToUnfavorite = mangaToLink
            .filter { it.favorite }
            .map {
                MangaUpdate(
                    id = it.id,
                    favorite = false,
                )
            }

        if (originalsToUnfavorite.isNotEmpty()) {
            val updated = mangaRepository.updateAll(originalsToUnfavorite)

            check(updated) {
                "The merge was created, but the original manga could not " +
                    "be removed from the library."
            }
        }

        return Result.Success(virtualManga.id)
    }

    private fun validateSelection(
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

        require(mangaToLink.map { it.isNovel }.distinct().size == 1) {
            "Manga and novels cannot be merged together."
        }

        val mergedEntries = mangaToLink.filter {
            it.source == MERGED_SOURCE_ID
        }

        require(mergedEntries.size <= 1) {
            "Only one existing merged manga can be selected."
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
        const val MERGED_SOURCE_ID: Long = 4577113990024601157L
    }
}