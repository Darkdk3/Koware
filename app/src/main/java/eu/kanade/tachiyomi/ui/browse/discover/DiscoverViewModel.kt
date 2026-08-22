// FILE: app/src/main/java/eu/kanade/tachiyomi/ui/browse/discover/DiscoverViewModel.kt

package eu.kanade.tachiyomi.ui.browse.discover

import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.isNovelSource
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.update
import mihon.core.viewmodel.StateViewModel
import tachiyomi.core.common.preference.PreferenceStore // TODO: confirm this exact package —
                                                           // this is the standard Tachiyomi/Mihon
                                                           // location, but if it fails to resolve,
                                                           // search your repo for an existing
                                                           // `PreferenceStore` usage (e.g. in
                                                           // NovelExtensionsViewModel or similar)
                                                           // and match its import instead.
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class DiscoverEntry(
    val source: CatalogueSource,
    val manga: SManga,
)

data class DiscoverScreenState(
    val items: List<DiscoverEntry> = emptyList(),
    val availableNovelSources: List<CatalogueSource> = emptyList(),
    val enabledSourceKeys: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
)

class DiscoverViewModel(
    private val sourceManager: SourceManager = Injekt.get(),
    preferenceStore: PreferenceStore = Injekt.get(),
) : StateViewModel<DiscoverScreenState>(DiscoverScreenState()) {

    // Persists which source IDs (as strings) are included in the Discover feed.
    // Empty set on first run = "not yet configured", handled specially below
    // so a fresh install shows everything rather than nothing.
    private val enabledSourcesPref = preferenceStore.getStringSet(
        "discover_enabled_sources",
        emptySet(),
    )

    init {
        loadAvailableSources()
        loadDiscoverFeed()
    }

    private fun loadAvailableSources() {
        val novelSources = sourceManager.getOnlineSources()
            .filterIsInstance<CatalogueSource>()
            .filter { it.isNovelSource() }
            .filter { it.supportsLatest }

        val storedSelection = enabledSourcesPref.get()
        // First run: nothing configured yet, default to "all enabled" so the
        // feed isn't empty out of the box.
        val effectiveSelection = storedSelection.ifEmpty {
            novelSources.map { it.id.toString() }.toSet()
        }

        mutableState.update {
            it.copy(
                availableNovelSources = novelSources,
                enabledSourceKeys = effectiveSelection,
            )
        }
    }

    fun toggleSource(source: CatalogueSource) {
        val key = source.id.toString()
        val current = state.value.enabledSourceKeys
        val updated = if (key in current) current - key else current + key

        enabledSourcesPref.set(updated)
        mutableState.update { it.copy(enabledSourceKeys = updated) }
        loadDiscoverFeed()
    }

    fun loadDiscoverFeed() {
        viewModelScope.launchIO {
            mutableState.update { it.copy(isLoading = true) }

            val enabledKeys = state.value.enabledSourceKeys
            val sourcesToQuery = state.value.availableNovelSources
                .filter { it.id.toString() in enabledKeys }

            // Each source's "latest" page is already sorted by recency internally, but
            // SManga carries no timestamp field, so a true cross-source chronological
            // merge isn't possible. Round-robin interleaving keeps one source from
            // dominating the top of the feed.
            val perSourceLists = sourcesToQuery.map { source ->
                runCatching { source.getLatestUpdates(page = 1).mangas }
                    .getOrDefault(emptyList())
                    .map { manga -> DiscoverEntry(source = source, manga = manga) }
            }
            val merged = interleave(perSourceLists)

            mutableState.update { it.copy(items = merged, isLoading = false) }
        }
    }

    fun refresh() {
        mutableState.update { it.copy(isRefreshing = true) }
        loadDiscoverFeed()
        mutableState.update { it.copy(isRefreshing = false) }
    }

    private fun <T> interleave(lists: List<List<T>>): List<T> {
        val result = mutableListOf<T>()
        var i = 0
        while (lists.any { it.size > i }) {
            lists.forEach { list -> list.getOrNull(i)?.let(result::add) }
            i++
        }
        return result
    }
}
