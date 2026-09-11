package tachiyomi.domain.manga.interactor

import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class ReadingProfile(
    val topGenres: List<String>,
    val topAuthors: List<String>,
)

class BuildReadingProfile(
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
) {
    suspend fun await(): ReadingProfile {
        val library = getLibraryManga.await()

        val genreWeights = mutableMapOf<String, Int>()
        val authorWeights = mutableMapOf<String, Int>()

        library.forEach { libraryManga ->
            val weight = libraryManga.readCount.coerceAtLeast(1).toInt()
            libraryManga.manga.genre.orEmpty().forEach { genre ->
                genreWeights[genre] = (genreWeights[genre] ?: 0) + weight
            }
            libraryManga.manga.author?.let { author ->
                authorWeights[author] = (authorWeights[author] ?: 0) + weight
            }
        }

        return ReadingProfile(
            topGenres = genreWeights.entries.sortedByDescending { it.value }.take(8).map { it.key },
            topAuthors = authorWeights.entries.sortedByDescending { it.value }.take(5).map { it.key },
        )
    }
}
