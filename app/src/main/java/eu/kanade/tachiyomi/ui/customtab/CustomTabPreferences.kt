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
}
