// FILE: app/src/main/java/eu/kanade/tachiyomi/ui/browse/discover/DiscoverViewModel.kt

package eu.kanade.tachiyomi.ui.browse.discover

import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.jsplugin.JsPluginManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.isNovelSource
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.update
import mihon.core.viewmodel.StateViewModel
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
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasPinnedNovelSources: Boolean = true, // drives the "nothing pinned yet" empty state
)

class DiscoverViewModel(
    private val sourceManager: SourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val jsPluginManager: JsPluginManager = Injekt.get(), // already registered as a
                                                                   // singleton elsewhere in the
                                                                   // app (its JS Plugins tab uses
                                                                   // it), so Injekt.get() should
                                                                   // resolve without extra wiring
) : StateViewModel<DiscoverScreenState>(DiscoverScreenState()) {

    init {
        loadDiscoverFeed()
    }

    fun loadDiscoverFeed() {
        viewModelScope.launchIO {
            mutableState.update { it.copy(isLoading = true) }

            val pinnedKeys = sourcePreferences.pinnedSources().get()

            // Regular (Kotlin/APK) extensions plus LNReader-compatible JS plugin sources,
            // merged into one pool before filtering — JsSource already implements
            // CatalogueSource, so it slots in alongside everything else with no special-casing.
            val allNovelSources = (sourceManager.getOnlineSources() + jsPluginManager.jsSources.value)
                .filterIsInstance<CatalogueSource>()
                .distinctBy { it.id }
                .filter { it.isNovelSource() }
                .filter { it.supportsLatest }

            val pinnedNovelSources = allNovelSources.filter { it.id.toString() in pinnedKeys }

            // Each source's "latest" page is already sorted by recency internally, but
            // SManga carries no timestamp field, so a true cross-source chronological
            // merge isn't possible. Round-robin interleaving keeps one source from
            // dominating the top of the feed.
            val perSourceLists = pinnedNovelSources.map { source ->
                runCatching { source.getLatestUpdates(page = 1).mangas }
                    .getOrDefault(emptyList())
                    .map { manga -> DiscoverEntry(source = source, manga = manga) }
            }
            val merged = interleave(perSourceLists)

            mutableState.update {
                it.copy(
                    items = merged,
                    isLoading = false,
                    hasPinnedNovelSources = pinnedNovelSources.isNotEmpty(),
                )
            }
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
