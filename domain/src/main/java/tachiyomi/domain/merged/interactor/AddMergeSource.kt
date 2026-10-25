package tachiyomi.domain.merged.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.UpdateManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.merged.model.MergedMangaReference
import tachiyomi.domain.merged.repository.MergedMangaRepository

class AddMergeSource(
    private val mergedMangaRepository: MergedMangaRepository,
    private val updateManga: UpdateManga,
) {

    suspend fun await(
        mergeId: Long,
        manga: Manga,
    ): Result {
        return try {
            val references = mergedMangaRepository.getReferencesById(mergeId)

            require(references.isNotEmpty()) {
                "Cannot add a source to a merge that does not exist"
            }

            require(
                references.none {
                    it.mangaId == manga.id &&
                        it.mangaSourceId == manga.source
                },
            ) {
                "This manga is already linked to the merge"
            }

            val nextPriority = (references.maxOfOrNull { it.chapterPriority } ?: -1) + 1

            val reference = MergedMangaReference(
                id = 0,
                isInfoManga = false,
                getChapterUpdates = true,
                chapterSortMode = 0,
                chapterPriority = nextPriority,
                downloadChapters = true,
                mergeId = mergeId,
                mangaId = manga.id,
                mangaUrl = manga.url,
                mangaSourceId = manga.source,
            )

            mergedMangaRepository.insert(reference)

            if (manga.favorite) {
                updateManga.awaitUpdateFavorite(manga.id, false)
            }

            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result

        data class InternalError(
            val error: Throwable,
        ) : Result
    }
}
