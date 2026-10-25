package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Backup representation of one original manga linked to a virtual merged manga.
 *
 * Database IDs are deliberately not stored as identifiers. They can change
 * when a backup is restored on another device. Manga are resolved by source
 * ID and URL during restoration.
 */
@Serializable
data class BackupMergedReference(
    // Identity of the virtual merged manga.
    @ProtoNumber(1)
    val mergeMangaUrl: String,

    @ProtoNumber(2)
    val mergeMangaSourceId: Long,

    // Identity of the original manga linked into the merge.
    @ProtoNumber(3)
    val mangaUrl: String,

    @ProtoNumber(4)
    val mangaSourceId: Long,

    // Settings for this merge reference.
    @ProtoNumber(5)
    val isInfoManga: Boolean,

    @ProtoNumber(6)
    val getChapterUpdates: Boolean,

    @ProtoNumber(7)
    val chapterSortMode: Int,

    @ProtoNumber(8)
    val chapterPriority: Int,

    @ProtoNumber(9)
    val downloadChapters: Boolean,
)