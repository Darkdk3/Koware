package eu.kanade.tachiyomi.data.translation.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text as MLText
import com.google.mlkit.vision.text.TextRecognition as MLTextRecognition
import com.google.mlkit.vision.text.TextRecognizer as MLTextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class TextRecognitionInteractor {

    // Recognizers are created on first use (inside the try/catch in recognizeText) so a broken or
    // missing language module can't take down the others, and its error is reported instead of lost.
    private val latinRecognizer: MLTextRecognizer by lazy {
        MLTextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val japaneseRecognizer: MLTextRecognizer by lazy {
        MLTextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }

    private val chineseRecognizer: MLTextRecognizer by lazy {
        MLTextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    private val koreanRecognizer: MLTextRecognizer by lazy {
        MLTextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }

    private val recognitionCache = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<Int, List<TextRecognitionResult>>(30, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<Int, List<TextRecognitionResult>>?,
            ): Boolean = size > 30
        },
    )

    /**
     * Recognize text in a bitmap using the selected model set.
     *
     * Each returned region is one speech bubble / text area: nearby text blocks are grouped, then the
     * box is grown outwards until it reaches the bubble outline, so the translation can be drawn
     * inside the bubble instead of only over the original text columns.
     *
     * Throws if every selected recognizer failed (so callers can show the real error); if only some
     * failed, the ones that worked are used and the failures are logged.
     *
     * @param bitmap the page image to process
     * @param model one of "all", "latin", "cjk", "japanese", "chinese", "korean"
     */
    suspend fun recognizeText(bitmap: Bitmap, model: String = "all"): List<TextRecognitionResult> = coroutineScope {
        val cacheKey = if (bitmap.generationId != 0) {
            bitmap.generationId
        } else {
            bitmap.width * 31 + bitmap.height + bitmap.byteCount + model.hashCode()
        }
        recognitionCache[cacheKey]?.let { return@coroutineScope it }

        val image = InputImage.fromBitmap(bitmap, 0)

        val useLatin = model == "all" || model == "latin"
        val useJapanese = model == "all" || model == "japanese" || model == "cjk"
        val useChinese = model == "all" || model == "chinese" || model == "cjk"
        val useKorean = model == "all" || model == "korean" || model == "cjk"

        val failures = java.util.Collections.synchronizedList(mutableListOf<Throwable>())

        suspend fun blocksOf(name: String, recognizer: () -> MLTextRecognizer): List<MLText.TextBlock> {
            return try {
                recognizer().process(image).await().textBlocks
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "OCR: $name recognizer failed" }
                failures.add(e)
                emptyList()
            }
        }

        val latinJob = if (useLatin) async { blocksOf("latin") { latinRecognizer } } else null
        val japaneseJob = if (useJapanese) async { blocksOf("japanese") { japaneseRecognizer } } else null
        val chineseJob = if (useChinese) async { blocksOf("chinese") { chineseRecognizer } } else null
        val koreanJob = if (useKorean) async { blocksOf("korean") { koreanRecognizer } } else null

        val allBlocks: List<MLText.TextBlock> =
            (latinJob?.await() ?: emptyList()) +
                (japaneseJob?.await() ?: emptyList()) +
                (chineseJob?.await() ?: emptyList()) +
                (koreanJob?.await() ?: emptyList())

        val enabledCount = listOf(useLatin, useJapanese, useChinese, useKorean).count { it }
        if (enabledCount > 0 && failures.size >= enabledCount) {
            val first = failures.first()
            throw IllegalStateException(
                "Text recognition failed ($enabledCount recognizer(s)): ${first.message ?: first::class.simpleName}",
                first,
            )
        }

        // 1) Drop duplicate blocks found by more than one recognizer.
        val uniqueBlocks = mutableListOf<MLText.TextBlock>()
        for (block in allBlocks) {
            var isDuplicate = false
            var duplicateIndexToReplace = -1
            for (idx in uniqueBlocks.indices) {
                val existing = uniqueBlocks[idx]
                val existingBox: Rect? = existing.boundingBox
                val newBox: Rect? = block.boundingBox
                if (existingBox != null && newBox != null) {
                    if (overlapRatio(existingBox, newBox) > 0.5f) {
                        isDuplicate = true
                        if (block.text.length > existing.text.length) {
                            duplicateIndexToReplace = idx
                        }
                        break
                    }
                } else if (existing.text == block.text) {
                    isDuplicate = true
                    break
                }
            }
            if (block.text.isNotBlank()) {
                if (duplicateIndexToReplace != -1) {
                    uniqueBlocks[duplicateIndexToReplace] = block
                } else if (!isDuplicate) {
                    uniqueBlocks.add(block)
                }
            }
        }

        // 2) Group blocks that belong together (neighbouring columns / stacked lines of one bubble).
        val grouped = mutableListOf<TextRecognitionResult>()
        val visited = BooleanArray(uniqueBlocks.size)
        for (i in uniqueBlocks.indices) {
            if (visited[i]) continue
            visited[i] = true
            val currentBox: Rect = uniqueBlocks[i].boundingBox?.let { Rect(it) } ?: Rect(0, 0, 0, 0)
            val cluster = mutableListOf(i)
            var expanded = true
            while (expanded) {
                expanded = false
                for (j in uniqueBlocks.indices) {
                    if (visited[j]) continue
                    val targetBox: Rect = uniqueBlocks[j].boundingBox ?: continue
                    if (shouldMerge(currentBox, targetBox)) {
                        visited[j] = true
                        cluster.add(j)
                        currentBox.set(
                            minOf(currentBox.left, targetBox.left),
                            minOf(currentBox.top, targetBox.top),
                            maxOf(currentBox.right, targetBox.right),
                            maxOf(currentBox.bottom, targetBox.bottom),
                        )
                        expanded = true
                    }
                }
            }

            // Reading order: vertical text reads right-to-left, horizontal text top-to-bottom.
            val vertical = currentBox.height() > currentBox.width()
            val sortedClusterIndices = cluster.sortedWith { idx1, idx2 ->
                val box1: Rect = uniqueBlocks[idx1].boundingBox ?: Rect()
                val box2: Rect = uniqueBlocks[idx2].boundingBox ?: Rect()
                if (vertical) {
                    val xCompare = box2.left.compareTo(box1.left)
                    if (xCompare != 0) xCompare else box1.top.compareTo(box2.top)
                } else {
                    val yCompare = box1.top.compareTo(box2.top)
                    if (yCompare != 0) yCompare else box1.left.compareTo(box2.left)
                }
            }
            val mergedText = sortedClusterIndices.map {
                uniqueBlocks[it].text.replace("\n", " ").trim()
            }.filter { it.isNotEmpty() }.joinToString(" ")

            grouped.add(
                TextRecognitionResult(
                    text = mergedText,
                    boundingBox = expandToBubble(bitmap, currentBox),
                ),
            )
        }

        // 3) Two groups that grew into the same bubble become one region.
        val mergedResults = mutableListOf<TextRecognitionResult>()
        for (item in grouped) {
            val box = item.boundingBox
            if (box == null) {
                mergedResults.add(item)
                continue
            }
            val index = mergedResults.indexOfFirst { existing ->
                existing.boundingBox?.let { overlapRatio(it, box) > 0.6f } == true
            }
            if (index == -1) {
                mergedResults.add(item)
            } else {
                val existing = mergedResults[index]
                val existingBox = existing.boundingBox!!
                val union = Rect(
                    minOf(existingBox.left, box.left),
                    minOf(existingBox.top, box.top),
                    maxOf(existingBox.right, box.right),
                    maxOf(existingBox.bottom, box.bottom),
                )
                val ordered = if (union.height() > union.width()) {
                    if (box.centerX() > existingBox.centerX()) listOf(item, existing) else listOf(existing, item)
                } else {
                    if (box.centerY() < existingBox.centerY()) listOf(item, existing) else listOf(existing, item)
                }
                mergedResults[index] = TextRecognitionResult(
                    text = ordered.joinToString(" ") { it.text },
                    boundingBox = union,
                )
            }
        }

        recognitionCache[cacheKey] = mergedResults
        mergedResults
    }

    /**
     * Two boxes belong to the same bubble when they are close (relative to the letter size) AND lined
     * up: side by side with a vertical overlap (neighbouring vertical columns), or stacked with a
     * horizontal overlap (lines of horizontal text). This stops unrelated bubbles that merely sit
     * near each other diagonally from being merged.
     */
    private fun shouldMerge(a: Rect, b: Rect): Boolean {
        val horizontalDist = maxOf(0, maxOf(a.left - b.right, b.left - a.right))
        val verticalDist = maxOf(0, maxOf(a.top - b.bottom, b.top - a.bottom))

        val charSize = minOf(minOf(a.width(), a.height()), minOf(b.width(), b.height()))
        val threshold = (charSize * 1.5f).toInt().coerceIn(20, 90)

        val verticalOverlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        val horizontalOverlap = minOf(a.right, b.right) - maxOf(a.left, b.left)

        val sideBySide = horizontalDist <= threshold &&
            verticalOverlap > 0.3f * minOf(a.height(), b.height())
        val stacked = verticalDist <= threshold &&
            horizontalOverlap > 0.3f * minOf(a.width(), b.width())
        return sideBySide || stacked
    }

    /**
     * Grows [box] outwards until each side reaches the dark bubble outline (or [MAX_GROW_FACTOR] of
     * the box size), so the translation gets the whole white bubble interior to sit in. If the
     * bitmap can't be read, or the text sits directly on artwork, the box is returned unchanged.
     */
    private fun expandToBubble(bitmap: Bitmap, box: Rect): Rect {
        if (box.width() <= 0 || box.height() <= 0) return box
        if (bitmap.config == Bitmap.Config.HARDWARE || bitmap.isRecycled) return box
        return try {
            val w = bitmap.width
            val h = bitmap.height
            val rect = Rect(
                box.left.coerceIn(0, w - 1),
                box.top.coerceIn(0, h - 1),
                box.right.coerceIn(1, w),
                box.bottom.coerceIn(1, h),
            )
            if (rect.width() <= 0 || rect.height() <= 0) return box

            val maxGrow = (maxOf(rect.width(), rect.height()) * MAX_GROW_FACTOR).toInt().coerceIn(24, 240)
            val step = 2
            var growLeft = 0
            var growTop = 0
            var growRight = 0
            var growBottom = 0
            var moved = true
            while (moved) {
                moved = false
                if (growLeft < maxGrow && rect.left - step >= 0 &&
                    !isDarkLine(bitmap, rect.left - step, rect.top, rect.left - step, rect.bottom)
                ) {
                    rect.left -= step
                    growLeft += step
                    moved = true
                }
                if (growTop < maxGrow && rect.top - step >= 0 &&
                    !isDarkLine(bitmap, rect.left, rect.top - step, rect.right, rect.top - step)
                ) {
                    rect.top -= step
                    growTop += step
                    moved = true
                }
                if (growRight < maxGrow && rect.right + step < w &&
                    !isDarkLine(bitmap, rect.right + step, rect.top, rect.right + step, rect.bottom)
                ) {
                    rect.right += step
                    growRight += step
                    moved = true
                }
                if (growBottom < maxGrow && rect.bottom + step < h &&
                    !isDarkLine(bitmap, rect.left, rect.bottom + step, rect.right, rect.bottom + step)
                ) {
                    rect.bottom += step
                    growBottom += step
                    moved = true
                }
            }
            // Never smaller than the recognized text itself.
            Rect(
                minOf(rect.left, box.left),
                minOf(rect.top, box.top),
                maxOf(rect.right, box.right),
                maxOf(rect.bottom, box.bottom),
            )
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "OCR: couldn't expand box to bubble" }
            box
        }
    }

    /** True if a large share of the pixels on the segment are dark (bubble outline / artwork). */
    private fun isDarkLine(bitmap: Bitmap, x1: Int, y1: Int, x2: Int, y2: Int): Boolean {
        val length = maxOf(x2 - x1, y2 - y1)
        if (length <= 0) return false
        val maxX = bitmap.width - 1
        val maxY = bitmap.height - 1
        var dark = 0
        var total = 0
        var t = 0
        while (t <= length) {
            val x = (if (x1 == x2) x1 else x1 + t).coerceIn(0, maxX)
            val y = (if (y1 == y2) y1 else y1 + t).coerceIn(0, maxY)
            val p = bitmap.getPixel(x, y)
            val luminance = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
            if (luminance < DARK_LUMINANCE) dark++
            total++
            t += 3
        }
        return total > 0 && dark.toFloat() / total > DARK_LINE_RATIO
    }

    private fun overlapRatio(a: Rect, b: Rect): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        if (left >= right || top >= bottom) return 0f
        val intersection = (right - left).toLong() * (bottom - top)
        val smaller = minOf(a.width().toLong() * a.height(), b.width().toLong() * b.height())
        return if (smaller <= 0L) 0f else intersection.toFloat() / smaller.toFloat()
    }

    data class TextRecognitionResult(
        val text: String,
        val boundingBox: Rect?,
    )

    private companion object {
        const val MAX_GROW_FACTOR = 0.6f
        const val DARK_LUMINANCE = 110
        const val DARK_LINE_RATIO = 0.15f
    }
}
