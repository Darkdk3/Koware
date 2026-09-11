package eu.kanade.presentation.more.settings.screen


import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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


        // Only engines that can take a free-form prompt make sense here - dedicated
        // translation-only APIs (Libre, DeepL, Google, SYSTRAN) are excluded.
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
            engineManager.getEngine()
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
                        title = "AI features engine",
                        subtitle = "Powers recommendations and other AI features. Can differ from the translation engine.",
                        entries = engineEntries,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = prefs.aiFeatureUseSeparateApiKey(),
                        title = "Use a separate API key",
                        subtitle = "Off reuses the selected engine's key from Translation settings. On lets you set a dedicated key just for AI features.",
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.aiFeatureApiKey(),
                        title = "AI features API key",
                        subtitle = if (separateKey.isNotBlank()) "••••••••" else "Not set",
                        enabled = useSeparateKey,
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = "Test AI features engine",
                        subtitle = when {
                            testing -> "Testing..."
                            testResult != null -> testResult!!
                            resolvedEngine == null -> "No compatible engine selected"
                            else -> "Tap to send a test prompt"
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
        )
    }
}
