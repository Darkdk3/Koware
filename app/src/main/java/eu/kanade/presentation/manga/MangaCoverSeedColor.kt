package eu.kanade.presentation.manga

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover

/**
 * Returns a seed [Color] extracted from the manga's cover for cover-based theme recoloring, or
 * null if [enabled] is false or nothing has been extracted yet.
 */
@Composable
fun rememberCoverSeedColor(manga: Manga, enabled: Boolean): Color? {
    var seedColor by remember(manga.id, manga.coverLastModified) { mutableStateOf<Color?>(null) }
    val context = LocalContext.current

    if (enabled && seedColor == null) {
        LaunchedEffect(manga.id, manga.coverLastModified) {
            val request = ImageRequest.Builder(context)
                .data(manga.asMangaCover())
                .allowHardware(false) // Palette needs a software bitmap
                .build()
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                val palette = Palette.from(result.image.toBitmap()).generate()
                val swatch = palette.dominantSwatch ?: palette.vibrantSwatch ?: palette.mutedSwatch
                swatch?.let { seedColor = Color(it.rgb) }
            }
        }
    }

    return if (enabled) seedColor else null
}