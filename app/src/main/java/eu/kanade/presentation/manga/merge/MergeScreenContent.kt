package eu.kanade.presentation.manga.merge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

private val FullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

/**
 * Dedicated merge screen. Pure UI: no database, search or merge calls happen here.
 *
 * @param gridColumns Pass the same column logic the library grid uses so it follows the user's setting.
 * @param cover Slot that draws a cover. Pass the project's real cover composable here.
 * @param resultItem Slot for one search result. The default is a simple comfortable-grid style item.
 * Replace it with the library's comfortable grid item to match the library and global search.
 */
@Composable
fun MergeScreenContent(
    state: MergeScreenState,
    onBackClick: () -> Unit,
    onOpenSourceSelection: () -> Unit,
    onScopeChange: (MergeSearchScope) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onToggleResult: (MergeCardItem) -> Unit,
    onPrimarySelected: (String) -> Unit,
    onConfirmMerge: () -> Unit,
    modifier: Modifier = Modifier,
    gridColumns: GridCells = GridCells.Fixed(2),
    cover: @Composable (item: MergeCardItem, modifier: Modifier) -> Unit = { _, coverModifier ->
        CoverPlaceholder(coverModifier)
    },
    resultItem: @Composable (
        item: MergeCardItem,
        isSelected: Boolean,
        isOriginal: Boolean,
        onClick: () -> Unit,
    ) -> Unit = { item, isSelected, isOriginal, onClick ->
        MergeResultGridItem(
            item = item,
            isSelected = isSelected,
            isOriginal = isOriginal,
            onClick = onClick,
            cover = cover,
        )
    },
) {
    val hazeState = rememberGlassState()
    val colors = glassColors()
    val density = LocalDensity.current

    var expanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.selectedResults.isEmpty()) {
        if (state.selectedResults.isEmpty()) expanded = false
    }

    var topBarHeight by remember { mutableStateOf(0.dp) }

    val allResults = remember(state.sourceResults) {
        state.sourceResults
            .flatMap { (it.status as? MergeSourceStatus.Success)?.items.orEmpty() }
            .distinctBy { it.key }
    }
    val selectedKeys = remember(state.selectedResults) {
        state.selectedResults.mapTo(HashSet()) { it.key }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = navBottom + if (state.selectedResults.isNotEmpty()) 96.dp else 24.dp

    Box(modifier = modifier.fillMaxSize()) {
        // Everything inside this box is what the glass panels blur.
        Box(modifier = Modifier.fillMaxSize().glassSource(hazeState)) {
            GlassBackdrop(Modifier.fillMaxSize())

            LazyVerticalGrid(
                columns = gridColumns,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = topBarHeight + 8.dp,
                    bottom = bottomPadding,
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "scope", span = FullSpan) {
                    ScopeSelector(scope = state.scope, colors = colors, onScopeChange = onScopeChange)
                }
                item(key = "search", span = FullSpan) {
                    SearchPanel(
                        state = state,
                        colors = colors,
                        onQueryChange = onQueryChange,
                        onSearch = onSearch,
                    )
                }
                item(key = "meta", span = FullSpan) {
                    MetaRow(
                        state = state,
                        resultCount = allResults.size,
                        colors = colors,
                        onOpenSourceSelection = onOpenSourceSelection,
                    )
                }

                resultsContent(
                    state = state,
                    allResults = allResults,
                    onSearch = onSearch,
                    onOpenSourceSelection = onOpenSourceSelection,
                )

                items(items = allResults, key = { it.key }) { item ->
                    resultItem(
                        item,
                        item.key in selectedKeys,
                        item.key == state.original.key,
                    ) { onToggleResult(item) }
                }
            }
        }

        MergeTopBar(
            state = state,
            hazeState = hazeState,
            colors = colors,
            onBackClick = onBackClick,
            onOpenSourceSelection = onOpenSourceSelection,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onSizeChanged { topBarHeight = with(density) { it.height.toDp() } },
        )

        MergeReviewHost(
            state = state,
            hazeState = hazeState,
            colors = colors,
            expanded = expanded,
            onExpandedChange = { expanded = it },
            onPrimarySelected = onPrimarySelected,
            onRemoveResult = onToggleResult,
            onConfirmMerge = onConfirmMerge,
            cover = cover,
        )
    }
}

// ---------------------------------------------------------------------------
// Top bar, scope, search, meta
// ---------------------------------------------------------------------------

@Composable
private fun MergeTopBar(
    state: MergeScreenState,
    hazeState: HazeState,
    colors: GlassColors,
    onBackClick: () -> Unit,
    onOpenSourceSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glass(hazeState, colors, RoundedCornerShape(20.dp))
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = MergeText.Back)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = MergeText.Title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = MergeText.withOriginal(state.original.title, state.original.sourceName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onOpenSourceSelection) {
                Icon(Icons.Filled.Settings, contentDescription = MergeText.SelectSources)
            }
        }
    }
}

@Composable
private fun ScopeSelector(
    scope: MergeSearchScope,
    colors: GlassColors,
    onScopeChange: (MergeSearchScope) -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glass(null, colors, shape)
            .padding(3.dp),
    ) {
        ScopeSegment(
            label = MergeText.SelectedSources,
            selected = scope == MergeSearchScope.SelectedSources,
            colors = colors,
            onClick = { onScopeChange(MergeSearchScope.SelectedSources) },
            modifier = Modifier.weight(1f),
        )
        ScopeSegment(
            label = MergeText.GlobalSearch,
            selected = scope == MergeSearchScope.GlobalSearch,
            colors = colors,
            onClick = { onScopeChange(MergeSearchScope.GlobalSearch) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ScopeSegment(
    label: String,
    selected: Boolean,
    colors: GlassColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (selected) {
                    Modifier
                        .background(colors.strongTint)
                        .border(0.5.dp, colors.border, shape)
                } else {
                    Modifier
                },
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun SearchPanel(
    state: MergeScreenState,
    colors: GlassColors,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    TextField(
        value = state.query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .glass(null, colors, RoundedCornerShape(28.dp)),
        singleLine = true,
        placeholder = { Text(MergeText.SearchPlaceholder) },
        leadingIcon = {
            IconButton(
                onClick = onSearch,
                enabled = state.query.isNotBlank() && !state.isSearching,
            ) {
                Icon(Icons.Filled.Search, contentDescription = MergeText.Search)
            }
        },
        trailingIcon = {
            if (state.query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = MergeText.ClearSearch)
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun MetaRow(
    state: MergeScreenState,
    resultCount: Int,
    colors: GlassColors,
    onOpenSourceSelection: () -> Unit,
) {
    val summary = when {
        state.hasSearched && state.sourceResults.isNotEmpty() ->
            MergeText.resultsSummary(state.sourceResults.size, resultCount)
        state.scope == MergeSearchScope.SelectedSources ->
            MergeText.sourcesSelected(state.selectedSourceCount)
        else -> MergeText.AllEligibleSources
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.scope == MergeSearchScope.SelectedSources) {
            val shape = RoundedCornerShape(14.dp)
            Text(
                text = MergeText.chooseSources(state.selectedSourceCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(shape)
                    .background(colors.tint)
                    .border(0.5.dp, colors.border, shape)
                    .clickable(onClick = onOpenSourceSelection)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// States (loading, error, idle, empty, per-source status)
// ---------------------------------------------------------------------------

private fun LazyGridScope.resultsContent(
    state: MergeScreenState,
    allResults: List<MergeCardItem>,
    onSearch: () -> Unit,
    onOpenSourceSelection: () -> Unit,
) {
    val noSources = state.scope == MergeSearchScope.SelectedSources && state.selectedSourceCount == 0
    val loadingCount = state.sourceResults.count { it.status is MergeSourceStatus.Loading }
    val failed = state.sourceResults.filter { it.status is MergeSourceStatus.Error }

    when {
        state.globalError != null -> item(key = "state-error", span = FullSpan) {
            StateMessage(
                showWarning = true,
                title = MergeText.ErrorTitle,
                body = state.globalError,
                actionLabel = MergeText.Retry,
                onAction = onSearch,
            )
        }

        state.isSearching && state.sourceResults.isEmpty() -> item(key = "state-loading", span = FullSpan) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    text = MergeText.Searching,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        !state.hasSearched -> item(key = "state-idle", span = FullSpan) {
            if (noSources) {
                StateMessage(
                    showWarning = false,
                    title = MergeText.NoSourcesTitle,
                    body = MergeText.NoSourcesBody,
                    actionLabel = MergeText.SelectSources,
                    onAction = onOpenSourceSelection,
                )
            } else {
                StateMessage(
                    showWarning = false,
                    title = MergeText.IdleTitle,
                    body = MergeText.IdleBody,
                )
            }
        }

        !state.isSearching && allResults.isEmpty() && failed.isEmpty() -> item(key = "state-empty", span = FullSpan) {
            if (noSources) {
                StateMessage(
                    showWarning = false,
                    title = MergeText.NoSourcesTitle,
                    body = MergeText.NoSourcesBody,
                    actionLabel = MergeText.SelectSources,
                    onAction = onOpenSourceSelection,
                )
            } else {
                StateMessage(
                    showWarning = false,
                    title = MergeText.EmptyTitle,
                    body = MergeText.EmptyBody,
                )
            }
        }

        else -> {
            if (loadingCount > 0) {
                item(key = "status-loading", span = FullSpan) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text(
                            text = MergeText.searchingSources(loadingCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            failed.forEach { result ->
                val message = (result.status as MergeSourceStatus.Error).message
                item(key = "status-error-${result.sourceId}", span = FullSpan) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = MergeText.sourceFailed(result.sourceName, message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StateMessage(
    showWarning: Boolean,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showWarning) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

// ---------------------------------------------------------------------------
// Default result item (comfortable-grid style: title and source under the cover)
// ---------------------------------------------------------------------------

/**
 * Default result cell. Replace via the `resultItem` slot with the library's comfortable grid item
 * to match the library and global search exactly.
 */
@Composable
fun MergeResultGridItem(
    item: MergeCardItem,
    isSelected: Boolean,
    isOriginal: Boolean,
    onClick: () -> Unit,
    cover: @Composable (MergeCardItem, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val coverShape = RoundedCornerShape(14.dp)
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Column(
        modifier = modifier
            .then(if (isOriginal) Modifier.alpha(0.55f) else Modifier)
            .then(
                if (isOriginal) {
                    Modifier
                } else {
                    Modifier.toggleable(value = isSelected, role = Role.Checkbox, onValueChange = { onClick() })
                },
            ),
    ) {
        Box {
            cover(
                item,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(coverShape)
                    .border(if (isSelected) 2.dp else 0.dp, borderColor, coverShape),
            )
            Text(
                text = MergeText.chapterBadge(item.totalChapters),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(3.dp),
                )
            }
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, top = 6.dp),
        )
        Text(
            text = if (isOriginal) MergeText.Current else item.sourceName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

@Composable
private fun CoverPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
}