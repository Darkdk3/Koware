package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import eu.kanade.presentation.more.settings.screen.SettingsTranslationScreen
import eu.kanade.tachiyomi.data.translation.ocr.OcrTranslator
import eu.kanade.tachiyomi.data.translation.ocr.TextRecognitionInteractor
import eu.kanade.tachiyomi.data.translation.ocr.TextRecognitionInteractor.TextRecognitionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.translation.service.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayInputStream

/**
 * What the overlay on one webtoon page should draw. Coordinates in [ocr] are in the
 * page image's own pixel space ([width] x [height]).
 */
data class WebtoonOverlayUi(
    val ocr: List<TextRecognitionResult>,
    val translated: List<String>,
    val processing: Boolean,
    val width: Int,
    val height: Int,
)

/**
 * OCR + translation for a single (very tall) webtoon page.
 *
 * Tall pages are cut into overlapping tiles so ML Kit never sees a huge bitmap. The work is a
 * two-stage pipeline: OCR (CPU) runs one page at a time, and translation (network) runs one page
 * at a time, so while page N is being translated page N+1 is already being OCR'd. Results are
 * cached per page (enough for a whole chapter), so scrolling back doesn't redo any work.
 */
object WebtoonPageOcr {

    sealed interface Outcome {
        data class Ready(val ui: WebtoonOverlayUi) : Outcome
        data class Failed(val message: String) : Outcome
    }

    private class OcrPage(val width: Int, val height: Int, val results: List<TextRecognitionResult>)

    private const val TAG = "WebtoonPageOcr"
    private const val TILE_HEIGHT = 1800
    private const val TILE_OVERLAP = 200
    private const val MAX_TILE_WIDTH = 2400
    private const val CACHE_SIZE = 150
    private const val REPORT_INTERVAL_MS = 8000L

    private val interactor by lazy { TextRecognitionInteractor() }
    private val translator by lazy { OcrTranslator() }

    private val ocrMutex = Mutex()
    private val translateMutex = Mutex()

    private val ocrCache = object : LinkedHashMap<String, OcrPage>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OcrPage>?): Boolean =
            size > CACHE_SIZE
    }

    private val uiCache = object : LinkedHashMap<String, WebtoonOverlayUi>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WebtoonOverlayUi>?): Boolean =
            size > CACHE_SIZE
    }

    private var lastReportMessage: String? = null
    private var lastReportTime = 0L

    /** Returns the finished overlay for [pageKey] if it was already computed with the current settings. */
    fun cached(pageKey: String): WebtoonOverlayUi? = synchronized(uiCache) { uiCache[uiKey(pageKey)] }

    /** Throttles error toasts so scrolling through many failing pages doesn't spam the user. */
    @Synchronized
    fun shouldReport(message: String): Boolean {
        val now = System.currentTimeMillis()
        if (message == lastReportMessage && now - lastReportTime < REPORT_INTERVAL_MS) return false
        lastReportMessage = message
        lastReportTime = now
        return true
    }

    /**
     * OCRs and translates one page.
     *
     * @param pageKey unique id of the page (used for caching)
     * @param loadBytes returns the encoded image bytes of the page, as shown in the reader
     */
    suspend fun process(pageKey: String, loadBytes: suspend () -> ByteArray?): Outcome =
        withContext(Dispatchers.IO) {
            try {
                cached(pageKey)?.let { return@withContext Outcome.Ready(it) }
                processPipelined(pageKey, loadBytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "OCR failed for $pageKey" }
                Outcome.Failed(e.message ?: e.javaClass.simpleName)
            }
        }

    private suspend fun processPipelined(pageKey: String, loadBytes: suspend () -> ByteArray?): Outcome {
        val prefs = Injekt.get<TranslationPreferences>()
        val model = modelFor(prefs.sourceLanguage().get())
        val ocrKey = "$pageKey|$model"

        // Stage 1: OCR. If another caller already OCR'd this page while we waited, it's a cache hit.
        val ocrPage: OcrPage = ocrMutex.withLock {
            synchronized(ocrCache) { ocrCache[ocrKey] } ?: run {
                val bytes = loadBytes() ?: return Outcome.Failed("Couldn't read the page image")
                val recognized = recognize(bytes, model)
                    ?: return Outcome.Failed("Couldn't decode the page image for OCR")
                synchronized(ocrCache) { ocrCache[ocrKey] = recognized }
                recognized
            }
        }

        if (ocrPage.results.isEmpty()) {
            val ui = WebtoonOverlayUi(emptyList(), emptyList(), false, ocrPage.width, ocrPage.height)
            synchronized(uiCache) { uiCache[uiKey(pageKey)] = ui }
            return Outcome.Ready(ui)
        }

        // Stage 2: translate. Meanwhile the next page can already be in stage 1.
        return translateMutex.withLock {
            cached(pageKey)?.let { return@withLock Outcome.Ready(it) }

            when (val result = translator.translate(ocrPage.results)) {
                is OcrTranslator.Result.Success -> {
                    val ui = WebtoonOverlayUi(
                        ocr = ocrPage.results,
                        translated = result.texts,
                        processing = false,
                        width = ocrPage.width,
                        height = ocrPage.height,
                    )
                    synchronized(uiCache) { uiCache[uiKey(pageKey)] = ui }
                    Outcome.Ready(ui)
                }
                is OcrTranslator.Result.Failure -> Outcome.Failed(result.message)
            }
        }
    }

    private suspend fun recognize(bytes: ByteArray, model: String): OcrPage? {
        @Suppress("DEPRECATION")
        val decoder = BitmapRegionDecoder.newInstance(ByteArrayInputStream(bytes), false) ?: return null
        try {
            val width = decoder.width
            val height = decoder.height
            val sample = if (width > MAX_TILE_WIDTH) 2 else 1
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val merged = mutableListOf<TextRecognitionResult>()
            var top = 0
            while (top < height) {
                currentCoroutineContext().ensureActive()
                val bottom = minOf(top + TILE_HEIGHT, height)
                val tile = decoder.decodeRegion(Rect(0, top, width, bottom), options) ?: break
                try {
                    val found = interactor.recognizeText(tile, model)
                    val shifted = found.mapNotNull { result ->
                        val box = result.boundingBox ?: return@mapNotNull null
                        result.copy(
                            boundingBox = Rect(
                                box.left * sample,
                                top + box.top * sample,
                                box.right * sample,
                                top + box.bottom * sample,
                            ),
                        )
                    }
                    mergeInto(merged, shifted)
                } finally {
                    tile.recycle()
                }
                if (bottom >= height) break
                top = bottom - TILE_OVERLAP
            }
            logcat(LogPriority.DEBUG) { "$TAG: ${merged.size} text regions on ${width}x$height page (model=$model)" }
            return OcrPage(width, height, merged)
        } finally {
            decoder.recycle()
        }
    }

    /** Adds [incoming] to [target], dropping regions already found in the overlap between tiles. */
    private fun mergeInto(target: MutableList<TextRecognitionResult>, incoming: List<TextRecognitionResult>) {
        for (item in incoming) {
            val box = item.boundingBox ?: continue
            val index = target.indexOfFirst { existing ->
                existing.boundingBox?.let { overlapRatio(it, box) > 0.5f } == true
            }
            when {
                index == -1 -> target.add(item)
                item.text.length > target[index].text.length -> target[index] = item
            }
        }
    }

    private fun overlapRatio(a: Rect, b: Rect): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        if (left >= right || top >= bottom) return 0f
        val intersection = (right - left).toLong() * (bottom - top)
        val smaller = minOf(
            a.width().toLong() * a.height(),
            b.width().toLong() * b.height(),
        )
        return if (smaller <= 0L) 0f else intersection.toFloat() / smaller.toFloat()
    }

    /** Picks the ML Kit recognizer that matches the configured source language. */
    private fun modelFor(sourceLanguage: String): String = when {
        sourceLanguage.startsWith("ja", ignoreCase = true) -> "japanese"
        sourceLanguage.startsWith("zh", ignoreCase = true) -> "chinese"
        sourceLanguage.startsWith("ko", ignoreCase = true) -> "korean"
        sourceLanguage.startsWith("en", ignoreCase = true) -> "latin"
        else -> "all"
    }

    private fun uiKey(pageKey: String): String {
        val prefs = Injekt.get<TranslationPreferences>()
        val target = Injekt.get<PreferenceStore>()
            .getString(SettingsTranslationScreen.OCR_TARGET_LANGUAGE_KEY, "en")
            .get()
        return "$pageKey|${prefs.selectedEngineId().get()}|${prefs.sourceLanguage().get()}|$target"
    }
}
