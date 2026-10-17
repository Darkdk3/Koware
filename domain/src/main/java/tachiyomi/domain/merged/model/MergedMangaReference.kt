
package tachiyomi.domain.merged.model

import java.io.Serializable

/**
 * Represents a manga linked to a merged manga entry.
 *
 * [mergeId] is the ID of the virtual merged manga.
 * [mangaId] is the ID of the original manga entry.
 *
 * The source ID and URL are stored separately so the merge feature
 * can identify the original source even when the merged manga is
 * represented by an internal source.
 */
data class MergedMangaReference(
    val id: Long,
    val isInfoManga: Boolean,
    val getChapterUpdates: Boolean,
    val chapterSortMode: Int,
    val chapterPriority: Int,
    val downloadChapters: Boolean,
    val mergeId: Long,
    val mangaId: Long,
    val mangaUrl: String,
    val mangaSourceId: Long,
) : Serializable
