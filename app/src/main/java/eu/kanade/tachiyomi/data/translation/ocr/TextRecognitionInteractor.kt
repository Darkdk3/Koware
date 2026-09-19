package eu.kanade.tachiyomi.data.translation.ocr

import android.graphics.Bitmap
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

        val uniqueBlocks = mutableListOf<MLText.TextBlock>()
        for (block in allBlocks) {
            var isDuplicate = false
            var duplicateIndexToReplace = -1
            for (idx in uniqueBlocks.indices) {
                val existing = uniqueBlocks[idx]
                val existingBox: Rect? = existing.boundingBox
                val newBox: Rect? = block.boundingBox
                if (existingBox != null && newBox != null) {
                    val intersectionLeft = maxOf(existingBox.left, newBox.left)
                    val intersectionTop = maxOf(existingBox.top, newBox.top)
                    val intersectionRight = minOf(existingBox.right, newBox.right)
                    val intersectionBottom = minOf(existingBox.bottom, newBox.bottom)
                    if (intersectionLeft < intersectionRight && intersectionTop < intersectionBottom) {
                        val intersectionArea =
                            (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop)
                        val existingArea =
                            (existingBox.right - existingBox.left) * (existingBox.bottom - existingBox.top)
                        val newArea = (newBox.right - newBox.left) * (newBox.bottom - newBox.top)
                        val overlapRatio =
                            intersectionArea.toFloat() / minOf(existingArea, newArea).toFloat()
                        if (overlapRatio > 0.5f) {
                            isDuplicate = true
                            if (block.text.length > existing.text.length) {
                                duplicateIndexToReplace = idx
                            }
                            break
                        }
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

        val mergedResults = mutableListOf<TextRecognitionResult>()
        val visited = BooleanArray(uniqueBlocks.size)
        for (i in uniqueBlocks.indices) {
            if (visited[i]) continue
            visited[i] = true
            val currentBlock = uniqueBlocks[i]
            val currentBox: Rect = currentBlock.boundingBox?.let { Rect(it) } ?: Rect(0, 0, 0, 0)
            val cluster = mutableListOf(i)
            var expanded = true
            while (expanded) {
                expanded = false
                for (j in uniqueBlocks.indices) {
                    if (visited[j]) continue
                    val targetBlock = uniqueBlocks[j]
                    val targetBox: Rect = targetBlock.boundingBox ?: continue
                    val horizontalDist = maxOf(
                        0,
                        maxOf(currentBox.left - targetBox.right, targetBox.left - currentBox.right),
                    )
                    val verticalDist = maxOf(
                        0,
                        maxOf(currentBox.top - targetBox.bottom, targetBox.top - currentBox.bottom),
                    )
                    val currentHeight = currentBox.height()
                    val targetHeight = targetBox.height()
                    val threshold = (maxOf(currentHeight, targetHeight) * 1.5).toInt()
                        .coerceIn(30, 150)
                    if (horizontalDist <= threshold && verticalDist <= threshold) {
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

            val sortedClusterIndices = cluster.sortedWith { idx1, idx2 ->
                val box1: Rect = uniqueBlocks[idx1].boundingBox ?: Rect()
                val box2: Rect = uniqueBlocks[idx2].boundingBox ?: Rect()
                if (currentBox.height() > currentBox.width()) {
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
            mergedResults.add(
                TextRecognitionResult(
                    text = mergedText,
                    boundingBox = currentBox,
                ),
            )
        }

        recognitionCache[cacheKey] = mergedResults
        mergedResults
    }

    data class TextRecognitionResult(
        val text: String,
        val boundingBox: Rect?,
    )
}
