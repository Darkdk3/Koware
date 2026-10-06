package eu.kanade.presentation.components

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.lifecycle.DisposableEffectIgnoringConfiguration
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.ScreenTransition
import eu.kanade.presentation.util.isTabletUi
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.presentation.core.util.LocalHazeState
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.presentation.core.components.AdaptiveSheet as AdaptiveSheetImpl

/**
 * Set to `true` by hosts whose content behind the sheet is NOT Compose (e.g. ReaderActivity, whose
 * pages are native Views / a WebView). Haze can only blur Compose content registered through
 * `hazeSource`, so there it would blur nothing and leave the sheet looking flat and see-through.
 *
 * When this is true, Frosted/Grainy sheets use the system's window blur-behind instead (Android 12+),
 * which blurs whatever is behind the dialog window, native Views included. On older Android versions,
 * or when the system has cross-window blur disabled (battery saver, some OEM settings), the sheet falls
 * back to a solid surface so it stays readable.
 */
val LocalSheetWindowBlur = staticCompositionLocalOf { false }

private fun isWindowBlurSupported(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true

@OptIn(InternalVoyagerApi::class)
@Composable
fun NavigatorAdaptiveSheet(
    screen: Screen,
    enableSwipeDismiss: (Navigator) -> Boolean = { true },
    onDismissRequest: () -> Unit,
) {
    Navigator(
        screen = screen,
        content = { sheetNavigator ->
            AdaptiveSheet(
                onDismissRequest = onDismissRequest,
                enableImplicitDismiss = enableSwipeDismiss(sheetNavigator),
            ) {
                ScreenTransition(
                    navigator = sheetNavigator,
                    transition = {
                        fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                            fadeOut(animationSpec = tween(90))
                    },
                )
            }

            // Make sure screens are disposed no matter what
            if (sheetNavigator.parent?.disposeBehavior?.disposeNestedNavigators == false) {
                DisposableEffectIgnoringConfiguration {
                    onDispose {
                        sheetNavigator.items
                            .asReversed()
                            .forEach(sheetNavigator::dispose)
                    }
                }
            }
        },
    )
}

/**
 * Sheet with adaptive position aligned to bottom on small screen, otherwise aligned to center
 * and will not be able to dismissed with swipe gesture.
 *
 * Max width of the content is set to 460 dp.
 */
@Composable
fun AdaptiveSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    enableImplicitDismiss: Boolean = true,
    properties: DialogProperties = dialogProperties,
    content: @Composable () -> Unit,
) {
    val isTabletUi = isTabletUi()
    val context = LocalContext.current
    val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
    val backgroundStyle by libraryPreferences.sheetBackgroundStyle.collectAsState()
    val opacityPercent by libraryPreferences.sheetOpacityPercent.collectAsState()
    // Shared with HomeScreen's tab content; null (or Solid/Transparent style)
    // just means no blur is applied - see LocalHazeState for why.
    val hazeState = LocalHazeState.current

    val wantsFrost = backgroundStyle == LibraryPreferences.NavBarBackgroundStyle.Frosted ||
        backgroundStyle == LibraryPreferences.NavBarBackgroundStyle.Grainy
    val isNonComposeBackdrop = LocalSheetWindowBlur.current
    val windowBlur = isNonComposeBackdrop && wantsFrost && remember(context) { isWindowBlurSupported(context) }

    val containerAlpha = when {
        // Nothing can blur the backdrop here, so stay opaque instead of showing the raw reader through.
        isNonComposeBackdrop && wantsFrost && !windowBlur -> 1f
        else -> when (backgroundStyle) {
            LibraryPreferences.NavBarBackgroundStyle.Solid -> 1f
            LibraryPreferences.NavBarBackgroundStyle.Transparent,
            LibraryPreferences.NavBarBackgroundStyle.Frosted,
            LibraryPreferences.NavBarBackgroundStyle.Grainy,
            -> opacityPercent / 100f
        }
    }
    // Haze only where the backdrop is Compose content; reader sheets use window blur instead.
    val sheetHazeState = if (wantsFrost && !isNonComposeBackdrop) hazeState else null
    val sheetNoiseFactor = if (backgroundStyle == LibraryPreferences.NavBarBackgroundStyle.Grainy) {
        0.65f
    } else {
        0f
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        if (windowBlur) {
            val density = LocalDensity.current
            val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
            DisposableEffect(dialogWindow) {
                if (dialogWindow != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    dialogWindow.attributes = dialogWindow.attributes.apply {
                        blurBehindRadius = with(density) { 24.dp.roundToPx() }
                    }
                }
                onDispose { }
            }
        }

        AdaptiveSheetImpl(
            isTabletUi = isTabletUi,
            enableImplicitDismiss = enableImplicitDismiss,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            containerAlpha = containerAlpha,
            hazeState = sheetHazeState,
            noiseFactor = sheetNoiseFactor,
        ) {
            content()
        }
    }
}

val dialogProperties = DialogProperties(
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = true,
)

val imeAwareDialogProperties = DialogProperties(
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false,
)
