package eu.kanade.presentation.manga

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import eu.kanade.tachiyomi.util.manga.MangaCoverPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.manga.model.Manga

/**
 * Returns a seed [Color] extracted from the manga's cover for cover-based theme recoloring (see
 * LibraryPreferences.mangaDetailsCoverTheme), or null if [enabled] is false or nothing has been
 * extracted yet - e.g. the cover isn't downloaded locally. Extraction is cached in
 * [MangaCoverPalette] so it only runs once per cover.
 */
@Composable
fun rememberCoverSeedColor(manga: Manga, enabled: Boolean): Color? {
    var seedColor by remember(manga.id, manga.coverLastModified) {
        mutableStateOf(if (enabled) MangaCoverPalette.getColor(manga)?.let(::Color) else null)
    }

    if (enabled && seedColor == null) {
        LaunchedEffect(manga.id, manga.coverLastModified) {
            // Bitmap decode + palette generation - real CPU work, off the main thread.
            val extracted = withContext(Dispatchers.IO) { MangaCoverPalette.getColor(manga) }
            if (extracted != null) seedColor = Color(extracted)
        }
    }

    return if (enabled) seedColor else null
}
