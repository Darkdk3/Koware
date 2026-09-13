package eu.kanade.presentation.library.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import java.util.concurrent.ConcurrentHashMap

private const val MIN_COVER_RATIO = 0.4f
private const val MAX_COVER_RATIO = 2.5f

/**
 * Keeps decoded dimensions available while the app is running so changing grid modes or scrolling
 * away and back does not repeatedly decode the same full-size covers.
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
 * Returns the manga's cover aspect ratio (width / height), decoded off the cover image via Coil.
 * Returns null if [enabled] is false, or if the cover hasn't loaded/decoded yet - callers should
 * fall back to a fixed ratio (e.g. the standard 2:3 book shape) while this is null. Ratios are
 * cached for the session and bounded to keep unusually wide banners or thin images usable in a
 * grid.
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
                    val request = ImageRequest.Builder(context)
                        .data(manga.asMangaCover())
                        .size(coil3.size.Size.ORIGINAL)
                        .build()
                    val result = context.imageLoader.execute(request)
                    if (result is SuccessResult && result.image.width > 0 && result.image.height > 0) {
                        (result.image.width.toFloat() / result.image.height.toFloat())
                            .coerceIn(MIN_COVER_RATIO, MAX_COVER_RATIO)
                    } else null
                } catch (_: Exception) {
                    // Cover failed to load; leave ratio as null so the grid falls back to 2:3
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
