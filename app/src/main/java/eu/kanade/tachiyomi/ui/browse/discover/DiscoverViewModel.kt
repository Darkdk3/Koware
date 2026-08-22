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
import mihon.domain.manga.model.toDomainManga // confirmed real
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.manga.interactor.NetworkToLocalManga // confirmed real
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
    val hasPinnedNovelSources: Boolean = true,
    val pendingMangaId: Long? = null,
)

class DiscoverViewModel(
    private val sourceManager: SourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val jsPluginManager: JsPluginManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
) : StateViewModel<DiscoverScreenState>(DiscoverScreenState()) {

    init {
        loadDiscoverFeed()
    }

    fun loadDiscoverFeed() {
        viewModelScope.launchIO {
            mutableState.update { it.copy(isLoading = true) }

            val pinnedKeys = sourcePreferences.pinnedSources.get()

            val allNovelSources = (sourceManager.getOnlineSources() + jsPluginManager.jsSources.value)
                .filterIsInstance<CatalogueSource>()
                .distinctBy { it.id }
                .filter { it.isNovelSource() }
                .filter { it.supportsLatest }

            val pinnedNovelSources = allNovelSources.filter { it.id.toString() in pinnedKeys }

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

    /**
     * Tapped a card. The entry's SManga only exists as a remote listing right now - it has
     * no local database row, so MangaScreen can't be opened with it directly. This converts
     * it to a domain Manga (isNovel = true, since Discover is novels-only), inserts-or-fetches
     * it via NetworkToLocalManga to get a real local id, then exposes that id via state for
     * the UI to navigate with.
     */
    fun openEntry(entry: DiscoverEntry) {
        viewModelScope.launchIO {
            val domainManga = entry.manga.toDomainManga(
                sourceId = entry.source.id,
                isNovel = true,
            )
            val localManga = networkToLocalManga(domainManga)
            mutableState.update { it.copy(pendingMangaId = localManga.id) }
        }
    }

    fun consumePendingNavigation() {
        mutableState.update { it.copy(pendingMangaId = null) }
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
