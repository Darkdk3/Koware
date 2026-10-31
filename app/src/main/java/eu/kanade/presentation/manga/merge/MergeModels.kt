package eu.kanade.presentation.manga.merge

import androidx.compose.runtime.Immutable

/** Which sources the merge search runs against. */
enum class MergeSearchScope {
    /** Sources chosen in the existing Migration Config screen. */
    SelectedSources,

    /** Existing global-search infrastructure across all eligible sources. */
    GlobalSearch,
}

/**
 * A manga shown anywhere on the screen (the original, a search result, or an entry in the review sheet).
 *
 * @param key Opaque, unique identifier supplied by the integration layer. The UI never interprets it.
 * @param coverModel Opaque cover model handed to the `cover` slot of [MergeScreenContent].
 * @param author Null while unknown or not yet fetched.
 * @param artist Null while unknown or not yet fetched.
 * @param totalChapters Null until the chapter list has been fetched.
 * @param readChapters Null when unknown. Use 0 for manga that is not in the library.
 */
@Immutable
data class MergeCardItem(
    val key: String,
    val title: String,
    val sourceName: String,
    val coverModel: Any? = null,
    val author: String? = null,
    val artist: String? = null,
    val totalChapters: Int? = null,
    val readChapters: Int? = null,
)

/** Loading / error / success status of one source's search. */
sealed interface MergeSourceStatus {
    data object Loading : MergeSourceStatus
    data class Error(val message: String) : MergeSourceStatus
    data class Success(val items: List<MergeCardItem>) : MergeSourceStatus
}

/** Results for a single source. Both search scopes emit per source. */
@Immutable
data class MergeSourceResult(
    val sourceId: Long,
    val sourceName: String,
    val status: MergeSourceStatus,
)

/**
 * Everything the screen renders. All values are provided by the ViewModel / integration layer.
 *
 * @param original The manga the user started the merge from.
 * @param selectedResults Search results currently ticked for merging, in selection order.
 * @param primaryKey Key of the info/primary manga. Must be [original]'s key or one of [selectedResults].
 * @param selectedSourceCount Number of sources saved in the Migration Config screen.
 * @param hasSearched True once a search has been started at least once.
 * @param isSearching True while any search is in flight.
 * @param globalError Non-null when the whole search failed (as opposed to a single source).
 * @param isConfirming True while the merge is being executed.
 */
@Immutable
data class MergeScreenState(
    val original: MergeCardItem,
    val query: String,
    val scope: MergeSearchScope,
    val selectedSourceCount: Int,
    val hasSearched: Boolean,
    val isSearching: Boolean,
    val sourceResults: List<MergeSourceResult>,
    val selectedResults: List<MergeCardItem>,
    val primaryKey: String,
    val globalError: String? = null,
    val isConfirming: Boolean = false,
) {
    /** The original followed by every selected result. */
    val groupItems: List<MergeCardItem> get() = listOf(original) + selectedResults

    val canConfirm: Boolean get() = selectedResults.isNotEmpty() && !isConfirming
}
