package eu.kanade.tachiyomi.ui.browse.discover

import eu.kanade.tachiyomi.data.translation.TranslationEngineManager
import kotlinx.serialization.json.Json
import tachiyomi.domain.manga.interactor.BuildReadingProfile
import tachiyomi.domain.translation.model.TranslationResult
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GetAiRecommendations(
    private val buildReadingProfile: BuildReadingProfile = BuildReadingProfile(),
    private val translationEngineManager: TranslationEngineManager = Injekt.get(),
) {
    suspend fun await(pool: List<DiscoverEntry>): List<DiscoverEntry> {
        val engine = translationEngineManager.getEngine()
        if (engine == null || !engine.supportsGeneralPrompts || pool.isEmpty()) return emptyList()

        val profile = buildReadingProfile.await()
        if (profile.topGenres.isEmpty()) return emptyList()

        val candidateList = pool.mapIndexed { index, entry ->
            "$index: \"${entry.manga.title}\" — genres: ${entry.manga.genre.orEmpty().joinToString()}, " +
                "author: ${entry.manga.author ?: "unknown"}"
        }.joinToString("\n")

        val prompt = """
            Reader's most-read genres: ${profile.topGenres.joinToString()}
            Reader's most-read authors: ${profile.topAuthors.joinToString()}

            From this candidate list, pick up to 8 titles this reader would most enjoy.
            Return ONLY a JSON array of the candidate index numbers, best match first.
            No explanation, no other text.

            Candidates:
            $candidateList
        """.trimIndent()

        val result = engine.complete(prompt)
        val text = (result as? TranslationResult.Success)?.translatedTexts?.firstOrNull()
            ?: return emptyList()

        val cleaned = text.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val indices = runCatching { Json.decodeFromString<List<Int>>(cleaned) }.getOrNull()
            ?: return emptyList()

        return indices.mapNotNull { pool.getOrNull(it) }
    }
}
