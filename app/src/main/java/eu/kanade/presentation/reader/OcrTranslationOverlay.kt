package eu.kanade.presentation.reader

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.data.translation.ocr.TextRecognitionInteractor.TextRecognitionResult

/**
 * Overlay that displays OCR-recognized text regions on top of the manga page.
 * Used by the live translation feature to show detected text bubbles.
 *
 * If a translation is missing for a region, a "…" placeholder is shown instead of
 * silently falling back to the original OCR text.
 */
@Composable
fun OcrTranslationOverlay(
    ocrResults: List<TextRecognitionResult>,
    translatedResults: List<String>,
    isProcessing: Boolean,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier,
) {
    if (ocrResults.isEmpty() && !isProcessing) return

    val density = LocalDensity.current
    val boxColor = Color(0x8800BCD4)
    val textColor = Color.White
    val bgColor = Color(0xCC000000)

    Box(modifier = modifier.fillMaxSize()) {
        if (isProcessing) {
            Text(
                text = "Recognizing text...",
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (imageWidth <= 0 || imageHeight <= 0) return@Canvas

            val scaleX = size.width / imageWidth.toFloat()
            val scaleY = size.height / imageHeight.toFloat()

            ocrResults.forEachIndexed { index, result ->
                val box = result.boundingBox ?: return@forEachIndexed
                val left = box.left * scaleX
                val top = box.top * scaleY
                val width = (box.right - box.left) * scaleX
                val height = (box.bottom - box.top) * scaleY

                // Draw bounding box
                drawRect(
                    color = boxColor,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    style = Stroke(width = 2f),
                )

                // Draw translated text background + text
                val displayText = translatedResults.getOrElse(index) { "…" }
                if (displayText.isNotBlank()) {
                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = with(density) { 12.sp.toPx() }
                        isAntiAlias = true
                    }
                    val textWidth = textPaint.measureText(displayText)
                    val textHeight = textPaint.textSize

                    // Background rect for text
                    drawRect(
                        color = bgColor,
                        topLeft = Offset(left, top - textHeight - 4f),
                        size = Size(textWidth + 8f, textHeight + 8f),
                    )

                    // Draw text
                    drawContext.canvas.nativeCanvas.drawText(
                        displayText,
                        left + 4f,
                        top - 4f,
                        textPaint,
                    )
                }
            }
        }
    }
}
