package eu.kanade.tachiyomi.util.manga

import android.graphics.BitmapFactory
import androidx.palette.graphics.Palette
import eu.kanade.tachiyomi.data.cache.CoverCache
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache of a dominant "seed" color (ARGB Int) extracted from each manga's cover, used
 * to drive cover-based theme recoloring (see LibraryPreferences.mangaDetailsCoverTheme).
 *
 * Reads the same locally-cached cover file as MangaCoverMetadata, but needs actual pixel data -
 * not just bounds - since Palette analyzes color distribution. The bitmap is downsampled via
 * inSampleSize before palette generation to keep this cheap; it still returns null if the cover
 * hasn't been downloaded to disk yet.
 */
object MangaCoverPalette {
    private val coverCache: CoverCache by lazy { Injekt.get() }

    private val colors = ConcurrentHashMap<Long, Int>()
    private val lastModifiedSeen = ConcurrentHashMap<Long, Long>()

    fun getColor(manga: Manga): Int? {
        val cached = colors[manga.id]
        if (cached != null && lastModifiedSeen[manga.id] == manga.coverLastModified) {
            return cached
        }

        val extracted = extract(manga) ?: return null
        colors[manga.id] = extracted
        lastModifiedSeen[manga.id] = manga.coverLastModified
        return extracted
    }

    /**
     * Blocking file I/O + bitmap decode + palette generation - call from a background dispatcher,
     * never directly from composition.
     */
    private fun extract(manga: Manga): Int? {
        val file = manga.let(coverCache::getCustomCoverFile).takeIf { it.exists() }
            ?: manga.let(coverCache::getCoverFile).takeIf { it.exists() }
            ?: return null

        // Downsampled - palette only needs a rough color distribution, not full resolution.
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return null

        val palette = Palette.from(bitmap).generate()
        return palette.dominantSwatch?.rgb
            ?: palette.vibrantSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
    }

    fun remove(mangaId: Long) {
        colors.remove(mangaId)
        lastModifiedSeen.remove(mangaId)
    }
}
