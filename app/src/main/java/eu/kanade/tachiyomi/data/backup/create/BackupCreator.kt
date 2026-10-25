package eu.kanade.tachiyomi.data.backup.create

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.create.creators.CategoriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.ExtensionStoresBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.PreferenceBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.SourcesBackupCreator
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupMergedReference
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.source.isNovelSource
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import okio.buffer
import okio.gzip
import okio.sink
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.merged.interactor.GetMergedReferences
import tachiyomi.domain.merged.model.MergedMangaReference
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Clock

class BackupCreator(
    private val context: Context,
    private val isAutoBackup: Boolean,

    private val parser: ProtoBuf = Injekt.get(),
    private val getFavorites: GetFavorites = Injekt.get(),
    private val backupPreferences: BackupPreferences = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val getMergedReferences: GetMergedReferences = Injekt.get(),

    private val categoriesBackupCreator: CategoriesBackupCreator = CategoriesBackupCreator(),
    private val mangaBackupCreator: MangaBackupCreator = MangaBackupCreator(),
    private val preferenceBackupCreator: PreferenceBackupCreator = PreferenceBackupCreator(),
    private val extensionStoresBackupCreator: ExtensionStoresBackupCreator = ExtensionStoresBackupCreator(),
    private val sourcesBackupCreator: SourcesBackupCreator = SourcesBackupCreator(),
) {

    private val mangaBatchSize = 20
    private val mangaPageSize = 500L
    private val maxMangaInMemory = 200

    suspend fun backup(
        uri: Uri,
        options: BackupOptions,
        onProgress: ((Int, Int) -> Unit)? = null,
    ): String {
        var file: UniFile? = null

        try {
            file = if (isAutoBackup) {
                val dir = UniFile.fromUri(context, uri)

                dir?.listFiles { _, filename -> FILENAME_REGEX.matches(filename) }
                    .orEmpty()
                    .sortedByDescending { it.name }
                    .drop(MAX_AUTO_BACKUPS - 1)
                    .forEach { it.delete() }

                dir?.createFile(getFilename())
            } else {
                UniFile.fromUri(context, uri)
            }

            if (file == null || !file.isFile) {
                throw IllegalStateException(
                    context.stringResource(MR.strings.create_backup_file_error),
                )
            }

            val backupCategories = backupCategories(options)
            val backupAppPrefs = backupAppPreferences(options)
            val backupExtensionStores = backupExtensionStores(options)
            val backupSourcePrefs = backupSourcePreferences(options)

            val outputStream = file.openOutputStream()
            (outputStream as? FileOutputStream)?.channel?.truncate(0)
            val gzipOut = outputStream.sink().gzip().buffer()

            val sourceIds = mutableSetOf<Long>()
            val mergedReferences = linkedMapOf<String, BackupMergedReference>()

            var written = 0
            val approxTotal = if (options.libraryEntries) {
                mangaRepository.getFavoritesCount().toInt()
            } else {
                0
            }

            suspend fun processBatch(batch: List<Manga>) {
                val filtered = batch.filter { manga ->
                    val isNovel = manga.isNovel ||
                        sourceManager.getOrStub(manga.source).isNovelSource()

                    when {
                        options.includeManga && options.includeNovels -> true
                        options.includeManga && !isNovel -> true
                        options.includeNovels && isNovel -> true
                        else -> false
                    }
                }

                if (filtered.isEmpty()) return

                val fullBatch = filtered.map { manga ->
                    if (manga.description == null) {
                        mangaRepository.getMangaById(manga.id)
                    } else {
                        manga
                    }
                }

                mangaBackupCreator.backupMangaStream(fullBatch, options).collect { backupManga ->
                    sourceIds.add(backupManga.source)

                    val bytes = parser.encodeToByteArray(
                        BackupManga.serializer(),
                        backupManga,
                    )

                    writeProtoField(gzipOut.outputStream(), 1, bytes)

                    /*
                     * Save merge-reference information for each virtual merged manga.
                     *
                     * The virtual manga's source ID and URL identify the merge entry.
                     * The original manga's source ID and URL are saved in each reference.
                     */
                    val sourceManga = fullBatch.firstOrNull {
                        it.url == backupManga.url &&
                            it.source == backupManga.source
                    }

                    if (sourceManga != null) {
                        val references = getMergedReferences.await(sourceManga.id)

                        references.forEach { reference ->
                            val backupReference = reference.toBackupMergedReference(
                                mergeManga = sourceManga,
                            )

                            val key = buildString {
                                append(backupReference.mergeMangaSourceId)
                                append('|')
                                append(backupReference.mergeMangaUrl)
                                append('|')
                                append(backupReference.mangaSourceId)
                                append('|')
                                append(backupReference.mangaUrl)
                            }

                            mergedReferences[key] = backupReference
                        }
                    }
                }

                written += filtered.size
                onProgress?.invoke(written, approxTotal.coerceAtLeast(written))

                gzipOut.flush()
                kotlinx.coroutines.yield()
            }

            try {
                if (options.libraryEntries) {
                    var afterId = 0L

                    while (true) {
                        val page = mangaRepository.getFavoritesEntryPaged(
                            afterId,
                            mangaPageSize,
                        )

                        if (page.isEmpty()) break

                        afterId = page.last().id

                        page.chunked(mangaBatchSize).forEach { batch ->
                            processBatch(batch)
                        }
                    }
                }

                if (options.readEntries) {
                    mangaRepository.getReadMangaNotInLibrary()
                        .chunked(mangaBatchSize)
                        .forEach { batch ->
                            processBatch(batch)
                        }
                }

                logcat(LogPriority.INFO) {
                    "Backup: All manga streamed ($written entries), writing metadata..."
                }

                val backupSources = sourcesBackupCreator.forSourceIds(sourceIds)

                backupCategories.forEach { category ->
                    val bytes = parser.encodeToByteArray(
                        BackupCategory.serializer(),
                        category,
                    )
                    writeProtoField(gzipOut.outputStream(), 2, bytes)
                }

                backupSources.forEach { source ->
                    val bytes = parser.encodeToByteArray(
                        BackupSource.serializer(),
                        source,
                    )
                    writeProtoField(gzipOut.outputStream(), 101, bytes)
                }

                backupAppPrefs.forEach { preference ->
                    val bytes = parser.encodeToByteArray(
                        BackupPreference.serializer(),
                        preference,
                    )
                    writeProtoField(gzipOut.outputStream(), 104, bytes)
                }

                backupSourcePrefs.forEach { sourcePreference ->
                    val bytes = parser.encodeToByteArray(
                        BackupSourcePreferences.serializer(),
                        sourcePreference,
                    )
                    writeProtoField(gzipOut.outputStream(), 105, bytes)
                }

                backupExtensionStores.forEach { extensionStore ->
                    val bytes = parser.encodeToByteArray(
                        BackupExtensionStore.serializer(),
                        extensionStore,
                    )
                    writeProtoField(gzipOut.outputStream(), 106, bytes)
                }

                /*
                 * Field 107 stores merge-reference records.
                 * Backup.kt already declares this field as backupMergedReferences.
                 */
                mergedReferences.values.forEach { reference ->
                    val bytes = parser.encodeToByteArray(
                        BackupMergedReference.serializer(),
                        reference,
                    )
                    writeProtoField(gzipOut.outputStream(), 107, bytes)
                }

                gzipOut.flush()
            } finally {
                gzipOut.close()
            }

            val fileUri = file.uri

            logcat(LogPriority.INFO) {
                "Backup: Write complete, validating..."
            }

            BackupFileValidator(context).validate(fileUri)

            if (isAutoBackup) {
                backupPreferences.lastAutoBackupTimestamp.set(
                    Clock.System.now().toEpochMilliseconds(),
                )
            }

            return fileUri.toString()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)

            try {
                file?.delete()
            } catch (deleteError: Exception) {
                logcat(LogPriority.WARN, deleteError) {
                    "Failed to delete partial backup file"
                }
            }

            throw e
        }
    }

    private fun MergedMangaReference.toBackupMergedReference(
        mergeManga: Manga,
    ) = BackupMergedReference(
        mergeMangaUrl = mergeManga.url,
        mergeMangaSourceId = mergeManga.source,
        mangaUrl = mangaUrl,
        mangaSourceId = mangaSourceId,
        isInfoManga = isInfoManga,
        getChapterUpdates = getChapterUpdates,
        chapterSortMode = chapterSortMode,
        chapterPriority = chapterPriority,
        downloadChapters = downloadChapters,
    )

    private fun writeProtoField(
        out: OutputStream,
        fieldNumber: Int,
        data: ByteArray,
    ) {
        writeVarint(out, (fieldNumber.toLong() shl 3) or 2L)
        writeVarint(out, data.size.toLong())
        out.write(data)
    }

    private fun writeVarint(
        out: OutputStream,
        value: Long,
    ) {
        var v = value

        while (v and 0x7FL.inv() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }

        out.write((v and 0x7F).toInt())
    }

    private suspend fun backupCategories(
        options: BackupOptions,
    ): List<BackupCategory> {
        if (!options.categories) return emptyList()

        return categoriesBackupCreator()
    }

    private fun backupAppPreferences(
        options: BackupOptions,
    ): List<BackupPreference> {
        if (!options.appSettings) return emptyList()

        return preferenceBackupCreator.createApp(
            includePrivatePreferences = options.privateSettings,
        )
    }

    private suspend fun backupExtensionStores(
        options: BackupOptions,
    ): List<BackupExtensionStore> {
        if (!options.extensionStores) return emptyList()

        return extensionStoresBackupCreator()
    }

    private fun backupSourcePreferences(
        options: BackupOptions,
    ): List<BackupSourcePreferences> {
        if (!options.sourceSettings) return emptyList()

        return preferenceBackupCreator.createSource(
            includePrivatePreferences = options.privateSettings,
        )
    }

    companion object {
        private const val MAX_AUTO_BACKUPS: Int = 4

        private val FILENAME_REGEX =
            """${BuildConfig.APPLICATION_ID}_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}.tachibk""".toRegex()

        fun getFilename(): String {
            val date = SimpleDateFormat(
                "yyyy-MM-dd_HH-mm",
                Locale.ENGLISH,
            ).format(Date())

            return "${BuildConfig.APPLICATION_ID}_$date.tachibk"
        }
    }
}