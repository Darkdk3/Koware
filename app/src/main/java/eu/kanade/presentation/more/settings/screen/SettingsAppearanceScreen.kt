package eu.kanade.presentation.more.settings.screen

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.materialkolor.PaletteStyle
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.domain.ui.model.setAppCompatDelegateThemeMode
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.appearance.AppLanguageScreen
import eu.kanade.presentation.more.settings.widget.AppThemeModePreferenceWidget
import eu.kanade.presentation.more.settings.widget.AppThemePreferenceWidget
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.novel.TDMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt
import kotlin.time.Clock

object SettingsAppearanceScreen : SearchableSettings {

    override val supportsReset: Boolean get() = true

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_appearance

    @Composable
    override fun getPreferences(): List<Preference> {
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val libraryPreferences = remember { Injekt.get<tachiyomi.domain.library.service.LibraryPreferences>() }
        return listOf(
            getThemeGroup(uiPreferences = uiPreferences),
            getDisplayGroup(uiPreferences = uiPreferences),
            getLibraryLayoutGroup(libraryPreferences = libraryPreferences),
            getNavBarAppearanceGroup(libraryPreferences = libraryPreferences),
            getSheetAppearanceGroup(libraryPreferences = libraryPreferences),
            getMangaDetailsGroup(libraryPreferences = libraryPreferences, uiPreferences = uiPreferences),
        )
    }

    @Composable
    private fun getMangaDetailsGroup(
        libraryPreferences: tachiyomi.domain.library.service.LibraryPreferences,
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        val centerCover by libraryPreferences.mangaDetailsCenterCover.collectAsState()
        val centerCoverSizePercent by libraryPreferences.mangaDetailsCenterCoverSizePercent.collectAsState()
        val coverTheme by libraryPreferences.mangaDetailsCoverTheme.collectAsState()
        val hideBackdrop by libraryPreferences.mangaDetailsHideBackdrop.collectAsState()
        val backdropBlurDp by libraryPreferences.mangaDetailsBackdropBlurDp.collectAsState()
        val backdropOpacityPercent by libraryPreferences.mangaDetailsBackdropOpacityPercent.collectAsState()
        val backdropBrightnessPercent by libraryPreferences.mangaDetailsBackdropBrightnessPercent.collectAsState()

        return Preference.PreferenceGroup(
            title = "Manga details screen",
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.mangaDetailsHideBackdrop,
                    title = "Hide backdrop image",
                    subtitle = "Remove the blurred cover image behind the title area",
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = "Backdrop blur",
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            text = "Backdrop blur: ${backdropBlurDp}dp",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (!hideBackdrop) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        )
                        androidx.compose.material3.Slider(
                            value = backdropBlurDp.toFloat(),
                            valueRange = 0f..20f,
                            steps = 19,
                            enabled = !hideBackdrop,
                            onValueChange = {
                                libraryPreferences.mangaDetailsBackdropBlurDp.set(it.roundToInt())
                            },
                        )
                    }
                },
                Preference.PreferenceItem.CustomPreference(
                    title = "Backdrop opacity",
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            text = "Backdrop opacity: $backdropOpacityPercent%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (!hideBackdrop) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        )
                        androidx.compose.material3.Slider(
                            value = backdropOpacityPercent.toFloat(),
                            valueRange = 0f..100f,
                            steps = 19,
                            enabled = !hideBackdrop,
                            onValueChange = {
                                libraryPreferences.mangaDetailsBackdropOpacityPercent.set(it.roundToInt())
                            },
                        )
                    }
                },
                Preference.PreferenceItem.CustomPreference(
                    title = "Backdrop brightness",
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            text = "Backdrop brightness: $backdropBrightnessPercent%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (!hideBackdrop) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        )
                        androidx.compose.material3.Slider(
                            value = backdropBrightnessPercent.toFloat(),
                            valueRange = 50f..150f,
                            steps = 19,
                            enabled = !hideBackdrop,
                            onValueChange = {
                                libraryPreferences.mangaDetailsBackdropBrightnessPercent.set(it.roundToInt())
                            },
                        )
                    }
                },
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.mangaDetailsCenterCover,
                    title = "Center cover",
                    subtitle = "Show a large centered cover above the title instead of beside it",
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = "Center cover size",
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            text = "Center cover size: $centerCoverSizePercent%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (centerCover) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        )
                        androidx.compose.material3.Slider(
                            value = centerCoverSizePercent.toFloat(),
                            valueRange = 40f..90f,
                            steps = 9,
                            enabled = centerCover,
                            onValueChange = {
                                libraryPreferences.mangaDetailsCenterCoverSizePercent.set(it.roundToInt())
                            },
                        )
                    }
                },
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.mangaDetailsFreeformCover,
                    title = "Uncropped cover",
                    subtitle = "Show the manga's real cover shape on this screen instead of cropping it to a fixed size",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.mangaDetailsCoverTheme,
                    title = "Theme from cover",
                    subtitle = "Recolor this screen's whole theme using a dominant color from the manga's cover",
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = uiPreferences.themeCoverBasedStyle,
                    entries = PaletteStyle.entries.associateWith { it.name },
                    title = "Cover theme style",
                    subtitle = "How the cover's color is turned into a full theme",
                    enabled = coverTheme,
                ),
            ),
        )
    }

    /**
     * Bottom-sheet / dialog (AdaptiveSheet) background style and opacity. Reuses
     * LibraryPreferences.NavBarBackgroundStyle since sheets support the same
     * Solid/Transparent/Frosted styles as the floating nav bar.
     */
    @Composable
    private fun getSheetAppearanceGroup(
        libraryPreferences: tachiyomi.domain.library.service.LibraryPreferences,
    ): Preference.PreferenceGroup {
        val backgroundStyle by libraryPreferences.sheetBackgroundStyle.collectAsState()
        val opacityPercent by libraryPreferences.sheetOpacityPercent.collectAsState()
        val opacityEnabled = backgroundStyle != LibraryPreferences.NavBarBackgroundStyle.Solid

        return Preference.PreferenceGroup(
            title = "Sheet appearance",
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = libraryPreferences.sheetBackgroundStyle,
                    entries = LibraryPreferences.NavBarBackgroundStyle.entries.associateWith { it.name },
                    title = "Sheet background style",
                    subtitle = "Solid, transparent, or frosted (blurred) background for sheets and dialogs",
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = "Sheet opacity",
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            text = "Sheet opacity: $opacityPercent%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (opacityEnabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        )
                        androidx.compose.material3.Slider(
                            value = opacityPercent.toFloat(),
                            valueRange = 0f..100f,
                            steps = 19,
                            enabled = opacityEnabled,
                            onValueChange = {
                                libraryPreferences.sheetOpacityPercent.set(it.roundToInt())
                            },
                        )
                    }
                },
            ),
        )
    }

    /**
     * Floating bottom navigation bar customization - size, spacing, shape, and background style.
     */
    @Composable
    private fun getNavBarAppearanceGroup(
        libraryPreferences: tachiyomi.domain.library.service.LibraryPreferences,
    ): Preference.PreferenceGroup {
        val widthPercent by libraryPreferences.navBarWidthPercent.collectAsState()
        val heightDp by libraryPreferences.navBarHeightDp.collectAsState()
        val itemSpacingDp by libraryPreferences.navBarItemSpacingDp.collectAsState()
        val cornerRadiusDp by libraryPreferences.navBarCornerRadiusDp.collectAsState()
        val backgroundStyle by libraryPreferences.navBarBackgroundStyle.collectAsState()
        val opacityPercent by libraryPreferences.navBarOpacityPercent.collectAsState()
        val opacityEnabled = backgroundStyle != LibraryPreferences.NavBarBackgroundStyle.Solid
        val frostEnabled = backgroundStyle == LibraryPreferences.NavBarBackgroundStyle.Frosted

        return Preference.PreferenceGroup(
            title = "Navigation bar appearance",
            preferenceItems = listOf(
                Preference.PreferenceItem.CustomPreference(
                    title = "Nav bar width",
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            text = "Nav bar width: $widthPercent%",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        androidx.compose.material3.Slider(
                            value = widthPercent.toFloat(),
                            valueRange = 50f..100f,
                            steps = 9,
                            onValueChange = {
                                libraryPreferences.navBarWidthPercent.set(it.roundToInt())
                            },
                        )
                    }
                },
                Preference.PreferenceItem.CustomPreference(
                    title = "Nav bar height",
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            text = "Nav bar height: ${heightDp}dp",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        androidx.compose.material3.Slider(
                            value = heightDp.toFloat(),
                            valueRange = 56f..120f,
                            steps = 15,
                            onValueChange = {
                                libraryPreferences.navBarHeightDp.set(it.roundToInt())
                            },
                        )
                    }
                },
                Preference.PreferenceItem.CustomPreference(
                    title = "Nav bar item spacing",
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            text = "Item spacing: ${itemSpacingDp}dp",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        androidx.compose.material3.Slider(
                            value = itemSpacingDp.toFloat(),
                            valueRange = 0f..24f,
                            steps = 23,
                            onValueChange = {
                                libraryPreferences.navBarItemSpacingDp.set(it.roundToInt())
                            },
                        )
                    }
                },
                Preference.PreferenceItem.CustomPreference(
                    title = "Nav bar corner radius",
                ) {
                    val cornerRadiusDp by libraryPreferences.navBarCornerRadiusDp.collectAsState()
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            text = "Corner radius: ${cornerRadiusDp}dp",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "0 = pill shape, higher values = rounded corners",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        androidx.compose.material3.Slider(
                            value = cornerRadiusDp.toFloat(),
                            valueRange = 0f..56f,
                            steps = 11,
                            onValueChange = {
                                libraryPreferences.navBarCornerRadiusDp.set(it.roundToInt())
                            },
                        )
                    }
                },
                Preference.PreferenceItem.ListPreference(
                    preference = libraryPreferences.navBarBackgroundStyle,
                    entries = LibraryPreferences.NavBarBackgroundStyle.entries.associateWith { it.name },
                    title = "Nav bar background style",
                    subtitle = "Solid, transparent, or frosted (blurred) background",
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = "Nav bar opacity",
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            text = "Nav bar opacity: $opacityPercent%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (opacityEnabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        )
                        androidx.compose.material3.Slider(
                            value = opacityPercent.toFloat(),
                            valueRange = 0f..100f,
                            steps = 19,
                            enabled = opacityEnabled,
                            onValueChange = {
                                libraryPreferences.navBarOpacityPercent.set(it.roundToInt())
                            },
                        )
                    }
                },
            ),
        )
    }

    @Composable
    private fun getThemeGroup(
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val themeModePref = uiPreferences.themeMode
        val themeMode by themeModePref.collectAsState()
        val appThemePref = uiPreferences.appTheme
        val appTheme by appThemePref.collectAsState()
        val amoledPref = uiPreferences.themeDarkAmoled
        val amoled by amoledPref.collectAsState()
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_theme),
            preferenceItems = listOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MR.strings.pref_app_theme),
                ) {
                    Column {
                        AppThemeModePreferenceWidget(
                            value = themeMode,
                            onItemClick = {
                                themeModePref.set(it)
                                setAppCompatDelegateThemeMode(it)
                            },
                        )
                        AppThemePreferenceWidget(
                            value = appTheme,
                            amoled = amoled,
                            onItemClick = { appThemePref.set(it) },
                        )
                    }
                },
                Preference.PreferenceItem.SwitchPreference(
                    preference = amoledPref,
                    title = stringResource(MR.strings.pref_dark_theme_pure_black),
                    enabled = themeMode != ThemeMode.LIGHT,
                    onValueChanged = {
                        (context as? Activity)?.let { ActivityCompat.recreate(it) }
                        true
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getDisplayGroup(
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val now = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime() }
        val dateFormat by uiPreferences.dateFormat.collectAsState()
        val formattedNow = remember(dateFormat) {
            UiPreferences.dateFormat(dateFormat).format(now)
        }
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_display),
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_app_language),
                    onClick = { navigator.push(AppLanguageScreen()) },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = uiPreferences.tabletUiMode,
                    entries = TabletUiMode.entries
                        .associateWith { stringResource(it.titleRes) },
                    title = stringResource(MR.strings.pref_tablet_ui_mode),
                    onValueChanged = {
                        context.toast(MR.strings.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = uiPreferences.dateFormat,
                    entries = DateFormats
                        .associateWith {
                            val formattedDate = UiPreferences.dateFormat(it).format(now)
                            "${it.ifEmpty { stringResource(MR.strings.label_default) }} ($formattedDate)"
                        },
                    title = stringResource(MR.strings.pref_date_format),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.relativeTime,
                    title = stringResource(MR.strings.pref_relative_format),
                    subtitle = stringResource(
                        MR.strings.pref_relative_format_summary,
                        stringResource(MR.strings.relative_time_today),
                        formattedNow,
                    ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.imagesInDescription,
                    title = stringResource(MR.strings.pref_display_images_description),
                ),
            ),
        )
    }

    @Composable
    private fun getLibraryLayoutGroup(
        libraryPreferences: tachiyomi.domain.library.service.LibraryPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val basePreferences = remember { Injekt.get<eu.kanade.domain.base.BasePreferences>() }
        val freeformCoverGrid by libraryPreferences.freeformCoverGrid.collectAsState()
        return Preference.PreferenceGroup(
            title = "Library layout",
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.joinedLibrary,
                    title = "Combined library",
                    subtitle = "Merge Novels and Manga into a single Library tab",
                    onValueChanged = {
                        context.toast(MR.strings.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = "Content UI",
                    content = {
                        UiModeSelector(basePreferences = basePreferences)
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.alwaysShowNavigationLabels,
                    title = "Always show navigation labels",
                    subtitle = "When off, bottom bar labels only show under the selected tab",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.showAuthorArtistSubtitle,
                    title = "Show author/artist under title",
                    subtitle = "In library grid view, shows the author (or author + artist) below the title when it fits",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.freeformCoverGrid,
                    title = "Freeform cover grid",
                    subtitle = "Show original cover aspect ratios in grid view instead of cropping to a fixed 2:3 (book) shape",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.freeformCoverGridStaggered,
                    title = "Staggered layout for freeform covers",
                    subtitle = "Only applies when freeform cover grid is enabled. Arranges grid items in a masonry-style staggered layout to pack covers tightly",
                    enabled = freeformCoverGrid,
                ),
            ),
        )
    }
}

@Composable
private fun UiModeSelector(basePreferences: BasePreferences) {
    val uiMode by basePreferences.uiMode.collectAsState()
    val context = LocalContext.current

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "Show content for",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UiModeChip(
                label = "Manga only",
                value = BasePreferences.UiMode.MANGA_ONLY,
                selected = uiMode,
                onSelect = {
                    basePreferences.uiMode.set(BasePreferences.UiMode.MANGA_ONLY)
                    basePreferences.hideMangaUi.set(false)
                    restartApp(context)
                },
            )
            UiModeChip(
                label = "Novel only",
                value = BasePreferences.UiMode.NOVEL_ONLY,
                selected = uiMode,
                onSelect = {
                    basePreferences.uiMode.set(BasePreferences.UiMode.NOVEL_ONLY)
                    basePreferences.hideMangaUi.set(true)
                    restartApp(context)
                },
            )
            UiModeChip(
                label = "Both",
                value = BasePreferences.UiMode.BOTH,
                selected = uiMode,
                onSelect = {
                    basePreferences.uiMode.set(BasePreferences.UiMode.BOTH)
                    basePreferences.hideMangaUi.set(false)
                    restartApp(context)
                },
            )
        }
    }
}

@Composable
private fun UiModeChip(
    label: String,
    value: BasePreferences.UiMode,
    selected: BasePreferences.UiMode,
    onSelect: () -> Unit,
) {
    FilterChip(
        selected = selected == value,
        onClick = onSelect,
        label = { Text(label) },
    )
}

private fun restartApp(context: android.content.Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}

private val DateFormats = listOf(
    "", // Default
    "MM/dd/yy",
    "dd/MM/yy",
    "yyyy-MM-dd",
    "dd MMM yyyy",
    "MMM dd, yyyy",
)
