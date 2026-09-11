package eu.kanade.tachiyomi.ui.browse.discover

import eu.kanade.tachiyomi.data.translation.AiEngineResolver
import kotlinx.serialization.json.Json
import tachiyomi.domain.manga.interactor.BuildReadingProfile
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.translation.model.TranslationResult
import tachiyomi.domain.translation.service.TranslationPreferences
import tachiyomi.domain.translation.service.TranslationPromptDefaults
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Common interface for items that can be recommended by AI.
 * Both [DiscoverEntry] (novel) and [DiscoverMangaEntry] (manga) satisfy this.
 */
interface RecommendableItem {
    val sourceName: String
    val mangaTitle: String
    val mangaGenre: List<String>?
    val mangaAuthor: String?
}

class GetAiRecommendations(
    private val buildReadingProfile: BuildReadingProfile = BuildReadingProfile(),
    private val aiEngineResolver: AiEngineResolver = AiEngineResolver(),
    private val preferences: TranslationPreferences = Injekt.get(),
) {
    suspend fun <T : RecommendableItem> await(pool: List<T>): List<T> {
        if (pool.isEmpty()) return emptyList()
        val resolved = aiEngineResolver.resolve() ?: return emptyList()

        val profile = buildReadingProfile.await()
        if (profile.topGenres.isEmpty()) return emptyList()

        val candidateList = pool.mapIndexed { index, item ->
            "$index: \"${item.mangaTitle}\" — genres: ${item.mangaGenre.orEmpty().joinToString()}, " +
                "author: ${item.mangaAuthor ?: "unknown"}"
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
