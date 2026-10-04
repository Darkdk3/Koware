package eu.kanade.domain.ui

import com.materialkolor.PaletteStyle
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.MangaDetailsStyle
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.domain.ui.model.UiStyle
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class UiPreferences(
    preferenceStore: PreferenceStore,
) {

    val themeMode: Preference<ThemeMode> = preferenceStore.getEnum("pref_theme_mode_key", ThemeMode.SYSTEM)

    val appTheme: Preference<AppTheme> = preferenceStore.getEnum(
        "pref_app_theme",
        if (DeviceUtil.isDynamicColorAvailable) {
            AppTheme.MONET
        } else {
            AppTheme.DEFAULT
        },
    )

    val themeDarkAmoled: Preference<Boolean> = preferenceStore.getBoolean("pref_theme_dark_amoled_key", false)

    /** Palette style used when recoloring the manga details screen from the cover. */
    val themeCoverBasedStyle: Preference<PaletteStyle> =
        preferenceStore.getEnum("pref_theme_cover_based_style", PaletteStyle.TonalSpot)

    /** App-wide look: Legacy (original) or Modern (card-based). */
    val uiStyle: Preference<UiStyle> = preferenceStore.getEnum("pref_ui_style", UiStyle.LEGACY)

    /** Manga details override; only applies while [uiStyle] is Modern. */
    val mangaDetailsStyle: Preference<MangaDetailsStyle> =
        preferenceStore.getEnum("pref_manga_details_style", MangaDetailsStyle.MODERN)

    val relativeTime: Preference<Boolean> = preferenceStore.getBoolean("relative_time_v2", true)

    val dateFormat: Preference<String> = preferenceStore.getString("app_date_format", "")

    val tabletUiMode: Preference<TabletUiMode> = preferenceStore.getEnum("tablet_ui_mode", TabletUiMode.AUTOMATIC)

    val imagesInDescription: Preference<Boolean> = preferenceStore.getBoolean("pref_render_images_description", true)

    val showNavigationLabels: Preference<Boolean> = preferenceStore.getBoolean("pref_show_navigation_labels", true)

    companion object {
        fun dateFormat(format: String): DateTimeFormatter = when (format) {
            "" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
            else -> DateTimeFormatter.ofPattern(format, Locale.getDefault())
        }
    }
}
