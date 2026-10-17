
package tachiyomi.domain.merged.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.merged.repository.MergedMangaRepository

/**
 * Updates the settings associated with a manga inside a merged manga group.
 *
 * The supplied [id] identifies the merged manga reference whose settings
 * should be changed.
 */
class UpdateMergedSettings(
    private val mergedMangaRepository: MergedMangaRepository,
) {

    /**
     * Updates the reference settings.
     *
     * The operation runs inside [withNonCancellableContext], matching the
     * original merge implementation's behavior.
     */
    suspend fun await(
        id: Long,
        isInfoManga: Boolean,
        getChapterUpdates: Boolean,
        chapterSortMode: Int,
        chapterPriority: Int,
        downloadChapters: Boolean,
    ): Result = withNonCancellableContext {
        try {
            mergedMangaRepository.updateSettings(
                id = id,
                isInfoManga = isInfoManga,
                getChapterUpdates = getChapterUpdates,
                chapterSortMode = chapterSortMode,
                chapterPriority = chapterPriority,
                downloadChapters = downloadChapters,
            )

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
