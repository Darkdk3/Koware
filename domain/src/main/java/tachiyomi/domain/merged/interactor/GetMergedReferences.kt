
package tachiyomi.domain.merged.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.merged.model.MergedMangaReference
import tachiyomi.domain.merged.repository.MergedMangaRepository

/**
 * Retrieves the original manga entries linked to a virtual merged manga.
 *
 * This interactor does not create, update, or delete merge references.
 */
class GetMergedReferences(
    private val mergedMangaRepository: MergedMangaRepository,
) {

    /**
     * Fetches the current references once.
     *
     * @param mergeId ID of the virtual merged manga.
     */
    suspend fun await(
        mergeId: Long,
    ): List<MergedMangaReference> {
        return mergedMangaRepository.getReferencesById(mergeId)
    }

    /**
     * Observes reference changes for a virtual merged manga.
     *
     * @param mergeId ID of the virtual merged manga.
     */
    fun subscribe(
        mergeId: Long,
    ): Flow<List<MergedMangaReference>> {
        return mergedMangaRepository.getReferencesByIdAsFlow(mergeId)
    }
}
