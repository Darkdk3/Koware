package eu.kanade.tachiyomi.data.translation


import tachiyomi.domain.translation.model.TranslationEngine
import tachiyomi.domain.translation.service.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get


/**
 * Resolves which engine (and API key) AI-only features - recommendations, and anything
 * else built on complete() - should use. Independent from the translation engine
 * selection, defaulting to mirror it so nothing extra needs configuring unless the person
 * wants to split them via Settings > AI.
 */
class AiEngineResolver(
    private val engineManager: TranslationEngineManager = Injekt.get(),
    private val preferences: TranslationPreferences = Injekt.get(),
) {
    data class Resolved(
        val engine: TranslationEngine,
        val apiKeyOverride: String?,
    )


    fun resolve(): Resolved? {
        val chosenId = preferences.aiFeatureEngineId().get()
        val engine = if (chosenId == 0L) {
            engineManager.getEngine()
        } else {
            engineManager.engines.find { it.id == chosenId }
        } ?: return null


        if (!engine.supportsGeneralPrompts) return null


        val apiKeyOverride = if (preferences.aiFeatureUseSeparateApiKey().get()) {
            preferences.aiFeatureApiKey().get().takeIf { it.isNotBlank() }
        } else {
            null
        }


        return Resolved(engine, apiKeyOverride)
    }
}
