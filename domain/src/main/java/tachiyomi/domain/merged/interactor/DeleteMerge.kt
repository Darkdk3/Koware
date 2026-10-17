
package tachiyomi.domain.merged.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.merged.repository.MergedMangaRepository

/**
 * Removes a single source reference from a merged manga group.
 *
 * This operation does not remove the other references belonging to the group.
 * Use [DeleteMergeGroup] to remove every reference for a merge.
 */
class DeleteMerge(
    private val mergedMangaRepository: MergedMangaRepository,
) {

    /**
     * Deletes the merged manga reference identified by [referenceId].
     */
    suspend fun await(referenceId: Long): Result = withNonCancellableContext {
        try {
            mergedMangaRepository.deleteById(referenceId)
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
