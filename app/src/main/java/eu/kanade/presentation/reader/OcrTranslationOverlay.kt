package eu.kanade.presentation.reader

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.data.translation.ocr.TextRecognitionInteractor.TextRecognitionResult

/**
 * Overlay that draws OCR-detected text regions on top of the manga page (live translation).
 *
 * - While a region has no translation yet (blank entry in [translatedResults]), only a thin outline is drawn.
 * - Once translated, the region is covered with a white box and the translation is wrapped inside it,
 *   shrinking the font until it fits.
 *
 * Boxes are mapped assuming the page is drawn fit-center inside this overlay (letterboxed), which matches
 * the paged viewers. Webtoon/continuous modes scroll, so boxes won't track the page there.
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

    val outlineColor = Color(0xAA00BCD4)
    val coverColor = Color(0xF2FFFFFF)

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (imageWidth <= 0 || imageHeight <= 0) return@Canvas

            // Fit-center mapping from bitmap coordinates to overlay coordinates.
            val scale = minOf(size.width / imageWidth.toFloat(), size.height / imageHeight.toFloat())
            val offsetX = (size.width - imageWidth * scale) / 2f
            val offsetY = (size.height - imageHeight * scale) / 2f

            val paint = TextPaint().apply {
                isAntiAlias = true
                color = android.graphics.Color.BLACK
            }
            val pad = 4.dp.toPx()
            val maxTextPx = 18.sp.toPx()
            val minTextPx = 9.sp.toPx()
            val stepPx = 1.sp.toPx()

            ocrResults.forEachIndexed { index, result ->
                val box = result.boundingBox ?: return@forEachIndexed

                val left = offsetX + box.left * scale
                val top = offsetY + box.top * scale
                val width = (box.right - box.left) * scale
                val height = (box.bottom - box.top) * scale

                val translated = translatedResults.getOrNull(index)?.takeIf { it.isNotBlank() }
                if (translated == null) {
                    // Not translated yet: outline only so the original stays readable.
                    drawRect(
                        color = outlineColor,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = Stroke(width = 2f),
                    )
                    return@forEachIndexed
                }

                drawRoundRect(
                    color = coverColor,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(8f, 8f),
                )

                val availW = (width - pad * 2).toInt().coerceAtLeast(1)
                val availH = (height - pad * 2).coerceAtLeast(1f)

                var textSizePx = maxTextPx
                paint.textSize = textSizePx
                var layout = buildLayout(translated, paint, availW)
                while (layout.height > availH && textSizePx > minTextPx) {
                    textSizePx -= stepPx
                    paint.textSize = textSizePx
                    layout = buildLayout(translated, paint, availW)
                }

                val finalLayout = layout
                val textTop = top + pad + ((availH - finalLayout.height) / 2f).coerceAtLeast(0f)
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.translate(left + pad, textTop)
                    finalLayout.draw(native)
                    native.restore()
                }
            }
        }

        if (isProcessing) {
            Text(
                text = if (ocrResults.isEmpty()) "Recognizing text..." else "Translating...",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

private fun buildLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setIncludePad(false)
        .build()
}
