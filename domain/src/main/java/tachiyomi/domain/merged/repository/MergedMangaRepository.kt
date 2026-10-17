
package tachiyomi.domain.merged.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.merged.model.MergedMangaReference

/**
 * Repository for the relationships between a virtual merged manga
 * and its original manga entries.
 *
 * A merge ID identifies the virtual manga. Each reference identifies
 * one original manga and stores the settings used when that manga
 * participates in the merge.
 *
 * This repository only manages merge-reference records. Creating or
 * deleting the virtual manga and restoring original manga library
 * state must be handled by the appropriate merge interactors.
 */
interface MergedMangaRepository {

    /**
     * Returns all original manga references belonging to a merged manga.
     */
    suspend fun getReferencesById(
        mergeId: Long,
    ): List<MergedMangaReference>

    /**
     * Observes changes to the references belonging to a merged manga.
     */
    fun getReferencesByIdAsFlow(
        mergeId: Long,
    ): Flow<List<MergedMangaReference>>

    /**
     * Returns one reference by its database ID, or null if it does not exist.
     */
    suspend fun getById(
        id: Long,
    ): MergedMangaReference?

    /**
     * Fetches references for multiple merged manga IDs.
     *
     * The returned map is keyed by merge ID. IDs without references
     * are omitted from the result.
     *
     * Implementations should handle large lists in batches to avoid
     * exceeding SQLite's bind-parameter limit.
     */
    suspend fun getReferencesByIds(
        mergeIds: List<Long>,
    ): Map<Long, List<MergedMangaReference>>

    /**
     * Inserts one merge reference.
     */
    suspend fun insert(
        reference: MergedMangaReference,
    )

    /**
     * Inserts multiple references.
     *
     * Implementations should perform this operation transactionally,
     * so a failure does not leave only part of a merge's references.
     */
    suspend fun insertAll(
        references: List<MergedMangaReference>,
    )

    /**
     * Updates the settings associated with one merge reference.
     */
    suspend fun updateSettings(
        id: Long,
        isInfoManga: Boolean,
        getChapterUpdates: Boolean,
        chapterSortMode: Int,
        chapterPriority: Int,
        downloadChapters: Boolean,
    )

    /**
     * Deletes one reference by its database ID.
     */
    suspend fun deleteById(
        id: Long,
    )

    /**
     * Deletes all references associated with a virtual merged manga.
     *
     * This does not itself delete the virtual manga or restore the
     * original manga's library state.
     */
    suspend fun deleteByMergeId(
        mergeId: Long,
    )
}
