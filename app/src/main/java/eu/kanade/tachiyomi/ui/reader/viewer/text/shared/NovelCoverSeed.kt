package eu.kanade.tachiyomi.ui.reader.viewer.text.shared

import eu.kanade.tachiyomi.util.manga.MangaCoverPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.domain.manga.model.Manga
import java.util.concurrent.ConcurrentHashMap

/**
 * Feeds ThemeUtils' "cover" theme. MangaCoverPalette.getColor() does blocking file I/O + bitmap
 * decode on a cache miss, so it can't run while styles are being built on the UI thread. This
 * loads it once per manga on IO, caches the result, and calls [onLoaded] on the main thread so
 * the caller can re-apply styles with the real color.
 */
object NovelCoverSeed {

    // Palette swatch colors always carry full alpha, so 0 can never be a real result.
    private const val NONE = 0

    private val cache = ConcurrentHashMap<Long, Int>()
    private val requested = ConcurrentHashMap.newKeySet<Long>()

    /** Cached seed color, or null if not loaded yet / the cover has no usable color. */
    fun peek(mangaId: Long): Int? = cache[mangaId]?.takeIf { it != NONE }

    /** Kicks off a one-time background load per manga. Safe to call repeatedly. */
    fun load(manga: Manga, scope: CoroutineScope, onLoaded: () -> Unit) {
        if (!requested.add(manga.id)) return
        scope.launch(Dispatchers.IO) {
            val color = runCatching { MangaCoverPalette.getColor(manga) }.getOrNull()
            cache[manga.id] = color ?: NONE
            if (color != null) withContext(Dispatchers.Main) { onLoaded() }
        }
    }
}
