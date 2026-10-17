
package tachiyomi.data.merged

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.merged.model.MergedMangaReference
import tachiyomi.domain.merged.repository.MergedMangaRepository

class MergedMangaRepositoryImpl(
    private val database: Database,
) : MergedMangaRepository {

    override suspend fun getReferencesById(
        mergeId: Long,
    ): List<MergedMangaReference> {
        return database.mergedQueries
            .getReferencesById(mergeId, ::mapMergedMangaReference)
            .awaitAsList()
    }

    override fun getReferencesByIdAsFlow(
        mergeId: Long,
    ): Flow<List<MergedMangaReference>> {
        return database.mergedQueries
            .getReferencesById(mergeId, ::mapMergedMangaReference)
            .subscribeToList()
    }

    override suspend fun getById(
        id: Long,
    ): MergedMangaReference? {
        return database.mergedQueries
            .getById(id, ::mapMergedMangaReference)
            .awaitAsOneOrNull()
    }

    override suspend fun getReferencesByIds(
        mergeIds: List<Long>,
    ): Map<Long, List<MergedMangaReference>> {
        if (mergeIds.isEmpty()) {
            return emptyMap()
        }

        val resultMap = mutableMapOf<Long, MutableList<MergedMangaReference>>()

        // Keep each query below SQLite's bind-parameter limit.
        mergeIds.distinct().chunked(500).forEach { chunk ->
            database.mergedQueries
                .getReferencesByIds(chunk) { id,
                    isInfoManga,
                    getChapterUpdates,
                    chapterSortMode,
                    chapterPriority,
                    downloadChapters,
                    mergeId,
                    mangaId,
                    mangaUrl,
                    mangaSourceId,
                ->
                    val reference = mapMergedMangaReference(
                        id = id,
                        isInfoManga = isInfoManga,
                        getChapterUpdates = getChapterUpdates,
                        chapterSortMode = chapterSortMode,
                        chapterPriority = chapterPriority,
                        downloadChapters = downloadChapters,
                        mergeId = mergeId,
                        mangaId = mangaId,
                        mangaUrl = mangaUrl,
                        mangaSourceId = mangaSourceId,
                    )

                    resultMap
                        .getOrPut(mergeId) { mutableListOf() }
                        .add(reference)
                }
                .awaitAsList()
        }

        return resultMap
    }

    override suspend fun insert(
        reference: MergedMangaReference,
    ) {
        database.mergedQueries.insert(
            info_manga = reference.isInfoManga,
            get_chapter_updates = reference.getChapterUpdates,
            chapter_sort_mode = reference.chapterSortMode.toLong(),
            chapter_priority = reference.chapterPriority.toLong(),
            download_chapters = reference.downloadChapters,
            merge_id = reference.mergeId,
            manga_id = reference.mangaId,
            manga_url = reference.mangaUrl,
            manga_source_id = reference.mangaSourceId,
        )
    }

    override suspend fun insertAll(
        references: List<MergedMangaReference>,
    ) {
        if (references.isEmpty()) {
            return
        }

        database.transaction {
            references.forEach { reference ->
                database.mergedQueries.insert(
                    info_manga = reference.isInfoManga,
                    get_chapter_updates = reference.getChapterUpdates,
                    chapter_sort_mode = reference.chapterSortMode.toLong(),
                    chapter_priority = reference.chapterPriority.toLong(),
                    download_chapters = reference.downloadChapters,
                    merge_id = reference.mergeId,
                    manga_id = reference.mangaId,
                    manga_url = reference.mangaUrl,
                    manga_source_id = reference.mangaSourceId,
                )
            }
        }
    }

    override suspend fun updateSettings(
        id: Long,
        isInfoManga: Boolean,
        getChapterUpdates: Boolean,
        chapterSortMode: Int,
        chapterPriority: Int,
        downloadChapters: Boolean,
    ) {
        database.mergedQueries.updateSettings(
            isInfoManga = isInfoManga,
            getChapterUpdates = getChapterUpdates,
            chapterSortMode = chapterSortMode.toLong(),
            chapterPriority = chapterPriority.toLong(),
            downloadChapters = downloadChapters,
            id = id,
        )
    }

    override suspend fun deleteById(
        id: Long,
    ) {
        database.mergedQueries.deleteById(id = id)
    }

    override suspend fun deleteByMergeId(
        mergeId: Long,
    ) {
        database.mergedQueries.deleteByMergeId(mergeId = mergeId)
    }

    private fun mapMergedMangaReference(
        id: Long,
        isInfoManga: Boolean,
        getChapterUpdates: Boolean,
        chapterSortMode: Long,
        chapterPriority: Long,
        downloadChapters: Boolean,
        mergeId: Long,
        mangaId: Long,
        mangaUrl: String,
        mangaSourceId: Long,
    ): MergedMangaReference {
        return MergedMangaReference(
            id = id,
            isInfoManga = isInfoManga,
            getChapterUpdates = getChapterUpdates,
            chapterSortMode = chapterSortMode.toInt(),
            chapterPriority = chapterPriority.toInt(),
            downloadChapters = downloadChapters,
            mergeId = mergeId,
            mangaId = mangaId,
            mangaUrl = mangaUrl,
            mangaSourceId = mangaSourceId,
        )
    }
}
