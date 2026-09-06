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
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * M3 Navbar with no horizontal spacer
 *
 * @see [androidx.compose.material3.NavigationBar]
 */
@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    height: Dp = 80.dp,
    itemSpacing: Dp = 0.dp,
    containerColor: Color = Color.Transparent,
    containerAlpha: Float = 1f,
    hazeState: HazeState? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    tonalElevation: Dp = 0.dp,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    content: @Composable RowScope.() -> Unit,
) {
    val resolvedColor = containerColor.copy(alpha = containerAlpha)
    androidx.compose.material3.Surface(
        shape = shape,
        // When hazing, the surface itself stays transparent; the tint is applied
        // by the haze effect instead so the blur underneath still shows through.
        color = if (hazeState != null) Color.Transparent else resolvedColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        modifier = if (hazeState != null) {
            modifier.hazeEffect(
                state = hazeState,
                style = HazeStyle(tint = HazeTint(resolvedColor)),
            )
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
                Arrangement.SpaceBetween
            },
            content = content,
        )
    }
}