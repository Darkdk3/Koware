package eu.kanade.tachiyomi.data.translation.ocr

import android.util.Log
import eu.kanade.presentation.more.settings.screen.SettingsTranslationScreen
import eu.kanade.tachiyomi.data.translation.TranslationEngineManager
import eu.kanade.tachiyomi.data.translation.ocr.TextRecognitionInteractor.TextRecognitionResult
import kotlinx.coroutines.CancellationException
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.translation.model.TranslationResult
import tachiyomi.domain.translation.service.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Translates OCR results using the engine selected in Settings > Translation,
 * the saved source language, and the "Live OCR target language" preference.
 *
 * Unlike a silent fallback, every failure is returned as [Result.Failure]
 * with a readable message, so the UI can show it.
 */
class OcrTranslator(
    private val prefs: TranslationPreferences = Injekt.get(),
    private val engineManager: TranslationEngineManager = Injekt.get(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
) {

    sealed interface Result {
        /** [texts] always has the same size and order as the OCR results passed in. */
        data class Success(val texts: List<String>) : Result

        data class Failure(val message: String) : Result
    }

    suspend fun translate(ocrResults: List<TextRecognitionResult>): Result {
        if (ocrResults.isEmpty()) return Result.Success(emptyList())

        val engines = engineManager.engines
        val engine = engines.find { it.id == prefs.selectedEngineId().get() } ?: engines.first()

        if (!engine.isConfigured()) {
            return fail("${engine.name} is not configured (missing API key or URL)")
        }

        val source = prefs.sourceLanguage().get()
        val target = preferenceStore
            .getString(SettingsTranslationScreen.OCR_TARGET_LANGUAGE_KEY, "en")
            .get()

        if (source == target) {
            return fail("Source and target language are both '$source'")
        }

        val supportedCodes = engine.supportedLanguages.map { it.first }
        if (supportedCodes.isNotEmpty() && target !in supportedCodes) {
            return fail(
                "${engine.name} doesn't support target language '$target'. " +
                    "Re-pick it in Settings > Translation.",
            )
        }

        val inputs = ocrResults.map { it.text }

        return try {
            when (val result = engine.translate(inputs, source, target)) {
                is TranslationResult.Success -> {
                    val output = result.translatedTexts
                    if (output.size != inputs.size) {
                        Log.w(TAG, "Engine returned ${output.size} texts for ${inputs.size} inputs")
                    }
                    if (output == inputs) {
                        Log.w(TAG, "Engine returned the original text unchanged (source=$source, target=$target)")
                    }
                    Result.Success(List(inputs.size) { output.getOrNull(it).orEmpty() })
                }
                is TranslationResult.Error -> fail(result.message)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun fail(message: String): Result.Failure {
        Log.e(TAG, message)
        return Result.Failure(message)
    }

    private companion object {
        const val TAG = "OcrTranslator"
    }
}
