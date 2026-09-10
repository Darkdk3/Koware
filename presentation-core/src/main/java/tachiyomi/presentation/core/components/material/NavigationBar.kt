package tachiyomi.presentation.core.components.material

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

/**
 * Floating M3-style navbar with no horizontal spacer, an adjustable [height] and
 * [itemSpacing], a configurable [shape] (plain rectangle by default), and an optional
 * frosted-glass background driven by [hazeState].
 *
 * @see [androidx.compose.material3.NavigationBar]
 */
@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    containerColor: Color = NavigationBarDefaults.containerColor,
    containerAlpha: Float = 1f,
    contentColor: Color = MaterialTheme.colorScheme.contentColorFor(containerColor),
    tonalElevation: Dp = NavigationBarDefaults.Elevation,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    shape: Shape = RectangleShape,
    height: Dp = 80.dp,
    itemSpacing: Dp = 0.dp,
    hazeState: HazeState? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        color = containerColor.copy(alpha = containerAlpha),
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shape = shape,
        modifier = if (hazeState != null) {
            modifier.hazeEffect(state = hazeState) {
                style = HazeStyle(backgroundColor = containerColor, tint = null)
            }
        } else {
            modifier
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .height(height)
                .selectableGroup(),
            horizontalArrangement = if (itemSpacing > 0.dp) {
                Arrangement.spacedBy(itemSpacing, Alignment.CenterHorizontally)
            } else {
                Arrangement.Start
            },
            content = content,
        )
    }
}