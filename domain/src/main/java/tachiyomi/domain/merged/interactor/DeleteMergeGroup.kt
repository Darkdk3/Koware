
package tachiyomi.domain.merged.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.merged.repository.MergedMangaRepository

/**
 * Deletes all references associated with a merged manga group.
 *
 * This removes the group's reference records. It does not independently
 * delete the virtual manga entry or the original manga entries.
 */
class DeleteMergeGroup(
    private val mergedMangaRepository: MergedMangaRepository,
) {

    /**
     * Deletes every merged manga reference associated with [mergeId].
     */
    suspend fun await(mergeId: Long): Result = withNonCancellableContext {
        try {
            mergedMangaRepository.deleteByMergeId(mergeId)
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
