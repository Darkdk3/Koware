package eu.kanade.tachiyomi.util.manga

import androidx.core.graphics.ColorUtils

/**
 * Turns a cover seed color into a reader-safe background + matching text color.
 * Background is pushed very dark/light so long-form text stays readable.
 */
object NovelCoverColors {

    fun background(seed: Int, isDark: Boolean): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(seed, hsl)
        hsl[1] = hsl[1].coerceAtMost(if (isDark) 0.45f else 0.50f)
        hsl[2] = if (isDark) 0.10f else 0.94f
        return ColorUtils.HSLToColor(hsl) or 0xFF000000.toInt()
    }

    fun text(background: Int): Int =
        if (ColorUtils.calculateLuminance(background) > 0.5) {
            0xFF1A1A1A.toInt()
        } else {
            0xFFE6E6E6.toInt()
        }
}
