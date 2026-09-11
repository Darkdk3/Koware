package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.translation.TranslationEngineManager
import kotlinx.coroutines.launch
import tachiyomi.domain.translation.model.TranslationResult
import tachiyomi.domain.translation.service.TranslationPreferences
import tachiyomi.domain.translation.service.TranslationPromptDefaults
import tachiyomi.i18n.MR
import tachiyomi.i18n.novel.TDMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsAiScreen : SearchableSettings {

    override val supportsReset: Boolean get() = true

    @Composable
    override fun getAdditionalResetPreferences(): List<tachiyomi.core.common.preference.Preference<*>> {
        val prefs = remember { Injekt.get<TranslationPreferences>() }
        return listOf(
            prefs.aiFeatureEngineId(),
            prefs.aiFeatureUseSeparateApiKey(),
            prefs.aiFeatureApiKey(),
            prefs.aiFeatureSystemPrompt(),
            prefs.aiFeatureUserPrompt(),
        )
    }

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = TDMR.strings.pref_category_ai_features

    @Composable
    override fun getPreferences(): List<Preference> {
        val prefs = remember { Injekt.get<TranslationPreferences>() }
        val engineManager = remember { Injekt.get<TranslationEngineManager>() }
        val scope = rememberCoroutineScope()

        val engineId by prefs.aiFeatureEngineId().collectAsState()
        val useSeparateKey by prefs.aiFeatureUseSeparateApiKey().collectAsState()
        val separateKey by prefs.aiFeatureApiKey().collectAsState()
        val translationEngineId by prefs.selectedEngineId().collectAsState()

        val eligibleEngines = engineManager.engines.filter { it.supportsGeneralPrompts }
        val translationEngineName = engineManager.engines
            .find { it.id == translationEngineId }
            ?.name
            ?: "translation engine"

        val engineEntries = buildMap {
            put(0L, "Same as translation ($translationEngineName)")
            eligibleEngines.forEach { put(it.id, it.name) }
        }

        val resolvedEngine = if (engineId == 0L) {
            val selected = engineManager.getSelectedEngine()
            if (selected.supportsGeneralPrompts) selected else null
        } else {
            eligibleEngines.find { it.id == engineId }
        }

        var testResult by remember { mutableStateOf<String?>(null) }
        var testing by remember { mutableStateOf(false) }

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(TDMR.strings.pref_category_ai_features),
                preferenceItems = listOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = prefs.aiFeatureEngineId(),
                        title = stringResource(TDMR.strings.pref_ai_features_engine),
                        subtitle = stringResource(TDMR.strings.pref_ai_features_engine_summary),
                        entries = engineEntries,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = prefs.aiFeatureUseSeparateApiKey(),
                        title = stringResource(TDMR.strings.pref_ai_features_separate_key),
                        subtitle = stringResource(TDMR.strings.pref_ai_features_separate_key_summary),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.aiFeatureApiKey(),
                        title = stringResource(TDMR.strings.pref_ai_features_api_key),
                        subtitle = if (separateKey.isNotBlank()) "••••••••" else stringResource(TDMR.strings.not_set),
                        enabled = useSeparateKey,
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(TDMR.strings.pref_ai_features_test_engine),
                        subtitle = when {
                            testing -> stringResource(TDMR.strings.pref_ai_features_testing)
                            testResult != null -> testResult!!
                            resolvedEngine == null -> stringResource(TDMR.strings.pref_ai_features_no_engine)
                            else -> stringResource(TDMR.strings.pref_ai_features_test_send)
                        },
                        enabled = !testing && resolvedEngine != null,
                        onClick = {
                            val engine = resolvedEngine
                            if (engine != null) {
                                testing = true
                                testResult = null
                                scope.launch {
                                    val apiKeyOverride = if (useSeparateKey) {
                                        separateKey.takeIf { it.isNotBlank() }
                                    } else {
                                        null
                                    }
                                    val result = engine.complete(
                                        "Reply with exactly the word: pong",
                                        apiKeyOverride,
                                    )
                                    testResult = when (result) {
                                        is TranslationResult.Success ->
                                            "✓ ${result.translatedTexts.joinToString(" | ")}"
                                        is TranslationResult.Error ->
                                            "✗ ${result.message}"
                                    }
                                    testing = false
                                }
                            }
                        },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(TDMR.strings.pref_ai_features_system_prompt),
                preferenceItems = listOf(
                    Preference.PreferenceItem.PromptPreference(
                        preference = prefs.aiFeatureSystemPrompt(),
                        title = stringResource(TDMR.strings.pref_ai_features_system_prompt),
                        subtitle = stringResource(TDMR.strings.pref_ai_features_system_prompt_summary),
                        defaultValue = TranslationPromptDefaults.DEFAULT_AI_RECOMMENDATION_PROMPT,
                    ),
                    Preference.PreferenceItem.PromptPreference(
                        preference = prefs.aiFeatureUserPrompt(),
                        title = stringResource(TDMR.strings.pref_ai_features_user_prompt),
                        subtitle = stringResource(TDMR.strings.pref_ai_features_user_prompt_summary),
                        defaultValue = "",
                    ),
                ),
            ),
        ) + getEngineConfigGroups(prefs, engineManager, scope, useSeparateKey, separateKey)
    }

    @Composable
    private fun getEngineConfigGroups(
        prefs: TranslationPreferences,
        engineManager: TranslationEngineManager,
        scope: kotlinx.coroutines.CoroutineScope,
        useSeparateKey: Boolean,
        separateKey: String,
    ): List<Preference.PreferenceGroup> {
        val openAiKey by prefs.openAiApiKey().collectAsState()
        val nvidiaNimBaseUrl by prefs.nvidiaNimBaseUrl().collectAsState()
        val nvidiaNimApiKey by prefs.nvidiaNimApiKey().collectAsState()
        val nvidiaNimModel by prefs.nvidiaNimModel().collectAsState()
        val deepSeekKey by prefs.deepSeekApiKey().collectAsState()
        val ollamaUrl by prefs.ollamaUrl().collectAsState()
        val ollamaModel by prefs.ollamaModel().collectAsState()
        val geminiKey by prefs.geminiApiKey().collectAsState()
        val geminiModel by prefs.geminiModel().collectAsState()
        val customHttpUrl by prefs.customHttpUrl().collectAsState()
        val customHttpApiKey by prefs.customHttpApiKey().collectAsState()
        val customHttpResponsePath by prefs.customHttpResponsePath().collectAsState()

        val testEngineFormat = stringResource(MR.strings.pref_translation_test_engine)
        val testingStr = stringResource(MR.strings.pref_translation_testing)
        val testSendStr = stringResource(MR.strings.pref_translation_test_send)
        val notConfiguredStr = stringResource(TDMR.strings.not_configured)

        var testingEngineId by remember { mutableStateOf<Long?>(null) }
        val testResults = remember { mutableMapOf<Long, String>() }

        fun aiTestButton(engine: tachiyomi.domain.translation.model.TranslationEngine): Preference.PreferenceItem.TextPreference {
            val engineId = engine.id
            val engineConfigured = engine.isConfigured()
            return Preference.PreferenceItem.TextPreference(
                title = testEngineFormat.format(engine.name),
                subtitle = when {
                    testingEngineId == engineId -> testingStr
                    testResults.containsKey(engineId) -> testResults[engineId]!!
                    !engineConfigured -> notConfiguredStr
                    else -> testSendStr
                },
                enabled = testingEngineId == null && engineConfigured,
                onClick = {
                    if (engineConfigured) {
                        testingEngineId = engineId
                        testResults.remove(engineId)
                        scope.launch {
                            try {
                                val apiKeyOverride = if (useSeparateKey) {
                                    separateKey.takeIf { it.isNotBlank() }
                                } else {
                                    null
                                }
                                val result = engine.complete(
                                    "Reply with exactly the word: pong",
                                    apiKeyOverride,
                                )
                                testResults[engineId] = when (result) {
                                    is TranslationResult.Success ->
                                        "✓ ${result.translatedTexts.joinToString(" | ")}"
                                    is TranslationResult.Error ->
                                        "✗ ${result.message}"
                                }
                            } catch (e: Exception) {
                                testResults[engineId] = "✗ ${e.message ?: e.javaClass.simpleName}"
                            } finally {
                                testingEngineId = null
                            }
                        }
                    }
                },
            )
        }

        return listOf(
            // OpenAI
            Preference.PreferenceGroup(
                title = "OpenAI",
                preferenceItems = listOf(
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.openAiApiKey(),
                        title = stringResource(TDMR.strings.pref_translation_openai_key),
                        subtitle = if (openAiKey.isNotBlank()) "••••••••" else stringResource(TDMR.strings.not_set),
                    ),
                    Preference.PreferenceItem.PromptPreference(
                        preference = prefs.openAiSystemPrompt(),
                        title = stringResource(MR.strings.pref_translation_system_prompt),
                        subtitle = stringResource(MR.strings.pref_translation_system_prompt_desc),
                        defaultValue = TranslationPromptDefaults.DEFAULT_SYSTEM_PROMPT,
                    ),
                    Preference.PreferenceItem.PromptPreference(
                        preference = prefs.openAiUserPrompt(),
                        title = stringResource(MR.strings.pref_translation_user_prompt),
                        subtitle = stringResource(MR.strings.pref_translation_user_prompt_desc),
                        defaultValue = TranslationPromptDefaults.DEFAULT_USER_PROMPT,
                    ),
                    aiTestButton(engineManager.engines.first { it.name.contains("OpenAI") }),
                ),
            ),
            // NVIDIA NIM
            Preference.PreferenceGroup(
                title = "NVIDIA NIM",
                preferenceItems = listOf(
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.nvidiaNimBaseUrl(),
                        title = stringResource(MR.strings.pref_translation_api_url),
                        subtitle = if (nvidiaNimBaseUrl.isNotBlank()) nvidiaNimBaseUrl else stringResource(TDMR.strings.not_set),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.nvidiaNimApiKey(),
                        title = stringResource(MR.strings.pref_translation_api_key),
                        subtitle = if (nvidiaNimApiKey.isNotBlank()) "••••••••" else stringResource(TDMR.strings.not_set),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.nvidiaNimModel(),
                        title = stringResource(TDMR.strings.pref_translation_ollama_model),
                        subtitle = nvidiaNimModel.ifBlank { stringResource(TDMR.strings.not_set) },
                    ),
                    Preference.PreferenceItem.PromptPreference(
                        preference = prefs.nvidiaNimSystemPrompt(),
                        title = stringResource(MR.strings.pref_translation_system_prompt),
                        subtitle = stringResource(MR.strings.pref_translation_system_prompt_desc),
                        defaultValue = TranslationPromptDefaults.DEFAULT_SYSTEM_PROMPT,
                    ),
                    Preference.PreferenceItem.PromptPreference(
                        preference = prefs.nvidiaNimUserPrompt(),
                        title = stringResource(MR.strings.pref_translation_user_prompt),
                        subtitle = stringResource(MR.strings.pref_translation_user_prompt_desc),
                        defaultValue = TranslationPromptDefaults.DEFAULT_USER_PROMPT,
                    ),
                    aiTestButton(engineManager.getEngineById(TranslationEngineManager.ENGINE_NVIDIA_NIM)!!),
                ),
            ),
            // DeepSeek
            Preference.PreferenceGroup(
                title = "DeepSeek",
                preferenceItems = listOf(
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.deepSeekApiKey(),
                        title = stringResource(TDMR.strings.pref_translation_deepseek_key),
                        subtitle = if (deepSeekKey.isNotBlank()) "••••••••" else stringResource(TDMR.strings.not_set),
                    ),
                    Preference.PreferenceItem.PromptPreference(
                        preference = prefs.deepSeekSystemPrompt(),
                        title = stringResource(MR.strings.pref_translation_system_prompt),
                        subtitle = stringResource(MR.strings.pref_translation_system_prompt_desc),
                        defaultValue = TranslationPromptDefaults.DEFAULT_SYSTEM_PROMPT,
                    ),
                    Preference.PreferenceItem.PromptPreference(
                        preference = prefs.deepSeekUserPrompt(),
                        title = stringResource(MR.strings.pref_translation_user_prompt),
                        subtitle = stringResource(MR.strings.pref_translation_user_prompt_desc),
                        defaultValue = TranslationPromptDefaults.DEFAULT_USER_PROMPT,
                    ),
                    aiTestButton(engineManager.engines.first { it.name.contains("DeepSeek") }),
                ),
            ),
            // Gemini (Google AI)
            Preference.PreferenceGroup(
                title = "Gemini (Google AI)",
                preferenceItems = listOf(
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.geminiApiKey(),
                        title = stringResource(MR.strings.pref_translation_api_key),
                        subtitle = if (geminiKey.isNotBlank()) "••••••••" else stringResource(TDMR.strings.not_set),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.geminiModel(),
                        title = stringResource(TDMR.strings.pref_translation_ollama_model),
                        subtitle = geminiModel.ifBlank { "gemini-2.0-flash" },
                    ),
                    Preference.PreferenceItem.PromptPreference(
                        preference = prefs.geminiPrompt(),
                        title = stringResource(MR.strings.pref_translation_custom_prompt),
                        subtitle = stringResource(MR.strings.pref_translation_user_prompt_desc),
                        defaultValue = TranslationPromptDefaults.DEFAULT_COMBINED_PROMPT,
                    ),
                    aiTestButton(engineManager.engines.first { it.name.contains("Gemini") }),
                ),
            ),
            // Ollama
            Preference.PreferenceGroup(
                title = "Ollama",
                preferenceItems = listOf(
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.ollamaUrl(),
                        title = stringResource(TDMR.strings.pref_translation_ollama_url),
                        subtitle = ollamaUrl,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.ollamaModel(),
                        title = stringResource(TDMR.strings.pref_translation_ollama_model),
                        subtitle = ollamaModel,
                    ),
                    Preference.PreferenceItem.PromptPreference(
                        preference = prefs.ollamaPrompt(),
                        title = stringResource(MR.strings.pref_translation_custom_prompt),
                        subtitle = stringResource(MR.strings.pref_translation_user_prompt_desc),
                        defaultValue = TranslationPromptDefaults.DEFAULT_COMBINED_PROMPT,
                    ),
                    aiTestButton(engineManager.engines.first { it.name.contains("Ollama") }),
                ),
            ),
            // Custom HTTP
            Preference.PreferenceGroup(
                title = "Custom HTTP",
                preferenceItems = listOf(
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.customHttpUrl(),
                        title = stringResource(MR.strings.pref_translation_api_url),
                        subtitle = if (customHttpUrl.isNotBlank()) customHttpUrl else stringResource(TDMR.strings.not_set),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.customHttpApiKey(),
                        title = stringResource(MR.strings.pref_translation_api_key),
                        subtitle = if (customHttpApiKey.isNotBlank()) "••••••••" else stringResource(TDMR.strings.not_set),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = prefs.customHttpMethod(),
                        title = stringResource(MR.strings.pref_translation_request_method),
                        entries = mapOf(
                            "POST" to "POST",
                            "GET" to "GET",
                        ),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.customHttpHeaders(),
                        title = stringResource(MR.strings.pref_translation_custom_headers),
                        subtitle = stringResource(MR.strings.pref_translation_custom_headers_desc),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.customHttpRequestTemplate(),
                        title = stringResource(MR.strings.pref_translation_request_template),
                        subtitle = stringResource(MR.strings.pref_translation_request_template_desc),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.customHttpResponsePath(),
                        title = stringResource(MR.strings.pref_translation_response_path),
                        subtitle = customHttpResponsePath.ifBlank { "translatedText" },
                    ),
                    aiTestButton(engineManager.engines.first { it.name.contains("Custom") }),
                ),
            ),
        )
    }
}
