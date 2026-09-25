package eu.kanade.tachiyomi.ui.browse.migration.search

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.presentation.browse.MigrateSearchScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.isNovelSource
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchViewModel
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Restore a title from the Notion library: searches the sources for the Notion title, and when you
 * pick the right match it adds it to the library and links it to the same Notion row, which pulls
 * your chapter progress, status and score back.
 */
class RestoreSearchScreen(
    private val pageId: String,
    private val title: String,
    private val author: String,
    private val novel: Boolean,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val viewModel = viewModel<RestoreSearchViewModel>(
            factory = RestoreSearchViewModel.Factory,
            extras = CreationExtras {
                set(RestoreSearchViewModel.QUERY_KEY, title)
                set(RestoreSearchViewModel.NOVEL_KEY, novel)
            },
        )
        val state by viewModel.state.collectAsState()

        // The search only matches titles, so show the author to help pick the right one.
        LaunchedEffect(Unit) {
            if (author.isNotBlank()) context.toast("Restoring \"$title\" by $author")
        }

        MigrateSearchScreen(
            state = state,
            fromSourceId = null,
            navigateUp = navigator::pop,
            onChangeSearchQuery = viewModel::updateSearchQuery,
            onSearch = { viewModel.search() },
            getManga = { viewModel.getManga(it) },
            onChangeSearchFilter = viewModel::setSourceFilter,
            onToggleResults = viewModel::toggleFilterResults,
            onClickSource = {},
            onClickItem = { target ->
                scope.launchIO {
                    NotionRestore.begin(context.applicationContext, target, pageId, title)
                    withUIContext { navigator.replace(MangaScreen(target.id)) }
                }
            },
            onLongClickItem = { navigator.push(MangaScreen(it.id, true)) },
        )
    }
}

class RestoreSearchViewModel(
    initialQuery: String,
    private val novel: Boolean,
) : SearchViewModel(State(searchQuery = initialQuery, sourceFilter = SourceFilter.All)) {

    companion object {
        val QUERY_KEY = CreationExtras.Key<String>()
        val NOVEL_KEY = CreationExtras.Key<Boolean>()

        val Factory = viewModelFactory {
            initializer {
                RestoreSearchViewModel(
                    initialQuery = get(QUERY_KEY)!!,
                    novel = get(NOVEL_KEY)!!,
                )
            }
        }
    }

    init {
        search()
    }

    /** Novel entries search novel sources only; everything else searches the regular sources. */
    override fun getEnabledSources(): List<Source> {
        return super.getEnabledSources().filter { it.isNovelSource() == novel }
    }
}

private object NotionRestore {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Adds the picked title to the library, then links it to its Notion row in the background. */
    suspend fun begin(context: Context, target: Manga, pageId: String, title: String) {
        Injekt.get<UpdateManga>().await(
            MangaUpdate(
                id = target.id,
                favorite = true,
                dateAdded = System.currentTimeMillis(),
            ),
        )
        linkWhenChaptersReady(context, target.id, pageId, title)
    }

    /**
     * Opening the title's screen makes the app fetch its chapters. Once they exist, binding the
     * Notion tracker pulls progress from the row and marks the chapters up to it as read.
     */
    private fun linkWhenChaptersReady(context: Context, mangaId: Long, pageId: String, title: String) {
        scope.launch {
            val getChapters = Injekt.get<GetChaptersByMangaId>()
            var tries = 0
            while (getChapters.await(mangaId).isEmpty() && tries < 80) {
                delay(1500)
                tries++
            }
            val chaptersLoaded = getChapters.await(mangaId).isNotEmpty()

            try {
                val tracker = Injekt.get<TrackerManager>().notion
                val wanted = pageId.replace("-", "")
                val match = tracker.search(title).firstOrNull {
                    it.tracking_url.substringAfterLast("/").substringBefore("?").replace("-", "") == wanted
                } ?: error("Couldn't find that row in Notion.")

                Injekt.get<AddTracks>().bind(tracker, match, mangaId)

                withUIContext {
                    context.toast(
                        if (chaptersLoaded) {
                            "Restored \"$title\" from Notion"
                        } else {
                            "Linked to Notion, but the chapters didn't load in time. Open the title to finish."
                        },
                    )
                }
            } catch (e: Throwable) {
                withUIContext { context.toast("Restore failed: ${e.message}") }
            }
        }
    }
}
