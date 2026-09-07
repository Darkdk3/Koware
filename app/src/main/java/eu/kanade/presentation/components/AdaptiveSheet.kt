package eu.kanade.presentation.components

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
    val backgroundStyle by libraryPreferences.sheetBackgroundStyle.collectAsState()
    val opacityPercent by libraryPreferences.sheetOpacityPercent.collectAsState()
    // Shared with HomeScreen's tab content; null (or Solid/Transparent style)
    // just means no blur is applied - see LocalHazeState for why.
    val hazeState = LocalHazeState.current

    val containerAlpha = when (backgroundStyle) {
        LibraryPreferences.NavBarBackgroundStyle.Solid -> 1f
        LibraryPreferences.NavBarBackgroundStyle.Transparent,
        LibraryPreferences.NavBarBackgroundStyle.Frosted,
        -> opacityPercent / 100f
    }
    val sheetHazeState = if (
        backgroundStyle == LibraryPreferences.NavBarBackgroundStyle.Frosted
    ) {
        hazeState
    } else {
        null
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        AdaptiveSheetImpl(
            isTabletUi = isTabletUi,
            enableImplicitDismiss = enableImplicitDismiss,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            containerAlpha = containerAlpha,
            hazeState = sheetHazeState,
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