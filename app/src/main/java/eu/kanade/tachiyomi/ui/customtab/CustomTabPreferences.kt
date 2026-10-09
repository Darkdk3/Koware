package eu.kanade.tachiyomi.ui.customtab

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class CustomTabPreferences(
    preferenceStore: PreferenceStore,
) {

    // Off by default so the nav bar stays unchanged until the user opts in.
    val enabled: Preference<Boolean> =
        preferenceStore.getBoolean("custom_tab_enabled", false)

    // Label shown in the nav bar and used as the tab title.
    val name: Preference<String> =
        preferenceStore.getString("custom_tab_name", "Custom")

    // When on, the tab can only load sites from the allowed list.
    val safeMode: Preference<Boolean> =
        preferenceStore.getBoolean("custom_tab_safe_mode", true)

    val allowedHosts: Preference<Set<String>> =
        preferenceStore.getStringSet(
            "custom_tab_allowed_hosts",
            CustomTabSafety.DEFAULT_HOSTS,
        )
}
