package eu.kanade.tachiyomi.ui.browse.discover

import eu.kanade.tachiyomi.data.translation.AiEngineResolver
import kotlinx.serialization.json.Json
import tachiyomi.domain.manga.interactor.BuildReadingProfile
import tachiyomi.domain.translation.model.TranslationResult

class GetAiRecommendations(
    private val buildReadingProfile: BuildReadingProfile = BuildReadingProfile(),
    private val aiEngineResolver: AiEngineResolver = AiEngineResolver(),
) {
    suspend fun await(pool: List<DiscoverEntry>): List<DiscoverEntry> {
        if (pool.isEmpty()) return emptyList()
        val resolved = aiEngineResolver.resolve() ?: return emptyList()

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

        val result = resolved.engine.complete(prompt, resolved.apiKeyOverride)
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
