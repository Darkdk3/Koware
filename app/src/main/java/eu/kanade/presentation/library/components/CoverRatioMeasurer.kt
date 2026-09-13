package eu.kanade.presentation.library.components

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil3.disk.DiskCache
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val MIN_COVER_RATIO = 0.4f
private const val MAX_COVER_RATIO = 2.5f

/**
 * Keeps decoded dimensions available while the app is running so changing grid modes or scrolling
 * away and back does not repeatedly decode the same covers.
 */
private object CoverRatioCache {
    private val ratios = ConcurrentHashMap<String, Float>()

    fun get(manga: Manga): Float? = ratios[cacheKey(manga)]

    fun put(manga: Manga, ratio: Float) {
        ratios[cacheKey(manga)] = ratio.coerceIn(MIN_COVER_RATIO, MAX_COVER_RATIO)
    }

    private fun cacheKey(manga: Manga) = "${manga.id}:${manga.coverLastModified}"
}

/**
 * Reads just the image dimensions from a file using BitmapFactory.Options with
 * [BitmapFactory.Options.inJustDecodeBounds] — no pixel data is allocated.
 */
private fun decodeBoundsFromFile(file: File): Pair<Int, Int>? {
    if (!file.exists()) return null
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, opts)
    val w = opts.outWidth
    val h = opts.outHeight
    return if (w > 0 && h > 0) w to h else null
}

/**
 * Returns the manga's cover aspect ratio (width / height).
 *
 * Uses a two-step approach for speed:
 * 1. Tries to read dimensions directly from the disk-cache file via
 *    [BitmapFactory.Options.inJustDecodeBounds] (no bitmap allocation).
 * 2. Falls back to a lightweight Coil decode at 1/16th resolution if the disk file
 *    is not yet available.
 *
 * Returns null if [enabled] is false, or the cover hasn't been cached yet — callers
 * should fall back to a fixed ratio (e.g. the standard 2:3 book shape).
 *
 * Ratios are cached for the session and bounded to keep unusually wide banners or
 * thin images usable in a grid.
 */
@Composable
fun rememberCoverRatio(manga: Manga, enabled: Boolean): Float? {
    var ratio by remember(manga.id, manga.coverLastModified) {
        mutableStateOf(CoverRatioCache.get(manga))
    }
    val context = LocalContext.current.applicationContext

    if (enabled && ratio == null) {
        LaunchedEffect(manga.id, manga.coverLastModified) {
            val measuredRatio = withContext(Dispatchers.IO) {
                try {
                    // Step 1: Try reading bounds from Coil's disk cache without decoding pixels
                    var dimensions: Pair<Int, Int>? = null
                    val diskCache = context.imageLoader.diskCache
                    val coverUrl = manga.asMangaCover().url
                    if (diskCache != null && coverUrl != null) {
                        val snapshot = diskCache.openSnapshot(coverUrl)
                        if (snapshot != null) {
                            dimensions = decodeBoundsFromFile(snapshot.data.toFile())
                            snapshot.close()
                        }
                    }

                    // Step 2: Fallback — decode at 1/16th resolution via Coil
                    if (dimensions == null) {
                        val request = ImageRequest.Builder(context)
                            .data(manga.asMangaCover())
                            .size(coil3.size.Size(100, 100))
                            .build()
                        val result = context.imageLoader.execute(request)
                        if (result is SuccessResult) {
                            val image = result.image
                            if (image.width > 0 && image.height > 0) {
                                dimensions = image.width to image.height
                            }
                        }
                    }

                    dimensions?.let { (w, h) ->
                        (w.toFloat() / h.toFloat()).coerceIn(MIN_COVER_RATIO, MAX_COVER_RATIO)
                    }
                } catch (_: Exception) {
                    null
                }
            }
            if (measuredRatio != null) {
                CoverRatioCache.put(manga, measuredRatio)
                ratio = measuredRatio
            }
        }
    }

    return if (enabled) ratio else null
}
