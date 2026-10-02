package eu.kanade.tachiyomi.ui.manga.recommendations

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.library.components.MangaComfortableGridItem
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * User settings for the recommendations screen: which recommendation types show,
 * how many rows each type shows, and which individual rows (sources / keywords) are hidden.
 * Persisted, so the choices stick between entries and app restarts.
 */
class RecommendationRowPreferences(
    store: PreferenceStore = Injekt.get<PreferenceStore>(),
) {
    val showSourceRows: Preference<Boolean> = store.getBoolean("rec_show_source_rows", true)
    val showRelatedRows: Preference<Boolean> = store.getBoolean("rec_show_related_rows", true)
    val showAiRows: Preference<Boolean> = store.getBoolean("rec_show_ai_rows", true)
    val showTrackerRows: Preference<Boolean> = store.getBoolean("rec_show_tracker_rows", true)

    val sourceMaxRows: Preference<Int> = store.getInt("rec_source_max_rows", 5)
    val relatedMaxRows: Preference<Int> = store.getInt("rec_related_max_rows", 4)

    val hiddenSourceRows: Preference<Set<String>> = store.getStringSet("rec_hidden_source_rows", emptySet())
    val hiddenRelatedRows: Preference<Set<String>> = store.getStringSet("rec_hidden_related_rows", emptySet())
}

private data class RowSettings(
    val showSource: Boolean,
    val showRelated: Boolean,
    val showAi: Boolean,
    val showTracker: Boolean,
    val sourceMaxRows: Int,
    val relatedMaxRows: Int,
    val hiddenSourceRows: Set<String>,
    val hiddenRelatedRows: Set<String>,
)

@Composable
private fun <T> Preference<T>.watch(): State<T> {
    val flow = remember(this) { changes() }
    return flow.collectAsState(initial = get())
}

private fun Preference<Set<String>>.toggle(id: String) {
    val current = get()
    set(if (id in current) current - id else current + id)
}

class MangaRecommendationsScreen(
    private val mangaId: Long,
) : Screen() {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val vm: MangaRecommendationsViewModel = viewModel(
            factory = MangaRecommendationsViewModel.Factory(mangaId),
        )
        val state by vm.state.collectAsState()

        val prefs = remember { RecommendationRowPreferences() }
        val showSource by prefs.showSourceRows.watch()
        val showRelated by prefs.showRelatedRows.watch()
        val showAi by prefs.showAiRows.watch()
        val showTracker by prefs.showTrackerRows.watch()
        val sourceMaxRows by prefs.sourceMaxRows.watch()
        val relatedMaxRows by prefs.relatedMaxRows.watch()
        val hiddenSourceRows by prefs.hiddenSourceRows.watch()
        val hiddenRelatedRows by prefs.hiddenRelatedRows.watch()

        val settings = RowSettings(
            showSource = showSource,
            showRelated = showRelated,
            showAi = showAi,
            showTracker = showTracker,
            sourceMaxRows = sourceMaxRows,
            relatedMaxRows = relatedMaxRows,
            hiddenSourceRows = hiddenSourceRows,
            hiddenRelatedRows = hiddenRelatedRows,
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Recommendations") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        var menuOpen by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Outlined.Tune, contentDescription = "Choose recommendation types")
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                CheckableMenuItem("More from sources", settings.showSource) {
                                    prefs.showSourceRows.set(!settings.showSource)
                                }
                                CheckableMenuItem("More like this", settings.showRelated) {
                                    prefs.showRelatedRows.set(!settings.showRelated)
                                }
                                CheckableMenuItem("AI picks", settings.showAi) {
                                    prefs.showAiRows.set(!settings.showAi)
                                }
                                CheckableMenuItem("Tracker recommendations", settings.showTracker) {
                                    prefs.showTrackerRows.set(!settings.showTracker)
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { innerPadding ->
            when (val s = state) {
                is MangaRecommendationsUiState.Loading -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
                is MangaRecommendationsUiState.Success -> {
                    RecommendationsContent(
                        state = s,
                        settings = settings,
                        prefs = prefs,
                        onMangaClick = { navigator.push(eu.kanade.tachiyomi.ui.manga.MangaScreen(it.id)) },
                        onKeywordClick = { navigator.push(BrowseSourceScreen(s.manga.source, it)) },
                        onKeywordLongClick = { navigator.push(GlobalSearchScreen(it)) },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecommendationsContent(
    state: MangaRecommendationsUiState.Success,
    settings: RowSettings,
    prefs: RecommendationRowPreferences,
    onMangaClick: (Manga) -> Unit,
    onKeywordClick: (String) -> Unit,
    onKeywordLongClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // "More from sources" rows: one per source (falls back to a single row for the current source).
    val sourceRows: List<Pair<String, List<Manga>>> = when {
        state.groupedSourceSuggestions.isNotEmpty() -> state.groupedSourceSuggestions.toList()
        state.sourceSuggestions.isNotEmpty() -> listOf("This source" to state.sourceSuggestions)
        else -> emptyList()
    }
    val visibleSourceRows = sourceRows
        .filter { it.first !in settings.hiddenSourceRows }
        .take(settings.sourceMaxRows)

    val visibleRelatedGroups = state.relatedGroups
        .filter { it.keyword !in settings.hiddenRelatedRows }
        .take(settings.relatedMaxRows)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // Hero — cover + title + source
        item { MangaHero(state.manga, state.sourceName) }

        // More from sources — header keeps its dropdown even if every row is hidden
        if (settings.showSource && sourceRows.isNotEmpty()) {
            item {
                SectionHeaderWithMenu(
                    title = "More from sources",
                    rowLabels = sourceRows.map { it.first },
                    hiddenRows = settings.hiddenSourceRows,
                    maxRows = settings.sourceMaxRows,
                    onToggleRow = { prefs.hiddenSourceRows.toggle(it) },
                    onMaxRowsChange = { prefs.sourceMaxRows.set(it) },
                )
            }
            items(visibleSourceRows, key = { "source_${it.first}" }) { row ->
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text(
                        text = row.first,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    SuggestionRow(
                        suggestions = row.second,
                        onMangaClick = onMangaClick,
                    )
                }
            }
        }

        // More like this — one row per genre / author keyword
        if (settings.showRelated && state.relatedGroups.isNotEmpty()) {
            item { Spacer(Modifier.height(8.dp)) }
            item {
                SectionHeaderWithMenu(
                    title = "More like this",
                    rowLabels = state.relatedGroups.map { it.keyword },
                    hiddenRows = settings.hiddenRelatedRows,
                    maxRows = settings.relatedMaxRows,
                    onToggleRow = { prefs.hiddenRelatedRows.toggle(it) },
                    onMaxRowsChange = { prefs.relatedMaxRows.set(it) },
                )
            }
            items(visibleRelatedGroups, key = { "related_${it.keyword}" }) { group ->
                Column(Modifier.padding(bottom = 8.dp)) {
                    // Tap: browse this source for the keyword. Long-press: global search.
                    Text(
                        text = "${group.keyword} ›",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .combinedClickable(
                                onClick = { onKeywordClick(group.keyword) },
                                onLongClick = { onKeywordLongClick(group.keyword) },
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    SuggestionRow(
                        suggestions = group.mangas,
                        onMangaClick = onMangaClick,
                    )
                }
            }
        }

        // AI picks for this novel
        if (settings.showAi) {
            item { Spacer(Modifier.height(8.dp)) }
            item { AiPicksSection(state.aiPicks, state.aiScores, state.aiMessage, onMangaClick) }
        }

        // Tracker recommendations — only when a non-Notion tracker is linked
        if (settings.showTracker && state.trackedOn.isNotEmpty()) {
            item { Spacer(Modifier.height(8.dp)) }
            item { TrackerRecommendationsSection(state.trackedOn) }
        }
    }
}

@Composable
private fun CheckableMenuItem(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        onClick = onClick,
    )
}

/**
 * Section title with a dropdown: a rows-shown stepper plus a checklist of every
 * row (source / keyword) in the section.
 */
@Composable
private fun SectionHeaderWithMenu(
    title: String,
    rowLabels: List<String>,
    hiddenRows: Set<String>,
    maxRows: Int,
    onToggleRow: (String) -> Unit,
    onMaxRowsChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val available = rowLabels.size.coerceAtLeast(1)
    val shown = maxRows.coerceIn(1, available)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        )
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Outlined.Tune, contentDescription = "Customize rows")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = "Rows shown",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onMaxRowsChange((shown - 1).coerceAtLeast(1)) },
                        enabled = shown > 1,
                    ) {
                        Icon(Icons.Outlined.Remove, contentDescription = "Fewer rows")
                    }
                    Text(
                        text = "$shown",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    IconButton(
                        onClick = { onMaxRowsChange((shown + 1).coerceAtMost(available)) },
                        enabled = shown < available,
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "More rows")
                    }
                }
                Spacer(Modifier.height(4.dp))
                rowLabels.forEach { label ->
                    CheckableMenuItem(
                        label = label,
                        checked = label !in hiddenRows,
                        onClick = { onToggleRow(label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MangaHero(manga: Manga, sourceName: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(manga)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(68.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = manga.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (manga.author != null) {
                Text(
                    text = "by ${manga.author}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (sourceName.isNotBlank()) {
                Text(
                    text = sourceName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestions: List<Manga>,
    onMangaClick: (Manga) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(suggestions, key = { "${it.source}_${it.id}" }) { manga ->
            Box(modifier = Modifier.width(110.dp)) {
                MangaComfortableGridItem(
                    isSelected = false,
                    title = manga.title,
                    coverData = MangaCover(
                        mangaId = manga.id,
                        sourceId = manga.source,
                        isMangaFavorite = manga.favorite,
                        url = manga.thumbnailUrl,
                        lastModified = manga.coverLastModified,
                    ),
                    coverBadgeStart = {},
                    coverBadgeEnd = {},
                    onLongClick = {},
                    onClick = { onMangaClick(manga) },
                    onClickContinueReading = null,
                    titleMaxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun AiPicksSection(
    picks: List<Manga>,
    scores: List<Int?>,
    message: String?,
    onMangaClick: (Manga) -> Unit,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "AI picks for this novel",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "based on your reading history",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when {
            picks.isNotEmpty() -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(picks.size) { idx ->
                        val manga = picks[idx]
                        val score = scores.getOrNull(idx)
                        Box(modifier = Modifier.width(100.dp)) {
                            MangaComfortableGridItem(
                                isSelected = false,
                                title = manga.title,
                                coverData = MangaCover(
                                    mangaId = manga.id,
                                    sourceId = manga.source,
                                    isMangaFavorite = manga.favorite,
                                    url = manga.thumbnailUrl,
                                    lastModified = manga.coverLastModified,
                                ),
                                coverBadgeStart = {},
                                coverBadgeEnd = {},
                                onLongClick = {},
                                onClick = { onMangaClick(manga) },
                                onClickContinueReading = null,
                                titleMaxLines = 2,
                            )
                            if (score != null) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    MaterialTheme.colorScheme.tertiary,
                                                ),
                                            ),
                                        )
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "$score%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            message != null -> {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun TrackerRecommendationsSection(trackedOn: List<String>) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Sync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Tracker recommendations",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Text(
            text = "Linked via: ${trackedOn.joinToString(", ")}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tracked titles will appear here once your services return recommendations for this entry.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
