package eu.kanade.tachiyomi.data.translation.ocr

import android.text.Html
import android.util.Log
import eu.kanade.presentation.more.settings.screen.SettingsTranslationScreen
import eu.kanade.tachiyomi.data.translation.TranslationEngineManager
import eu.kanade.tachiyomi.data.translation.ocr.TextRecognitionInteractor.TextRecognitionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.translation.model.TranslationResult
import tachiyomi.domain.translation.service.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.atomic.AtomicReference

/**
 * Translates OCR results using the engine selected in Settings > Translation,
 * the saved source language, and the "Live OCR target language" preference.
 *
 * The whole page is sent to the engine in one request (so it has the full context of the page).
 * Because engines return plain lists, a merged / dropped / split entry would silently shift every
 * following translation onto the wrong bubble. To prevent that, the result is only accepted when it
 * has exactly one text per input; otherwise the regions are translated one by one instead.
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
        /** [texts] always has the same size and order as the OCR results passed in ("" = nothing to show). */
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

        // Only real text goes to the engine: sound-effect scribbles / stray symbols are skipped.
        val indices = ocrResults.indices.filter { hasTranslatableText(ocrResults[it].text) }
        if (indices.isEmpty()) return Result.Success(List(ocrResults.size) { "" })
        val inputs = indices.map { ocrResults[it].text }

        val translateBatch: suspend (List<String>) -> TranslationResult = { texts ->
            engine.translate(texts, source, target)
        }

        return try {
            val translated: List<String> = when (val batch = translateBatch(inputs)) {
                is TranslationResult.Error -> return fail(batch.message)
                is TranslationResult.Success -> {
                    val output = batch.translatedTexts
                    if (output.size == inputs.size) {
                        output.map(::cleanText)
                    } else {
                        Log.w(
                            TAG,
                            "Engine returned ${output.size} texts for ${inputs.size} inputs; " +
                                "translating one by one to keep them lined up",
                        )
                        translateOneByOne(inputs, translateBatch)
                    }
                }
            }

            if (translated == inputs) {
                Log.w(TAG, "Engine returned the original text unchanged (source=$source, target=$target)")
            }

            val full = MutableList(ocrResults.size) { "" }
            indices.forEachIndexed { position, ocrIndex -> full[ocrIndex] = translated[position] }
            Result.Success(full)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Fallback: one request per region (a few at a time). Slower, but each translation is guaranteed
     * to belong to its own bubble. Throws if every request failed.
     */
    private suspend fun translateOneByOne(
        inputs: List<String>,
        translateBatch: suspend (List<String>) -> TranslationResult,
    ): List<String> = coroutineScope {
        val gate = Semaphore(MAX_PARALLEL_REQUESTS)
        val firstError = AtomicReference<String?>(null)

        val results = inputs.map { text ->
            async {
                gate.withPermit {
                    when (val single = translateBatch(listOf(text))) {
                        is TranslationResult.Success ->
                            single.translatedTexts.joinToString(" ") { cleanText(it) }.trim()
                        is TranslationResult.Error -> {
                            firstError.compareAndSet(null, single.message)
                            ""
                        }
                    }
                }
            }
        }.awaitAll()

        val error = firstError.get()
        if (error != null && results.all { it.isBlank() }) throw IllegalStateException(error)
        results
    }

    private fun fail(message: String): Result.Failure {
        Log.e(TAG, message)
        return Result.Failure(message)
    }

    private fun hasTranslatableText(text: String): Boolean = text.any { it.isLetterOrDigit() }

    /** Engines often answer with "<p>text</p>"; strip tags/entities and collapse whitespace. */
    private fun cleanText(raw: String): String {
        if (raw.isBlank()) return ""
        val text = if (raw.contains('<') || raw.contains('&')) {
            Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()
        } else {
            raw
        }
        return text
            .replace("``", "\"")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private companion object {
        const val TAG = "OcrTranslator"
        const val MAX_PARALLEL_REQUESTS = 3
    }
}
