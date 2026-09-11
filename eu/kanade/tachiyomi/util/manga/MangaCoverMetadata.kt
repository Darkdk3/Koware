package eu.kanade.tachiyomi.util.manga

import android.graphics.BitmapFactory
import eu.kanade.tachiyomi.data.cache.CoverCache
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache of measured cover aspect ratios (width / height), keyed by manga id.
 *
 * Ratios are read directly off the cover file already sitting in [CoverCache] via a bounds-only
 * BitmapFactory decode - this only reads the image header, not the full bitmap, so it's cheap
 * enough to do on every miss without a persisted on-disk cache the way the old View-based
 * MangaCoverMetadata needed. If the cover hasn't been downloaded to disk yet, [getRatio] returns
 * null and callers should fall back to a fixed ratio until it does.
 *
 * Entries are keyed against [Manga.coverLastModified], so a replaced cover naturally invalidates
 * its old ratio the next time it's read.
 */
object MangaCoverMetadata {
    private val coverCache: CoverCache by lazy { Injekt.get() }

    private val ratios = ConcurrentHashMap<Long, Float>()
    private val lastModifiedSeen = ConcurrentHashMap<Long, Long>()

    fun getRatio(manga: Manga): Float? {
        val cached = ratios[manga.id]
        if (cached != null && lastModifiedSeen[manga.id] == manga.coverLastModified) {
            return cached
        }

        val measured = measure(manga) ?: return null
        ratios[manga.id] = measured
        lastModifiedSeen[manga.id] = manga.coverLastModified
        return measured
    }

    /**
     * Blocking file I/O (bounds-only decode) - call from a background dispatcher, never directly
     * from composition.
     */
    private fun measure(manga: Manga): Float? {
        val file = manga.let(coverCache::getCustomCoverFile).takeIf { it.exists() }
            ?: manga.let(coverCache::getCoverFile).takeIf { it.exists() }
            ?: return null

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        return options.outWidth.toFloat() / options.outHeight.toFloat()
    }

    fun remove(mangaId: Long) {
        ratios.remove(mangaId)
        lastModifiedSeen.remove(mangaId)
    }
}
