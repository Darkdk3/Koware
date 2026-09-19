package eu.kanade.presentation.reader

import android.graphics.Typeface
import android.text.Html
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
import androidx.compose.runtime.remember
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
import kotlin.math.abs

/**
 * Overlay that draws OCR-detected text regions on top of a manga page (live translation).
 *
 * - A region with no translation yet (blank entry in [translatedResults]) only gets a thin outline.
 * - A translated region is covered by a white box and the translation is wrapped inside it, shrinking
 *   the font until it fits. Tiny boxes are widened/heightened (around their center) so the text is
 *   always readable.
 *
 * Mapping from image pixels to the overlay:
 * - if the overlay has (about) the same aspect ratio as the image (a webtoon page view), the image is
 *   stretched over the whole overlay;
 * - otherwise (a full-screen overlay over a pager page) the image is treated as fit-center/letterboxed.
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

    val cleaned = remember(translatedResults) { translatedResults.map(::cleanTranslation) }

    val outlineColor = Color(0xAA00BCD4)
    val coverColor = Color(0xFAFFFFFF)
    val borderColor = Color(0x33000000)

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (imageWidth <= 0 || imageHeight <= 0 || size.width <= 0f || size.height <= 0f) return@Canvas

            val viewAspect = size.width / size.height
            val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()
            val scaleX: Float
            val scaleY: Float
            val offsetX: Float
            val offsetY: Float
            if (abs(viewAspect / imageAspect - 1f) < 0.06f) {
                scaleX = size.width / imageWidth
                scaleY = size.height / imageHeight
                offsetX = 0f
                offsetY = 0f
            } else {
                val s = minOf(size.width / imageWidth, size.height / imageHeight)
                scaleX = s
                scaleY = s
                offsetX = (size.width - imageWidth * s) / 2f
                offsetY = (size.height - imageHeight * s) / 2f
            }

            val paint = TextPaint().apply {
                isAntiAlias = true
                color = android.graphics.Color.BLACK
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val pad = 6.dp.toPx()
            val maxTextPx = 22.sp.toPx()
            val minTextPx = 10.sp.toPx()
            val stepPx = 1.sp.toPx()
            val minBoxW = minOf(72.dp.toPx(), size.width - 16.dp.toPx()).coerceAtLeast(1f)

            ocrResults.forEachIndexed { index, result ->
                val box = result.boundingBox ?: return@forEachIndexed

                val left = offsetX + box.left * scaleX
                val top = offsetY + box.top * scaleY
                val width = (box.right - box.left) * scaleX
                val height = (box.bottom - box.top) * scaleY

                val translated = cleaned.getOrNull(index)?.takeIf { it.isNotBlank() }
                if (translated == null) {
                    // Not translated (yet): outline only, so the original text stays readable.
                    drawRect(
                        color = outlineColor,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = Stroke(width = 2f),
                    )
                    return@forEachIndexed
                }

                // Box we draw into: at least minBoxW wide, and tall enough for the text.
                val boxW = maxOf(width, minBoxW)
                val availW = (boxW - pad * 2).toInt().coerceAtLeast(1)

                var textSizePx = maxTextPx
                paint.textSize = textSizePx
                var layout = buildLayout(translated, paint, availW)
                while (layout.height > height - pad * 2 && textSizePx > minTextPx) {
                    textSizePx -= stepPx
                    paint.textSize = textSizePx
                    layout = buildLayout(translated, paint, availW)
                }

                val boxH = maxOf(height, layout.height + pad * 2)
                val centerX = left + width / 2f
                val centerY = top + height / 2f
                val rectLeft = (centerX - boxW / 2f).coerceIn(0f, (size.width - boxW).coerceAtLeast(0f))
                val rectTop = (centerY - boxH / 2f).coerceIn(0f, (size.height - boxH).coerceAtLeast(0f))
                val radius = minOf(boxW, boxH) / 6f

                drawRoundRect(
                    color = coverColor,
                    topLeft = Offset(rectLeft, rectTop),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(radius, radius),
                )
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(rectLeft, rectTop),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(width = 1.dp.toPx()),
                )

                val finalLayout = layout
                val textTop = rectTop + (boxH - finalLayout.height) / 2f
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.translate(rectLeft + pad, textTop)
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

/** Engines often return "<p>text</p>"; strip tags/entities and collapse whitespace for display. */
private fun cleanTranslation(raw: String): String {
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

private fun buildLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setIncludePad(false)
        .build()
}
