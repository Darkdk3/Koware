// FILE: app/src/main/java/eu/kanade/tachiyomi/ui/browse/discover/DiscoverViewModel.kt
// (new file, new package "discover" alongside "extension", "migration", "source")

package eu.kanade.tachiyomi.ui.browse.discover

import androidx.lifecycle.viewModelScope
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
)

class DiscoverViewModel(
    private val sourceManager: SourceManager = Injekt.get(),
) : StateViewModel<DiscoverScreenState>(DiscoverScreenState()) {

    init {
        loadDiscoverFeed()
    }

    fun loadDiscoverFeed() {
        viewModelScope.launchIO {
            mutableState.update { it.copy(isLoading = true) }

            // getOnlineSources() already excludes LocalSource/LocalNovelSource, which is
            // what we want here - discovery should surface external sources, not the
            // user's own on-device files.
            val novelSources = sourceManager.getOnlineSources()
                .filterIsInstance<CatalogueSource>()
                .filter { it.isNovelSource() }
                .filter { it.supportsLatest } // getLatestUpdates() throws otherwise

            // Each source's "latest" page is already sorted by recency internally, but
            // SManga carries no timestamp field, so a true cross-source chronological
            // merge isn't possible. Round-robin interleaving keeps one source from
            // dominating the top of the feed.
            val perSourceLists = novelSources.map { source ->
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
