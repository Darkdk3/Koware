package eu.kanade.presentation.manga.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.toBitmap
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotatorConfig
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.library.components.rememberCoverRatio
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.findChildOfType
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.clickableNoIndication
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.secondaryItemAlpha
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
fun MangaInfoBox(
    isTabletUi: Boolean,
    appBarPadding: Dp,
    manga: Manga,
    sourceName: String,
    isStubSource: Boolean,
    categories: List<Category>,
    onCoverClick: () -> Unit,
    doSearch: (query: String, global: Boolean) -> Unit,
    onCoverLoaded: (Color) -> Unit = {},
    modifier: Modifier = Modifier,
    // New - defaults to false, so every existing call site is unaffected until MangaScreen.kt
    // is updated to pass the real value from the appearance preference.
    modernStyle: Boolean = false,
) {
    val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val hideBackdrop by libraryPreferences.mangaDetailsHideBackdrop.collectAsState()
    val centerCover by libraryPreferences.mangaDetailsCenterCover.collectAsState()
    val freeformCover by libraryPreferences.mangaDetailsFreeformCover.collectAsState()
    val centerCoverSizePercent by libraryPreferences.mangaDetailsCenterCoverSizePercent.collectAsState()
    val backdropBlurDp by libraryPreferences.mangaDetailsBackdropBlurDp.collectAsState()
    val backdropOpacityPercent by libraryPreferences.mangaDetailsBackdropOpacityPercent.collectAsState()
    val backdropBrightnessPercent by libraryPreferences.mangaDetailsBackdropBrightnessPercent.collectAsState()

    val backdropColorFilter = remember(backdropBrightnessPercent) {
        val scale = backdropBrightnessPercent / 100f
        ColorFilter.colorMatrix(
            ColorMatrix().apply { setToScale(scale, scale, scale, 1f) },
        )
    }

    Box(modifier = modifier) {
        // Backdrop image is always loaded (needed for palette extraction below, which is
        // independent of whether it's visually shown), but only rendered visibly when the
        // "hide backdrop" appearance setting is off. Default (false) preserves the existing
        // look exactly.
        val backdropGradientColors = listOf(
            Color.Transparent,
            MaterialTheme.colorScheme.background,
        )

        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(manga)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = backdropColorFilter,
            onSuccess = { state ->
                // Feeds the "cover-based theme" appearance option. Runs regardless of the
                // hideBackdrop toggle since the two are independent settings.
                // NOTE: coil3.toBitmap() is the one line here I'm not fully certain about -
                // if this fails to resolve, check Coil3 3.5.0's real Image->Bitmap API
                // (version pinned in libs.versions.toml) and swap this call accordingly.
                runCatching {
                    val bitmap = state.result.image.toBitmap()
                    Palette.Builder(bitmap).generate { palette ->
                        val rgb = palette?.vibrantSwatch?.rgb
                            ?: palette?.dominantSwatch?.rgb
                            ?: palette?.mutedSwatch?.rgb
                        if (rgb != null) onCoverLoaded(Color(rgb))
                    }
                }
            },
            modifier = if (!hideBackdrop) {
                Modifier
                    .matchParentSize()
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(colors = backdropGradientColors),
                        )
                    }
                    // Modern style: same slider-driven blur/opacity, but with a visible floor so
                    // the cover's color still reads through even if the user's sliders are set
                    // very low - "more flair" per the redesign, without fighting their settings.
                    .blur(if (modernStyle) (backdropBlurDp / 2).dp else backdropBlurDp.dp)
                    .alpha(
                        if (modernStyle) {
                            (backdropOpacityPercent / 100f).coerceAtLeast(0.5f)
                        } else {
                            backdropOpacityPercent / 100f
                        },
                    )
            } else {
                // Still needs to load for palette extraction, but shouldn't be visible.
                Modifier.size(1.dp).alpha(0f)
            },
        )

        if (!hideBackdrop) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to MaterialTheme.colorScheme.primary.copy(alpha = if (modernStyle) 0.20f else 0.12f),
                            0.45f to Color.Transparent,
                            1f to MaterialTheme.colorScheme.background,
                        ),
                    ),
            )
        }

        // Manga & source info. The "center cover" appearance setting forces the centered
        // layout (normally tablet-only) even on phone, reusing it rather than building a
        // separate centered layout from scratch.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            ModernMangaHeader(
                appBarPadding = appBarPadding,
                manga = manga,
                sourceName = sourceName,
                isStubSource = isStubSource,
                categories = categories,
                onCoverClick = onCoverClick,
                doSearch = doSearch,
                freeformCover = freeformCover,
                centerCover = centerCover,
                coverSizePercent = centerCoverSizePercent,
                modernStyle = modernStyle,
            )
        }
    }
}

@Composable
fun MangaActionRow(
    favorite: Boolean,
    trackingCount: Int,
    nextUpdate: Instant?,
    isUserIntervalMode: Boolean,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,
    onEditIntervalClicked: (() -> Unit)?,
    onEditCategory: (() -> Unit)?,
    modifier: Modifier = Modifier,
    // New - both default so every existing call site compiles unchanged.
    modernStyle: Boolean = false,
    /**
     * Only used when modernStyle is on, for the "Tracked on" pill row. Populate this from
     * MangaViewModel.State.Success once you've added the field there (see MangaViewModel.kt) -
     * defaults to empty so the row simply doesn't render until that's wired up.
     */
    trackItems: List<TrackItem> = emptyList(),
) {
    val defaultActionButtonColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
    val nextUpdateDays = remember(nextUpdate) {
        return@remember if (nextUpdate != null) {
            val now = Clock.System.now()
            now.daysUntil(nextUpdate, TimeZone.currentSystemDefault()).coerceAtLeast(0)
        } else {
            null
        }
    }

    if (!modernStyle) {
        Row(modifier = modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)) {
            MangaActionButton(
                title = if (favorite) {
                    stringResource(MR.strings.in_library)
                } else {
                    stringResource(MR.strings.add_to_library)
                },
                icon = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                color = if (favorite) MaterialTheme.colorScheme.primary else defaultActionButtonColor,
                onClick = onAddToLibraryClicked,
                onLongClick = onEditCategory,
            )
            MangaActionButton(
                title = when (nextUpdateDays) {
                    null -> stringResource(MR.strings.not_applicable)
                    0 -> stringResource(MR.strings.manga_interval_expected_update_soon)
                    else -> pluralStringResource(
                        MR.plurals.day,
                        count = nextUpdateDays,
                        nextUpdateDays,
                    )
                },
                icon = Icons.Default.HourglassEmpty,
                color = if (isUserIntervalMode) MaterialTheme.colorScheme.primary else defaultActionButtonColor,
                onClick = { onEditIntervalClicked?.invoke() },
            )
            MangaActionButton(
                title = if (trackingCount == 0) {
                    stringResource(MR.strings.manga_tracking_tab)
                } else {
                    pluralStringResource(MR.plurals.num_trackers, count = trackingCount, trackingCount)
                },
                icon = if (trackingCount == 0) Icons.Outlined.Sync else Icons.Outlined.Done,
                color = if (trackingCount == 0) defaultActionButtonColor else MaterialTheme.colorScheme.primary,
                onClick = onTrackingClicked,
            )
            if (onWebViewClicked != null) {
                MangaActionButton(
                    title = stringResource(MR.strings.action_web_view),
                    icon = Icons.Outlined.Public,
                    color = defaultActionButtonColor,
                    onClick = onWebViewClicked,
                    onLongClick = onWebViewLongClicked,
                )
            }
        }
        return
    }

    // --- Modern style: a single row - tonal "In library" pill, outlined "N days" and tracker
    // count pills, and a round outlined web-view button. Buttons size to their content. ---
    Column(modifier = modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)) {
        val nextUpdateLabel = when (nextUpdateDays) {
            null -> stringResource(MR.strings.not_applicable)
            0 -> stringResource(MR.strings.manga_interval_expected_update_soon)
            else -> pluralStringResource(MR.plurals.day, count = nextUpdateDays, nextUpdateDays)
        }
        val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
        val pillPadding = PaddingValues(horizontal = 16.dp)

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = onAddToLibraryClicked,
                contentPadding = pillPadding,
            ) {
                Icon(
                    imageVector = Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (favorite) {
                        stringResource(MR.strings.in_library)
                    } else {
                        stringResource(MR.strings.add_to_library)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = { onEditIntervalClicked?.invoke() },
                contentPadding = pillPadding,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isUserIntervalMode) MaterialTheme.colorScheme.primary else mutedColor,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.HourglassEmpty,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = nextUpdateLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = onTrackingClicked,
                contentPadding = pillPadding,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = mutedColor),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Sync,
                    contentDescription = stringResource(MR.strings.manga_tracking_tab),
                    modifier = Modifier.size(16.dp),
                )
                if (trackingCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(text = trackingCount.toString(), maxLines = 1)
                }
            }
            if (onWebViewClicked != null) {
                OutlinedIconButton(
                    onClick = onWebViewClicked,
                    modifier = Modifier.size(48.dp),
                    colors = androidx.compose.material3.IconButtonDefaults.outlinedIconButtonColors(
                        contentColor = mutedColor,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Public,
                        contentDescription = stringResource(MR.strings.action_web_view),
                    )
                }
                // NOTE: OutlinedIconButton has no built-in onLongClick param the way TextButton
                // (used in the legacy MangaActionButton below) does. If you need long-press to
                // reopen the WebView in a specific way, wrap this button's Modifier in
                // Modifier.combinedClickable(onClick, onLongClick) instead of using the
                // component's own onClick lambda - onWebViewLongClicked is intentionally unused
                // in this branch for now.
            }
        }

        if (trackItems.any { it.track != null }) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Tracked on",
                style = MaterialTheme.typography.labelMedium,
                color = LocalContentColor.current.copy(alpha = DISABLED_ALPHA),
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(trackItems.filter { it.track != null }) { item ->
                    TrackerPill(item = item, onClick = onTrackingClicked)
                }
            }
        }
    }
}

/**
 * One linked-tracker pill for the modern action row: service name + last-synced chapter,
 * styled the same hollow/outlined way as the genre tag chips (TagsChip / SuggestionChip above)
 * rather than a solid fill, so it matches the rest of the theme instead of standing out.
 *
 * NOTE: `item.track?.lastChapterRead` below assumes tachiyomi.domain.track.model.Track exposes
 * a `lastChapterRead: Double` field, matching the name already used elsewhere in your
 * MangaViewModel.kt (`track.lastChapterRead` appears in the tracker-update-prompt logic there).
 * If your actual Track model names this differently, this is the one line to adjust.
 *
 * Still text-only (tracker.name) rather than a logo icon - swapping in the real per-service
 * icon (with a glow behind it) needs to see how your existing tracker sheet renders logos
 * (e.g. Tracker.getLogo() or similar) so it's copied from a real source instead of guessed.
 */
@Composable
private fun TrackerPill(item: TrackItem, onClick: () -> Unit) {
    val track = item.track
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        SuggestionChip(
            onClick = onClick,
            modifier = Modifier.height(28.dp),
            label = {
                Text(
                    text = if (track != null) {
                        "${item.tracker.name} · Ch. ${track.lastChapterRead.toInt()}"
                    } else {
                        item.tracker.name
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
            },
        )
    }
}

/**
 * Collapsible "N of M chapters read" bar for the modern manga screen. Collapsed by default,
 * expand state is a persisted global preference (same toggle for every manga you open) rather
 * than per-title, per your call to keep it simple.
 *
 * The preference is defined right here (straight from the PreferenceStore) instead of in
 * LibraryPreferences.kt, so this file compiles without any change to that class. It is stored
 * under the key "pref_manga_details_show_progress_bar" - if you later add a
 * `mangaDetailsShowProgressBar` property to LibraryPreferences using the same key, the saved
 * value carries over.
 */
@Composable
fun ChapterProgressToggle(
    readCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    val showProgressPref = remember {
        Injekt.get<tachiyomi.core.common.preference.PreferenceStore>()
            .getBoolean("pref_manga_details_show_progress_bar", false)
    }
    val expanded by showProgressPref.collectAsState()
    val percent = if (totalCount > 0) (readCount * 100) / totalCount else 0

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickableNoIndication { showProgressPref.set(!expanded) },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.BarChart,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = LocalContentColor.current.copy(alpha = DISABLED_ALPHA),
                )
                Text(
                    text = "Progress",
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalContentColor.current.copy(alpha = DISABLED_ALPHA),
                )
            }
            OutlinedIconButton(
                onClick = { showProgressPref.set(!expanded) },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
            }
        }

        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "$readCount of $totalCount chapters read",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalContentColor.current.copy(alpha = DISABLED_ALPHA),
                )
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = LocalContentColor.current.copy(alpha = DISABLED_ALPHA),
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (percent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(MaterialTheme.shapes.extraSmall),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
    }
}

@Composable
fun ExpandableMangaDescription(
    defaultExpandState: Boolean,
    description: String?,
    tagsProvider: () -> List<String>?,
    notes: String,
    onTagSearch: (String) -> Unit,
    onCopyTagToClipboard: (tag: String) -> Unit,
    onEditNotes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        val (expanded, onExpanded) = rememberSaveable {
            mutableStateOf(defaultExpandState)
        }
        val desc =
            description.takeIf { !it.isNullOrBlank() } ?: stringResource(MR.strings.description_placeholder)
        MangaSummary(
            description = desc,
            expanded = expanded,
            notes = notes,
            onEditNotesClicked = onEditNotes,
            modifier = Modifier
                .padding(top = 8.dp)
                .padding(horizontal = 16.dp)
                .clickableNoIndication { onExpanded(!expanded) },
        )
        val tags = tagsProvider()
        if (!tags.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .padding(vertical = 12.dp)
                    .animateContentSize(animationSpec = spring())
                    .fillMaxWidth(),
            ) {
                var showMenu by remember { mutableStateOf(false) }
                var tagSelected by remember { mutableStateOf("") }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(MR.strings.action_search)) },
                        onClick = {
                            onTagSearch(tagSelected)
                            showMenu = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(MR.strings.action_copy_to_clipboard)) },
                        onClick = {
                            onCopyTagToClipboard(tagSelected)
                            showMenu = false
                        },
                    )
                }
                if (expanded) {
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                    ) {
                        tags.forEach {
                            TagsChip(
                                modifier = DefaultTagChipModifier,
                                text = it,
                                onClick = {
                                    tagSelected = it
                                    showMenu = true
                                },
                            )
                        }
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = MaterialTheme.padding.medium),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                    ) {
                        items(items = tags) {
                            TagsChip(
                                modifier = DefaultTagChipModifier,
                                text = it,
                                onClick = {
                                    tagSelected = it
                                    showMenu = true
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modern ("redesigned") details header: the cover/title block sits on a neutral surface card
 * with rounded corners and a 1dp stroke. The blurred cover backdrop stays behind it as a
 * capped wash (see the scrim in [MangaInfoBox]) instead of bleeding into the text, and the
 * accent is reserved for the action row / buttons on the screen.
 */
@Composable
private fun ModernMangaHeader(
    appBarPadding: Dp,
    manga: Manga,
    sourceName: String,
    isStubSource: Boolean,
    categories: List<Category>,
    onCoverClick: () -> Unit,
    doSearch: (query: String, global: Boolean) -> Unit,
    freeformCover: Boolean,
    centerCover: Boolean,
    coverSizePercent: Int,
    modernStyle: Boolean = false,
) {
    if (centerCover) {
        MangaAndSourceTitlesLarge(
            modifier = Modifier.padding(top = appBarPadding + 24.dp),
            manga = manga,
            sourceName = sourceName,
            isStubSource = isStubSource,
            categories = categories,
            onCoverClick = onCoverClick,
            doSearch = doSearch,
            freeformCover = freeformCover,
            coverSizePercent = coverSizePercent,
            modernStyle = modernStyle,
        )
    } else {
        MangaAndSourceTitlesSmall(
            modifier = Modifier.padding(top = appBarPadding + 24.dp),
            manga = manga,
            sourceName = sourceName,
            isStubSource = isStubSource,
            categories = categories,
            onCoverClick = onCoverClick,
            doSearch = doSearch,
            freeformCover = freeformCover,
            modernStyle = modernStyle,
        )
    }
}

@Composable
private fun MangaAndSourceTitlesLarge(
    modifier: Modifier = Modifier,
    manga: Manga,
    sourceName: String,
    isStubSource: Boolean,
    categories: List<Category>,
    onCoverClick: () -> Unit,
    doSearch: (query: String, global: Boolean) -> Unit,
    freeformCover: Boolean = false,
    coverSizePercent: Int = 65,
    modernStyle: Boolean = false,
) {
    // Null until measured (or when freeformCover is off) - falls back to MangaCover.Book's own
    // default ratio via the `?:` below, same pattern used for the library grid's freeform mode.
    val ratio = rememberCoverRatio(manga = manga, enabled = freeformCover)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MangaCover.Book(
            modifier = Modifier
                .fillMaxWidth(coverSizePercent / 100f)
                .let { m -> if (ratio != null) m.aspectRatio(ratio) else m }
                .let { m ->
                    if (modernStyle) {
                        m.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    } else {
                        m
                    }
                },
            data = ImageRequest.Builder(LocalContext.current)
                .data(manga)
                .crossfade(true)
                .build(),
            contentDescription = stringResource(MR.strings.manga_cover),
            onClick = onCoverClick,
            // The measured freeform ratio is already applied above; don't let the fixed 2:3
            // book shape override it and crop the cover back to the default ratio.
            applyAspectRatio = ratio == null,
        )
        Spacer(modifier = Modifier.height(16.dp))
        MangaContentInfo(
            title = manga.title,
            alternativeTitles = manga.alternativeTitles,
            author = manga.author,
            artist = manga.artist,
            status = manga.status,
            sourceName = sourceName,
            isStubSource = isStubSource,
            categories = categories,
            doSearch = doSearch,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MangaAndSourceTitlesSmall(
    modifier: Modifier = Modifier,
    manga: Manga,
    sourceName: String,
    isStubSource: Boolean,
    categories: List<Category>,
    onCoverClick: () -> Unit,
    doSearch: (query: String, global: Boolean) -> Unit,
    freeformCover: Boolean = false,
    modernStyle: Boolean = false,
) {
    val ratio = rememberCoverRatio(manga = manga, enabled = freeformCover)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaCover.Book(
            modifier = Modifier
                .sizeIn(maxWidth = 100.dp)
                .align(Alignment.Top)
                .let { m -> if (ratio != null) m.aspectRatio(ratio) else m }
                .let { m ->
                    if (modernStyle) {
                        m.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    } else {
                        m
                    }
                },
            data = ImageRequest.Builder(LocalContext.current)
                .data(manga)
                .crossfade(true)
                .build(),
            contentDescription = stringResource(MR.strings.manga_cover),
            onClick = onCoverClick,
            applyAspectRatio = ratio == null,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MangaContentInfo(
                title = manga.title,
                alternativeTitles = manga.alternativeTitles,
                author = manga.author,
                artist = manga.artist,
                status = manga.status,
                sourceName = sourceName,
                isStubSource = isStubSource,
                categories = categories,
                doSearch = doSearch,
            )
        }
    }
}

@Composable
private fun ColumnScope.MangaContentInfo(
    title: String,
    alternativeTitles: List<String>,
    author: String?,
    artist: String?,
    status: Long,
    sourceName: String,
    isStubSource: Boolean,
    categories: List<Category>,
    doSearch: (query: String, global: Boolean) -> Unit,
    onEditAlternativeTitles: (() -> Unit)? = null,
    textAlign: TextAlign? = LocalTextStyle.current.textAlign,
) {
    val context = LocalContext.current
    Text(
        text = title.ifBlank { stringResource(MR.strings.unknown_title) },
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.clickableNoIndication(
            onLongClick = {
                if (title.isNotBlank()) {
                    context.copyToClipboard(
                        title,
                        title,
                    )
                }
            },
            onClick = { if (title.isNotBlank()) doSearch(title, true) },
        ),
        textAlign = textAlign,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (textAlign == TextAlign.Center) Arrangement.Center else Arrangement.Start,
    ) {
        if (alternativeTitles.isNotEmpty()) {
            Text(
                text = alternativeTitles.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickableNoIndication(
                        onLongClick = {
                            context.copyToClipboard(
                                alternativeTitles.joinToString("\n"),
                                alternativeTitles.joinToString("\n"),
                            )
                        },
                        onClick = { onEditAlternativeTitles?.invoke() },
                    ),
                textAlign = textAlign,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else if (onEditAlternativeTitles != null) {
            Text(
                text = "Add alternative titles",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickableNoIndication(
                    onClick = { onEditAlternativeTitles() },
                ),
                textAlign = textAlign,
            )
        }
    }
    Spacer(modifier = Modifier.height(2.dp))
    Row(
        modifier = Modifier.secondaryItemAlpha(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.PersonOutline,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = author?.takeIf { it.isNotBlank() }
                ?: stringResource(MR.strings.unknown_author),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .clickableNoIndication(
                    onLongClick = {
                        if (!author.isNullOrBlank()) {
                            context.copyToClipboard(
                                author,
                                author,
                            )
                        }
                    },
                    onClick = { if (!author.isNullOrBlank()) doSearch(author, true) },
                ),
            textAlign = textAlign,
        )
    }
    if (!artist.isNullOrBlank() && author != artist) {
        Row(
            modifier = Modifier.secondaryItemAlpha(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Brush,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .clickableNoIndication(
                        onLongClick = { context.copyToClipboard(artist, artist) },
                        onClick = { doSearch(artist, true) },
                    ),
                textAlign = textAlign,
            )
        }
    }
    Spacer(modifier = Modifier.height(2.dp))
    Row(
        modifier = Modifier.secondaryItemAlpha(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when (status) {
                SManga.ONGOING.toLong() -> Icons.Outlined.Schedule
                SManga.COMPLETED.toLong() -> Icons.Outlined.DoneAll
                SManga.LICENSED.toLong() -> Icons.Outlined.AttachMoney
                SManga.PUBLISHING_FINISHED.toLong() -> Icons.Outlined.Done
                SManga.CANCELLED.toLong() -> Icons.Outlined.Close
                SManga.ON_HIATUS.toLong() -> Icons.Outlined.Pause
                else -> Icons.Outlined.Block
            },
            contentDescription = null,
            modifier = Modifier
                .padding(end = 4.dp)
                .size(16.dp),
        )
        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
            Text(
                text = when (status) {
                    SManga.ONGOING.toLong() -> stringResource(MR.strings.ongoing)
                    SManga.COMPLETED.toLong() -> stringResource(MR.strings.completed)
                    SManga.LICENSED.toLong() -> stringResource(MR.strings.licensed)
                    SManga.PUBLISHING_FINISHED.toLong() -> stringResource(MR.strings.publishing_finished)
                    SManga.CANCELLED.toLong() -> stringResource(MR.strings.cancelled)
                    SManga.ON_HIATUS.toLong() -> stringResource(MR.strings.on_hiatus)
                    else -> stringResource(MR.strings.unknown)
                },
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
            DotSeparatorText()
            if (isStubSource) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = sourceName,
                modifier = Modifier.clickableNoIndication {
                    doSearch(
                        sourceName,
                        false,
                    )
                },
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        }
    }
    if (categories.isNotEmpty()) {
        Row(
            modifier = Modifier.secondaryItemAlpha(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Label,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(16.dp),
            )
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                Text(
                    text = categories.joinToString(", ") { it.name },
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun descriptionAnnotator(loadImages: Boolean, linkStyle: SpanStyle) = remember(loadImages, linkStyle) {
    markdownAnnotator(
        annotate = { content, child ->
            if (!loadImages && child.type == MarkdownElementTypes.IMAGE) {
                val inlineLink = child.findChildOfType(MarkdownElementTypes.INLINE_LINK)
                val url = inlineLink?.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)
                    ?.getUnescapedTextInNode(content)
                    ?: inlineLink?.findChildOfType(MarkdownElementTypes.AUTOLINK)
                        ?.findChildOfType(MarkdownTokenTypes.AUTOLINK)
                        ?.getUnescapedTextInNode(content)
                    ?: return@markdownAnnotator false
                val textNode = inlineLink?.findChildOfType(MarkdownElementTypes.LINK_TITLE)
                    ?: inlineLink?.findChildOfType(MarkdownElementTypes.LINK_TEXT)
                val altText = textNode?.findChildOfType(MarkdownTokenTypes.TEXT)
                    ?.getUnescapedTextInNode(content).orEmpty()
                withLink(LinkAnnotation.Url(url = url)) {
                    pushStyle(linkStyle)
                    appendInlineContent(MARKDOWN_INLINE_IMAGE_TAG)
                    append(altText)
                    pop()
                }
                return@markdownAnnotator true
            }
            if (child.type in DISALLOWED_MARKDOWN_TYPES) {
                append(content.substring(child.startOffset, child.endOffset))
                return@markdownAnnotator true
            }
            false
        },
        config = markdownAnnotatorConfig(
            eolAsNewLine = true,
        ),
    )
}

@Composable
private fun MangaSummary(
    description: String,
    notes: String,
    expanded: Boolean,
    onEditNotesClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val preferences = remember { Injekt.get<UiPreferences>() }
    val loadImages = remember { preferences.imagesInDescription.get() }
    val animProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        label = "summary",
    )
    var infoHeight by remember { mutableIntStateOf(0) }
    Layout(
        modifier = modifier.clipToBounds(),
        contents = listOf(
            {
                Text(
                    text = if (notes.isBlank()) "\n\n" else "\n\n\n\n\n",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            {
                Column(
                    modifier = Modifier.onSizeChanged { size ->
                        infoHeight = size.height
                    },
                ) {
                    MangaNotesSection(
                        content = notes,
                        expanded = expanded,
                        onEditNotes = onEditNotesClicked,
                    )
                    SelectionContainer {
                        MarkdownRender(
                            content = description,
                            modifier = Modifier.secondaryItemAlpha(),
                            annotator = descriptionAnnotator(
                                loadImages = loadImages,
                                linkStyle = getMarkdownLinkStyle().toSpanStyle(),
                            ),
                            loadImages = loadImages,
                        )
                    }
                }
            },
            {
                val colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                Box(
                    modifier = Modifier.background(Brush.verticalGradient(colors = colors)),
                    contentAlignment = Alignment.Center,
                ) {
                    val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_caret_down)
                    Icon(
                        painter = rememberAnimatedVectorPainter(image, !expanded),
                        contentDescription = stringResource(
                            if (expanded) MR.strings.manga_info_collapse else MR.strings.manga_info_expand,
                        ),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.background(Brush.radialGradient(colors = colors.asReversed())),
                    )
                }
            },
        ),
    ) { (shrunk, actual, scrim), constraints ->
        val shrunkHeight = shrunk.single()
            .measure(constraints)
            .height
        val heightDelta = infoHeight - shrunkHeight
        val scrimHeight = 24.dp.roundToPx()
        val actualPlaceable = actual.single()
            .measure(constraints)
        val scrimPlaceable = scrim.single()
            .measure(Constraints.fixed(width = constraints.maxWidth, height = scrimHeight))
        val currentHeight = shrunkHeight + ((heightDelta + scrimHeight) * animProgress).roundToInt()
        layout(constraints.maxWidth, currentHeight) {
            actualPlaceable.place(0, 0)
            val scrimY = currentHeight - scrimHeight
            scrimPlaceable.place(0, scrimY)
        }
    }
}

private val DefaultTagChipModifier = Modifier.padding(vertical = 4.dp)

@Composable
private fun TagsChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        SuggestionChip(
            modifier = modifier,
            onClick = onClick,
            label = { Text(text = text, style = MaterialTheme.typography.bodySmall) },
        )
    }
}

@Composable
private fun RowScope.MangaActionButton(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        onLongClick = onLongClick,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                color = color,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
