package eu.kanade.presentation.manga.merge

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * All Haze usage is isolated in this file, written against Haze 1.6.0 (the version Koware already uses).
 *
 * The Haze calls are:
 *  - [rememberGlassState]  -> rememberHazeState()
 *  - [glassSource]         -> Modifier.hazeSource(state)
 *  - [glass]               -> Modifier.hazeEffect(state) { blurRadius, tints }
 *
 * If one of them does not compile against the project's exact Haze version, fix it here only.
 */

@Immutable
internal data class GlassColors(
    val tint: Color,
    val strongTint: Color,
    val border: Color,
)

@Composable
internal fun glassColors(): GlassColors {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    return if (dark) {
        GlassColors(
            tint = Color.White.copy(alpha = 0.10f),
            strongTint = Color.White.copy(alpha = 0.18f),
            border = Color.White.copy(alpha = 0.28f),
        )
    } else {
        GlassColors(
            tint = Color.White.copy(alpha = 0.45f),
            strongTint = Color.White.copy(alpha = 0.65f),
            border = scheme.outline.copy(alpha = 0.35f),
        )
    }
}

@Composable
internal fun rememberGlassState(): HazeState = rememberHazeState()

/** Marks content that glass panels blur. Must not be an ancestor of any panel using the same state. */
internal fun Modifier.glassSource(state: HazeState): Modifier = hazeSource(state = state)

/**
 * Frosted panel. Pass a [state] for real background blur. Pass null for panels that live inside the
 * [glassSource] (for example items in the scrolling grid), which get a translucent fill only.
 */
internal fun Modifier.glass(
    state: HazeState?,
    colors: GlassColors,
    shape: Shape,
    strong: Boolean = false,
    radius: Dp = 24.dp,
): Modifier {
    val tint = if (strong) colors.strongTint else colors.tint
    val clipped = this.clip(shape)
    val filled = if (state != null) {
        clipped.hazeEffect(state = state) {
            blurRadius = radius
            tints = listOf(HazeTint(tint))
        }
    } else {
        clipped.background(tint)
    }
    return filled.border(width = 0.5.dp, color = colors.border, shape = shape)
}

/** Soft colored lights behind the content so the glass has something to blur. Theme-aware. */
@Composable
internal fun GlassBackdrop(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val strength = if (dark) 0.45f else 0.30f
    val base = scheme.background
    val a = scheme.primary
    val b = scheme.tertiary
    val c = scheme.secondary

    Canvas(modifier = modifier.background(base)) {
        val w = size.width
        val h = size.height
        val r = size.maxDimension * 0.55f

        fun blob(color: Color, center: Offset) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = strength), Color.Transparent),
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
        }

        blob(a, Offset(w * 0.10f, h * 0.06f))
        blob(b, Offset(w * 0.95f, h * 0.45f))
        blob(c, Offset(w * 0.05f, h * 0.95f))
    }
}