package eu.kanade.presentation.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.DynamicMaterialExpressiveTheme
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.theme.colorscheme.BaseColorScheme
import eu.kanade.presentation.theme.colorscheme.CatppuccinColorScheme
import eu.kanade.presentation.theme.colorscheme.GreenAppleColorScheme
import eu.kanade.presentation.theme.colorscheme.LavenderColorScheme
import eu.kanade.presentation.theme.colorscheme.MidnightDuskColorScheme
import eu.kanade.presentation.theme.colorscheme.MonetColorScheme
import eu.kanade.presentation.theme.colorscheme.MonochromeColorScheme
import eu.kanade.presentation.theme.colorscheme.NordColorScheme
import eu.kanade.presentation.theme.colorscheme.StrawberryColorScheme
import eu.kanade.presentation.theme.colorscheme.TachiyomiColorScheme
import eu.kanade.presentation.theme.colorscheme.TakoColorScheme
import eu.kanade.presentation.theme.colorscheme.TealTurqoiseColorScheme
import eu.kanade.presentation.theme.colorscheme.TidalWaveColorScheme
import eu.kanade.presentation.theme.colorscheme.TokyoNightColorScheme
import eu.kanade.presentation.theme.colorscheme.YinYangColorScheme
import eu.kanade.presentation.theme.colorscheme.YotsubaColorScheme
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun TachiyomiTheme(
    appTheme: AppTheme? = null,
    amoled: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val uiPreferences = Injekt.get<UiPreferences>()

    // Only the app-wide theme (no explicit theme passed in) follows the user's custom theme.
    // Callers that pass an explicit theme, like the theme picker previews, always show that theme.
    val customEnabled by uiPreferences.customThemeEnabled.collectAsState()
    val customBase by uiPreferences.customThemeBase.collectAsState()
    val customLight by uiPreferences.customThemeLight.collectAsState()
    val customDark by uiPreferences.customThemeDark.collectAsState()
    val custom = remember(appTheme, customEnabled, customBase, customLight, customDark) {
        if (appTheme == null) {
            CustomTheme.spec(customEnabled, customBase, customLight, customDark)
        } else {
            null
        }
    }

    BaseTachiyomiTheme(
        appTheme = appTheme ?: uiPreferences.appTheme.get(),
        isAmoled = amoled ?: uiPreferences.themeDarkAmoled.get(),
        custom = custom,
        content = content,
    )
}

/** Theme seeded from a manga's cover color, with a selectable Material Kolor palette style. */
@Composable
fun TachiyomiTheme(
    seedColor: Color?,
    appTheme: AppTheme? = null,
    amoled: Boolean? = null,
    typography: Typography = MaterialTheme.typography,
    content: @Composable () -> Unit,
) {
    if (seedColor == null) {
        TachiyomiTheme(appTheme, amoled, content)
    } else {
        val uiPreferences = Injekt.get<UiPreferences>()
        val isAmoled = amoled ?: uiPreferences.themeDarkAmoled.get()
        DynamicMaterialExpressiveTheme(
            seedColor = seedColor,
            isAmoled = isAmoled,
            style = uiPreferences.themeCoverBasedStyle.get(),
            typography = typography,
            animate = true,
            content = content,
        )
    }
}

@Composable
fun TachiyomiPreviewTheme(
    appTheme: AppTheme = AppTheme.DEFAULT,
    isAmoled: Boolean = false,
    content: @Composable () -> Unit,
) = BaseTachiyomiTheme(
    appTheme = appTheme,
    isAmoled = isAmoled,
    custom = null,
    content = content,
)

@Composable
private fun BaseTachiyomiTheme(
    appTheme: AppTheme,
    isAmoled: Boolean,
    custom: CustomThemeSpec?,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    MaterialExpressiveTheme(
        colorScheme = remember(appTheme, isDark, isAmoled, custom) {
            getThemeColorScheme(
                context = context,
                appTheme = appTheme,
                isDark = isDark,
                isAmoled = isAmoled,
                custom = custom,
            )
        },
        content = content,
    )
}

private fun getThemeColorScheme(
    context: Context,
    appTheme: AppTheme,
    isDark: Boolean,
    isAmoled: Boolean,
    custom: CustomThemeSpec?,
): ColorScheme {
    // A custom theme starts from the built-in theme it was created from.
    val effectiveTheme = custom?.base ?: appTheme

    val colorScheme = when (effectiveTheme) {
        AppTheme.MONET -> MonetColorScheme(context)
        else -> colorSchemes.getOrDefault(effectiveTheme, TachiyomiColorScheme)
    }
    val base = colorScheme.getColorScheme(
        isDark = isDark,
        isAmoled = isAmoled,
        overrideDarkSurfaceContainers = effectiveTheme != AppTheme.MONET,
    )

    return if (custom == null) {
        base
    } else {
        CustomTheme.apply(
            base = base,
            overrides = if (isDark) custom.dark else custom.light,
            isDark = isDark,
        )
    }
}

private val colorSchemes: Map<AppTheme, BaseColorScheme> = mapOf(
    AppTheme.DEFAULT to TachiyomiColorScheme,
    AppTheme.CATPPUCCIN to CatppuccinColorScheme,
    AppTheme.TOKYONIGHT to TokyoNightColorScheme,
    AppTheme.GREEN_APPLE to GreenAppleColorScheme,
    AppTheme.LAVENDER to LavenderColorScheme,
    AppTheme.MIDNIGHT_DUSK to MidnightDuskColorScheme,
    AppTheme.MONOCHROME to MonochromeColorScheme,
    AppTheme.NORD to NordColorScheme,
    AppTheme.STRAWBERRY_DAIQUIRI to StrawberryColorScheme,
    AppTheme.TAKO to TakoColorScheme,
    AppTheme.TEALTURQUOISE to TealTurqoiseColorScheme,
    AppTheme.TIDAL_WAVE to TidalWaveColorScheme,
    AppTheme.YINYANG to YinYangColorScheme,
    AppTheme.YOTSUBA to YotsubaColorScheme,
)
