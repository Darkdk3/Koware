package eu.kanade.presentation.manga.merge

/**
 * Hardcoded English strings. Move these to the project's string resources during integration.
 */
internal object MergeText {
    const val Title = "Merge"
    const val Back = "Back"
    const val SelectSources = "Choose sources"
    const val SelectedSources = "Selected sources"
    const val GlobalSearch = "Global search"
    const val SearchPlaceholder = "Search title"
    const val Search = "Search"
    const val ClearSearch = "Clear search"
    const val AllEligibleSources = "All eligible sources"
    const val Current = "Current"
    const val Original = "Original"
    const val ChapterComparison = "Chapter comparison"
    const val StarHint = "Star sets the info manga"
    const val SetAsInfo = "Use as info manga"
    const val Remove = "Remove from merge"
    const val Author = "Author"
    const val Artist = "Artist"
    const val Confirm = "Confirm merge"
    const val ReviewChapters = "Review chapters"
    const val Expand = "Expand"
    const val Collapse = "Collapse"
    const val SameChapterCount = "Both have the same number of chapters"
    const val Retry = "Retry"
    const val IdleTitle = "Find manga to merge"
    const val IdleBody = "Search for the same title in another source."
    const val EmptyTitle = "No results"
    const val EmptyBody = "Try a different title or another search scope."
    const val NoSourcesTitle = "No sources selected"
    const val NoSourcesBody = "Pick the sources to search, or switch to global search."
    const val ErrorTitle = "Search failed"
    const val Searching = "Searching…"

    fun withOriginal(title: String, source: String) = "with $title · $source"

    fun sourcesSelected(count: Int) = if (count == 1) "1 source selected" else "$count sources selected"

    fun chooseSources(count: Int) = "$SelectSources ($count)"

    fun resultsSummary(sources: Int, results: Int): String {
        val s = if (sources == 1) "1 source" else "$sources sources"
        val r = if (results == 1) "1 result" else "$results results"
        return "$s · $r"
    }

    fun selectedCount(count: Int) = if (count == 1) "1 selected" else "$count selected"

    fun chapterBadge(total: Int?) = if (total == null) "… ch" else "$total ch"

    fun chapterStats(read: Int?, total: Int?): String {
        val readText = read?.toString() ?: "—"
        val totalText = total?.toString() ?: "…"
        return "$readText read · $totalText total"
    }

    fun moreChapters(maxSource: String, difference: Int, minSource: String): String {
        val noun = if (difference == 1) "chapter" else "chapters"
        return "$maxSource has $difference more $noun than $minSource"
    }

    fun searchingSources(count: Int) = if (count == 1) "Searching 1 source…" else "Searching $count sources…"

    fun sourceFailed(source: String, message: String) = "$source: $message"
}