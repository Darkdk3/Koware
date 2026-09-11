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
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover

/**
 * Returns the manga's cover aspect ratio (width / height), decoded off the cover image via Coil.
 * Returns null if [enabled] is false, or if the cover hasn't loaded/decoded yet - callers should
 * fall back to a fixed ratio (e.g. the standard 2:3 book shape) while this is null.
 */
@Composable
fun rememberCoverRatio(manga: Manga, enabled: Boolean): Float? {
    var ratio by remember(manga.id, manga.coverLastModified) { mutableStateOf<Float?>(null) }
    val context = LocalContext.current

    if (enabled && ratio == null) {
        LaunchedEffect(manga.id, manga.coverLastModified) {
            val request = ImageRequest.Builder(context)
                .data(manga.asMangaCover())
                .build()
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult && result.image.width > 0 && result.image.height > 0) {
                ratio = result.image.width.toFloat() / result.image.height.toFloat()
            }
        }
    }

    return if (enabled) ratio else null
}