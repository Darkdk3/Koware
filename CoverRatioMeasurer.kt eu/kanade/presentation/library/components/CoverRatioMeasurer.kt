package eu.kanade.presentation.library.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import eu.kanade.tachiyomi.util.manga.MangaCoverMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.manga.model.Manga

/**
 * Returns the manga's cover aspect ratio (width / height), measured off the locally-cached cover
 * file if it isn't already known. Returns null if [enabled] is false, or if the cover hasn't been
 * downloaded to disk yet - callers should fall back to a fixed ratio (e.g. the standard 2:3 book
 * shape) while this is null, so the grid cell doesn't collapse to zero height before a cover
 * exists locally to measure.
 */
@Composable
fun rememberCoverRatio(manga: Manga, enabled: Boolean): Float? {
    var ratio by remember(manga.id, manga.coverLastModified) {
        mutableStateOf(if (enabled) MangaCoverMetadata.getRatio(manga) else null)
    }

    if (enabled && ratio == null) {
        LaunchedEffect(manga.id, manga.coverLastModified) {
            // Bounds-only file decode - cheap, but still disk I/O, so off the main thread.
            val measured = withContext(Dispatchers.IO) { MangaCoverMetadata.getRatio(manga) }
            if (measured != null) ratio = measured
        }
    }

    return if (enabled) ratio else null
}
