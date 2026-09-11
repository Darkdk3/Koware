package eu.kanade.tachiyomi.ui.browse.discover

import eu.kanade.tachiyomi.data.translation.AiEngineResolver
import kotlinx.serialization.json.Json
import tachiyomi.domain.manga.interactor.BuildReadingProfile
import tachiyomi.domain.translation.model.TranslationResult
import tachiyomi.domain.translation.service.TranslationPreferences
import tachiyomi.domain.translation.service.TranslationPromptDefaults
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GetAiRecommendations(
    private val buildReadingProfile: BuildReadingProfile = BuildReadingProfile(),
    private val aiEngineResolver: AiEngineResolver = AiEngineResolver(),
    private val preferences: TranslationPreferences = Injekt.get(),
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

        val systemPrompt = preferences.aiFeatureSystemPrompt().get()
            .ifBlank { TranslationPromptDefaults.DEFAULT_AI_RECOMMENDATION_PROMPT }

        val userPrompt = preferences.aiFeatureUserPrompt().get()

        val prompt = if (userPrompt.isNotBlank()) {
            TranslationPromptDefaults.applyAiPrompt(
                userPrompt,
                profile.topGenres.joinToString(),
                profile.topAuthors.joinToString(),
                candidateList,
            )
        } else {
            TranslationPromptDefaults.applyAiPrompt(
                systemPrompt,
                profile.topGenres.joinToString(),
                profile.topAuthors.joinToString(),
                candidateList,
            )
        }

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
