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
        val file = coverCache.getCustomCoverFile(manga.id).takeIf { it.exists() }
            ?: coverCache.getCoverFile(manga.thumbnailUrl)?.takeIf { it.exists() }
            ?: return null

        // Downsampled - palette only needs a rough color distribution, not full resolution.
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return null

        val palette = Palette.from(bitmap).generate()
        return palette.getBestColor()
    }

    fun remove(mangaId: Long) {
        colors.remove(mangaId)
        lastModifiedSeen.remove(mangaId)
    }
}

/**
 * Picks the most visually "vibrant" usable color from a Palette, weighing population,
 * saturation, and brightness together rather than just taking whichever swatch covers the
 * most pixels. A flat dominant-first pick (`dominantSwatch ?: vibrantSwatch ?: mutedSwatch`)
 * almost always resolves to dominant, since it's rarely null - this weighs the alternatives
 * properly instead.
 *
 * Ported from Komikku (eu.kanade.tachiyomi.data.coil.Utils), original author @Jays2Kings.
 */
private fun Palette.getBestColor(): Int? {
    val vibPopulation = vibrantSwatch?.population ?: -1
    val domSat = dominantSwatch?.hsl?.get(1) ?: 0f
    val domLum = dominantSwatch?.hsl?.get(2) ?: -1f
    val mutedPopulation = mutedSwatch?.population ?: -1
    val mutedSat = mutedSwatch?.hsl?.get(1) ?: 0f

    val mutedSatMinAcceptable = if (mutedPopulation > vibPopulation * 3f) 0.1f else 0.25f
    val dominantIsColorful = domSat >= .25f
    val dominantBrightnessJustRight = domLum <= .8f && domLum > .2f
    val vibrantIsConsiderableBigEnough = vibPopulation >= mutedPopulation * 0.75f
    val mutedIsBig = mutedPopulation > vibPopulation * 1.5f
    val mutedIsNotTooBoring = mutedSat > mutedSatMinAcceptable

    return when {
        dominantIsColorful && dominantBrightnessJustRight -> dominantSwatch
        vibrantIsConsiderableBigEnough -> vibrantSwatch
        mutedIsBig && mutedIsNotTooBoring -> mutedSwatch
        else -> listOfNotNull(vibrantSwatch, lightVibrantSwatch, darkVibrantSwatch)
            .maxByOrNull { if (it === vibrantSwatch) vibPopulation * 3 else it.population }
    }?.rgb
}