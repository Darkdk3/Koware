package eu.kanade.tachiyomi.data.translation.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.TextRecognizerOptions as ChineseOptions
import com.google.mlkit.vision.text.japanese.TextRecognizerOptions as JapaneseOptions
import com.google.mlkit.vision.text.korean.TextRecognizerOptions as KoreanOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class TextRecognitionInteractor(private val context: Context) {

    data class DetectedBlock(
        val text: String,
        val bounds: RectF,
        val confidence: Float,
        val textBlockId: String,
    ) {
        val centerX: Float get() = bounds.centerX()
        val centerY: Float get() = bounds.centerY()
    }

    suspend fun recognize(bitmap: Bitmap): List<DetectedBlock> = withContext(Dispatchers.IO) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val results = coroutineScope {
            val latinDeferred = async { recognizeWith(inputImage, TextRecognizerOptions.Builder().build()) }
            val japaneseDeferred = async { recognizeWith(inputImage, JapaneseOptions.Builder().build()) }
            val chineseDeferred = async { recognizeWith(inputImage, ChineseOptions.Builder().build()) }
            val koreanDeferred = async { recognizeWith(inputImage, KoreanOptions.Builder().build()) }
            listOf(latinDeferred.await(), japaneseDeferred.await(), chineseDeferred.await(), koreanDeferred.await())
        }
        val allBlocks = results.flatten()
        val deduped = deduplicateBlocks(allBlocks)
        val merged = mergeNearbyBlocks(deduped)
        logcat(LogPriority.DEBUG) { "OCR: ${merged.size} blocks after merge from ${allBlocks.size} raw blocks" }
        merged.sortedWith(compareBy({ it.centerY }, { it.centerX }))
    }

    private suspend fun recognizeWith(
        inputImage: InputImage,
        options: com.google.mlkit.vision.text.TextRecognizerOptions,
    ): List<DetectedBlock> {
        return try {
            val recognizer = TextRecognition.getClient(options)
            val task = recognizer.process(inputImage)
            val result = suspendCancellableCoroutine<Text> { continuation ->
                task.addOnSuccessListener { result -> continuation.resume(result) }
                    .addOnFailureListener { e -> continuation.resumeWithException(e) }
            }
            extractBlocks(result)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "OCR recognition failed" }
            emptyList()
        }
    }

    private fun extractBlocks(result: Text): List<DetectedBlock> {
        val blocks = mutableListOf<DetectedBlock>()
        for (textBlock in result.textBlocks) {
            val text = textBlock.text.trim()
            if (text.isBlank()) continue
            val confidence = textBlock.confidence ?: 0f
            val rect = textBlock.boundingRect
            blocks.add(DetectedBlock(
                text = text,
                bounds = RectF(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat()),
                confidence = confidence,
                textBlockId = textBlock.id,
            ))
        }
        return blocks
    }

    private fun deduplicateBlocks(blocks: List<DetectedBlock>): List<DetectedBlock> {
        if (blocks.isEmpty()) return emptyList()
        val sorted = blocks.sortedByDescending { it.confidence }
        val result = mutableListOf<DetectedBlock>()
        val used = BooleanArray(blocks.size)
        for (i in sorted.indices) {
            if (used[i]) continue
            result.add(sorted[i])
            used[i] = true
            for (j in i + 1 until sorted.size) {
                if (used[j]) continue
                if (computeOverlap(sorted[i].bounds, sorted[j].bounds) > 0.5f) used[j] = true
            }
        }
        return result
    }

    private fun mergeNearbyBlocks(blocks: List<DetectedBlock>): List<DetectedBlock> {
        if (blocks.isEmpty()) return emptyList()
        val sorted = blocks.sortedWith(compareBy({ it.centerY }, { it.centerX }))
        val result = mutableListOf<DetectedBlock>()
        var currentGroup = mutableListOf(sorted[0])
        for (i in 1 until sorted.size) {
            val block = sorted[i]
            val last = currentGroup.last()
            if (kotlin.math.abs(block.centerY - last.centerY) < 150f &&
                kotlin.math.abs(block.centerX - last.centerX) < 200f) {
                currentGroup.add(block)
            } else {
                result.add(mergeGroup(currentGroup))
                currentGroup = mutableListOf(block)
            }
        }
        result.add(mergeGroup(currentGroup))
        return result
    }

    private fun mergeGroup(group: List<DetectedBlock>): DetectedBlock {
        if (group.size == 1) return group[0]
        val mergedText = group.joinToString(" ") { it.text }
        val left = group.minOf { it.bounds.left }
        val top = group.minOf { it.bounds.top }
        val right = group.maxOf { it.bounds.right }
        val bottom = group.maxOf { it.bounds.bottom }
        val avgConfidence = group.sumOf { it.confidence } / group.size
        return DetectedBlock(
            text = mergedText, bounds = RectF(left, top, right, bottom),
            confidence = avgConfidence, textBlockId = group.joinToString("_") { it.textBlockId },
        )
    }

    private fun computeOverlap(r1: RectF, r2: RectF): Float {
        val intersection = RectF(
            kotlin.math.max(r1.left, r2.left), kotlin.math.max(r1.top, r2.top),
            kotlin.math.min(r1.right, r2.right), kotlin.math.min(r1.bottom, r2.bottom),
        )
        if (intersection.isEmpty) return 0f
        val intersectionArea = intersection.width() * intersection.height()
        val minArea = kotlin.math.min(r1.width() * r1.height(), r2.width() * r2.height())
        return if (minArea > 0) intersectionArea / minArea else 0f
    }

    suspend fun translateBlocks(
        blocks: List<DetectedBlock>,
        targetLanguage: String,
        translateFn: suspend (String, String, String) -> String,
    ): List<TranslatedBlock> = coroutineScope {
        blocks.map { block ->
            async {
                try {
                    TranslatedBlock(original = block, translatedText = translateFn(block.text, "auto", targetLanguage))
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to translate block" }
                    TranslatedBlock(original = block, translatedText = block.text)
                }
            }
        }.awaitAll().flatten()
    }

    data class TranslatedBlock(
        val original: DetectedBlock,
        val translatedText: String,
    ) {
        val bounds: RectF get() = original.bounds
    }
}
