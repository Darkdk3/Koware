package tachiyomi.presentation.core.util

import androidx.compose.runtime.staticCompositionLocalOf
import dev.chrisbanes.haze.HazeState

/**
 * Shared blur source for the app. The root Composable that hosts a screen's
 * content (see HomeScreen) marks itself as the haze source and provides its
 * [HazeState] here, so that any bottom sheet or dialog further down the tree
 * can read the same instance and blur whatever's currently on screen behind
 * it — without a direct dependency between the screen and the sheet.
 *
 * Defaults to null: a sheet reading a null value here simply can't offer a
 * Frosted background (it falls back to a flat tint) - it never crashes.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }
