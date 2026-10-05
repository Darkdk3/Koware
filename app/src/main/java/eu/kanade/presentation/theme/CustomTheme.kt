package eu.kanade.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** The colors a user can edit. Everything else in the scheme is derived from these. */
enum class ThemeRole(val label: String, val description: String) {
    PRIMARY("Primary", "Buttons, active tab, links"),
    SECONDARY("Secondary", "Chips, filters, toggles"),
    TERTIARY("Tertiary", "Accents, badges, highlights"),
    BACKGROUND("Background", "The screen behind everything"),
    SURFACE("Surface", "Cards, sheets, navigation bar"),
    ERROR("Error", "Failed items and warnings"),
}

/** What TachiyomiTheme needs to rebuild the custom scheme: a base theme plus edits per mode. */
data class CustomThemeSpec(
    val base: AppTheme,
    val light: Map<ThemeRole, Color>,
    val dark: Map<ThemeRole, Color>,
)

/** A custom theme the user saved. [name] may be blank; use [CustomTheme.displayName] to show it. */
data class SavedCustomTheme(
    val id: String,
    val name: String,
    val base: String,
    val light: String,
    val dark: String,
)

object CustomTheme {

    fun onColorFor(color: Color): Color {
        return if (color.luminance() > 0.5f) Color(0xFF111418) else Color.White
    }

    /** The color of [role] in [scheme], i.e. what the user currently sees. */
    fun currentColor(scheme: ColorScheme, role: ThemeRole): Color = when (role) {
        ThemeRole.PRIMARY -> scheme.primary
        ThemeRole.SECONDARY -> scheme.secondary
        ThemeRole.TERTIARY -> scheme.tertiary
        ThemeRole.BACKGROUND -> scheme.background
        ThemeRole.SURFACE -> scheme.surfaceContainer
        ThemeRole.ERROR -> scheme.error
    }

    fun spec(enabled: Boolean, baseName: String, light: String, dark: String): CustomThemeSpec? {
        if (!enabled) return null
        val base = runCatching { AppTheme.valueOf(baseName) }.getOrDefault(AppTheme.DEFAULT)
        return CustomThemeSpec(base = base, light = parse(light), dark = parse(dark))
    }

    fun parse(raw: String): Map<ThemeRole, Color> {
        if (raw.isBlank()) return emptyMap()
        val result = mutableMapOf<ThemeRole, Color>()
        raw.split(';').forEach { entry ->
            val parts = entry.split(':')
            if (parts.size != 2) return@forEach
            val role = ThemeRole.entries.firstOrNull { it.name == parts[0] } ?: return@forEach
            val argb = parts[1].toLongOrNull(16) ?: return@forEach
            result[role] = Color(argb.toInt())
        }
        return result
    }

    fun serialize(overrides: Map<ThemeRole, Color>): String {
        return overrides.entries.joinToString(";") { (role, color) ->
            "${role.name}:${"%08X".format(color.toArgb())}"
        }
    }

    /** Saves one edited color. The first edit turns the current theme into the custom theme. */
    fun setOverride(prefs: UiPreferences, isDark: Boolean, role: ThemeRole, color: Color) {
        if (!prefs.customThemeEnabled.get()) {
            prefs.customThemeBase.set(prefs.appTheme.get().name)
            prefs.customThemeLight.set("")
            prefs.customThemeDark.set("")
            prefs.customThemeEnabled.set(true)
        }
        val pref = if (isDark) prefs.customThemeDark else prefs.customThemeLight
        val updated = parse(pref.get()).toMutableMap()
        updated[role] = color
        pref.set(serialize(updated))
    }

    /** Goes back to the built-in theme the custom theme was based on. */
    fun reset(prefs: UiPreferences) {
        prefs.customThemeEnabled.set(false)
        prefs.customThemeLight.set("")
        prefs.customThemeDark.set("")
    }

    /** Applies the user's edits on top of [base]. Derived colors (on-colors, containers) follow. */
    fun apply(base: ColorScheme, overrides: Map<ThemeRole, Color>, isDark: Boolean): ColorScheme {
        if (overrides.isEmpty()) return base
        var s = base

        overrides[ThemeRole.BACKGROUND]?.let { c ->
            val on = onColorFor(c)
            s = s.copy(
                background = c,
                surface = c,
                surfaceDim = c,
                onBackground = on,
                onSurface = on,
                onSurfaceVariant = lerp(on, c, 0.25f),
                outline = lerp(on, c, 0.5f),
                outlineVariant = lerp(c, on, 0.2f),
                inverseSurface = on,
                inverseOnSurface = c,
            )
        }

        overrides[ThemeRole.SURFACE]?.let { c ->
            s = s.copy(
                surfaceContainerLowest = s.background,
                surfaceContainerLow = lerp(s.background, c, 0.5f),
                surfaceContainer = c,
                surfaceContainerHigh = lerp(c, s.onSurface, 0.06f),
                surfaceContainerHighest = lerp(c, s.onSurface, 0.12f),
                surfaceVariant = lerp(c, s.onSurface, 0.1f),
                surfaceBright = lerp(c, s.onSurface, 0.08f),
            )
        }

        val containerMix = if (isDark) 0.35f else 0.25f

        overrides[ThemeRole.PRIMARY]?.let { c ->
            val container = lerp(s.background, c, containerMix)
            s = s.copy(
                primary = c,
                onPrimary = onColorFor(c),
                primaryContainer = container,
                onPrimaryContainer = onColorFor(container),
                surfaceTint = c,
            )
        }
        overrides[ThemeRole.SECONDARY]?.let { c ->
            val container = lerp(s.background, c, containerMix)
            s = s.copy(
                secondary = c,
                onSecondary = onColorFor(c),
                secondaryContainer = container,
                onSecondaryContainer = onColorFor(container),
            )
        }
        overrides[ThemeRole.TERTIARY]?.let { c ->
            val container = lerp(s.background, c, containerMix)
            s = s.copy(
                tertiary = c,
                onTertiary = onColorFor(c),
                tertiaryContainer = container,
                onTertiaryContainer = onColorFor(container),
            )
        }
        overrides[ThemeRole.ERROR]?.let { c ->
            val container = lerp(s.background, c, containerMix)
            s = s.copy(
                error = c,
                onError = onColorFor(c),
                errorContainer = container,
                onErrorContainer = onColorFor(container),
            )
        }

        return s
    }

    // ---- Saved custom themes ----

    fun parseSaved(raw: String): List<SavedCustomTheme> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                SavedCustomTheme(
                    id = o.getString("id"),
                    name = o.optString("name"),
                    base = o.optString("base", AppTheme.DEFAULT.name),
                    light = o.optString("light"),
                    dark = o.optString("dark"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun serializeSaved(themes: List<SavedCustomTheme>): String {
        val array = JSONArray()
        themes.forEach { t ->
            array.put(
                JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    .put("base", t.base)
                    .put("light", t.light)
                    .put("dark", t.dark),
            )
        }
        return array.toString()
    }

    /** The name to show on a card: the user's name, or "Custom N" when they didn't give one. */
    fun displayName(theme: SavedCustomTheme, index: Int): String {
        return theme.name.ifBlank { "Custom ${index + 1}" }
    }

    /**
     * Saves a theme (updates it when [id] matches an existing one, otherwise adds a new one),
     * makes it the active custom theme, and returns its id.
     */
    fun saveTheme(
        prefs: UiPreferences,
        id: String?,
        name: String,
        base: AppTheme,
        light: Map<ThemeRole, Color>,
        dark: Map<ThemeRole, Color>,
    ): String {
        val themes = parseSaved(prefs.savedCustomThemes.get()).toMutableList()
        val themeId = id ?: UUID.randomUUID().toString()
        val updated = SavedCustomTheme(
            id = themeId,
            name = name.trim(),
            base = base.name,
            light = serialize(light),
            dark = serialize(dark),
        )
        val index = themes.indexOfFirst { it.id == themeId }
        if (index >= 0) themes[index] = updated else themes.add(updated)
        prefs.savedCustomThemes.set(serializeSaved(themes))
        activate(prefs, updated)
        return themeId
    }

    /** Makes [theme] the live custom theme and turns the custom theme on. */
    fun activate(prefs: UiPreferences, theme: SavedCustomTheme) {
        prefs.customThemeBase.set(theme.base)
        prefs.customThemeLight.set(theme.light)
        prefs.customThemeDark.set(theme.dark)
        prefs.activeCustomThemeId.set(theme.id)
        prefs.customThemeEnabled.set(true)
    }

    /** Deletes a saved theme. If it was the active one, the custom theme is turned off. */
    fun delete(prefs: UiPreferences, id: String) {
        val remaining = parseSaved(prefs.savedCustomThemes.get()).filterNot { it.id == id }
        prefs.savedCustomThemes.set(serializeSaved(remaining))
        if (prefs.activeCustomThemeId.get() == id) {
            prefs.activeCustomThemeId.set("")
            prefs.customThemeEnabled.set(false)
            prefs.customThemeLight.set("")
            prefs.customThemeDark.set("")
        }
    }
}