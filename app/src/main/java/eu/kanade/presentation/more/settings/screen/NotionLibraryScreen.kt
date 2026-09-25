package eu.kanade.presentation.more.settings.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cafe.adriel.voyager.navigator.LocalNavigator
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.data.track.notion.NotionTracker
import eu.kanade.tachiyomi.ui.browse.migration.search.RestoreSearchScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.system.openInBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.coroutines.cancellation.CancellationException

/** One row of the user's Notion tracking database. */
data class NotionLibraryEntry(
    val pageId: String,
    val title: String,
    val type: String,
    val status: String,
    val chapter: Double,
    val totalChapters: Double,
    val score: Double,
    val coverUrl: String?,
    val pageUrl: String,
    val source: String,
    val description: String,
    val chapterName: String,
    val author: String,
)

/**
 * Which layout the Notion library uses. Stored as a plain string preference so it can be
 * pointed at the app-wide UI style later by changing only [preference].
 */
object NotionLibraryStyle {
    const val CLASSIC = "Classic"
    const val MODERN = "Modern"

    fun preference() = Injekt.get<PreferenceStore>().getString("notion_library_style", CLASSIC)
}

private const val FILTER_ALL = "All"
private const val NOTION_API = "https://api.notion.com/v1"
private const val SOURCE_PROPERTY = "Source"
private const val DESCRIPTION_PROPERTY = "Description"
private const val DESCRIPTION_LIMIT = 1900

/**
 * Full-screen view of everything in the Notion tracking database, in a classic or modern layout.
 * The refresh button reloads the list and then copies Source and Description from the local
 * library into Notion for every tracked entry that is missing them (or has them out of date).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotionLibraryDialogContent(
    tracker: NotionTracker,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val stylePref = remember { NotionLibraryStyle.preference() }
    val style by stylePref.collectAsState()
    val modern = style == NotionLibraryStyle.MODERN

    var entries by remember { mutableStateOf<List<NotionLibraryEntry>>(emptyList()) }
    var dbTitle by remember { mutableStateOf("Notion library") }
    var bannerUrl by remember { mutableStateOf<String?>(null) }
    var bannerNote by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var syncing by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf(FILTER_ALL) }
    var statusFilter by remember { mutableStateOf(FILTER_ALL) }

    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var uploadingCover by remember { mutableStateOf(false) }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                uploadingCover = true
                syncStatus = "Uploading cover..."
                try {
                    val newCover = withContext(Dispatchers.IO) {
                        uploadDatabaseCover(context, tracker, uri)
                        fetchDatabaseCoverUrl(tracker)
                    }
                    bannerUrl = newCover
                    bannerNote = if (newCover == null) "Cover uploaded, but Notion didn't return it yet. Tap refresh." else null
                    syncStatus = "Cover updated"
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    syncStatus = "Cover upload failed: ${e.message}"
                }
                uploadingCover = false
            }
        }
    }

    LaunchedEffect(refreshKey) {
        loading = true
        errorMessage = null
        syncStatus = null
        try {
            val data = withContext(Dispatchers.IO) { fetchNotionLibrary(tracker) }
            entries = data.entries
            dbTitle = data.title
            bannerUrl = data.bannerUrl
            bannerNote = data.bannerNote
            loading = false

            // Only a manual refresh writes to Notion; opening the screen never does.
            if (refreshKey > 0) {
                syncing = true
                syncStatus = "Syncing library info..."
                val result = withContext(Dispatchers.IO) {
                    syncNotionMetadata(tracker, data) { done, total ->
                        syncStatus = "Syncing $done/$total"
                    }
                }
                if (result.updated.isNotEmpty()) {
                    entries = entries.map { result.updated[it.pageId] ?: it }
                }
                syncStatus = when {
                    result.failed > 0 -> "Updated ${result.updated.size}, ${result.failed} failed"
                    result.updated.isEmpty() -> "Everything is up to date"
                    else -> "Updated ${result.updated.size} entries"
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            errorMessage = e.message ?: "Couldn't load your Notion library."
            syncStatus = null
        }
        loading = false
        syncing = false
    }

    val busy = loading || syncing || uploadingCover
    val types = remember(entries) {
        listOf(FILTER_ALL) + entries.map { it.type }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val statuses = remember { listOf(FILTER_ALL) + NotionTracker.STATUS_NAMES }

    val filtered = remember(entries, query, typeFilter, statusFilter) {
        val q = query.trim()
        entries.filter { entry ->
            (typeFilter == FILTER_ALL || entry.type == typeFilter) &&
                (statusFilter == FILTER_ALL || entry.status == statusFilter) &&
                (q.isEmpty() || entry.title.contains(q, ignoreCase = true))
        }
    }
    val subtitle = syncStatus ?: "${filtered.size} of ${entries.size} entries"

    // Restore: close this screen, then open a search for the title so you can pick the right match.
    val restoreFor: (NotionLibraryEntry) -> (() -> Unit)? = { entry ->
        navigator?.let { nav ->
            {
                onDismissRequest()
                nav.push(
                    RestoreSearchScreen(
                        pageId = entry.pageId,
                        title = entry.title,
                        author = entry.author,
                        novel = entry.type.equals("Novel", ignoreCase = true) ||
                            entry.type.equals("Light Novel", ignoreCase = true) ||
                            entry.type.equals("Book", ignoreCase = true),
                    ),
                )
            }
        }
    }
    val err = errorMessage

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (modern) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        item(key = "header") {
                            NotionModernHeader(
                                title = dbTitle,
                                subtitle = subtitle,
                                bannerUrl = bannerUrl,
                                bannerNote = bannerNote,
                            )
                        }
                        item(key = "stats") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(statuses, key = { it }) { status ->
                                    val selected = status == statusFilter
                                    val count = if (status == FILTER_ALL) {
                                        entries.size
                                    } else {
                                        entries.count { it.status == status }
                                    }
                                    val content = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                    Column(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                if (selected) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                },
                                            )
                                            .clickable { statusFilter = status }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                    ) {
                                        Text(
                                            text = count.toString(),
                                            style = MaterialTheme.typography.titleLarge,
                                            color = content,
                                        )
                                        Text(
                                            text = status,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = content,
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "search") {
                            NotionSearchField(
                                query = query,
                                onQueryChange = { query = it },
                                shape = RoundedCornerShape(50),
                            )
                        }
                        if (types.size > 2) {
                            item(key = "types") {
                                NotionFilterRow(options = types, selected = typeFilter, onSelect = { typeFilter = it })
                            }
                        }
                        val centered = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp)
                        when {
                            err != null && entries.isEmpty() -> item(key = "message") {
                                NotionCenterMessage(err, "Retry", { refreshKey++ }, centered)
                            }
                            loading && entries.isEmpty() -> item(key = "loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                            filtered.isEmpty() -> item(key = "empty") {
                                NotionCenterMessage("Nothing here yet.", null, {}, centered)
                            }
                            else -> items(filtered, key = { it.pageId }) { entry ->
                                NotionEntryRow(
                                    entry = entry,
                                    modern = true,
                                    onOpen = { context.openInBrowser(entry.pageUrl) },
                                    onRestore = restoreFor(entry),
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NotionCircleButton(onClick = onDismissRequest) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NotionCircleButton(onClick = { refreshKey++ }, enabled = !busy) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Refresh",
                                    tint = Color.White,
                                )
                            }
                            Box {
                                NotionCircleButton(onClick = { menuOpen = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "More",
                                        tint = Color.White,
                                    )
                                }
                                NotionOverflowMenu(
                                    expanded = menuOpen,
                                    onDismiss = { menuOpen = false },
                                    enabled = !busy,
                                    styleLabel = "Switch to Classic",
                                    onStyle = { stylePref.set(NotionLibraryStyle.CLASSIC) },
                                    onChangeCover = { coverPicker.launch("image/*") },
                                )
                            }
                        }
                    }
                }
            } else {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            navigationIcon = {
                                IconButton(onClick = onDismissRequest) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                    )
                                }
                            },
                            title = {
                                Column {
                                    Text(
                                        text = "Notion library",
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = { refreshKey++ }, enabled = !busy) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = "Refresh",
                                    )
                                }
                                Box {
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(
                                            imageVector = Icons.Filled.MoreVert,
                                            contentDescription = "More",
                                        )
                                    }
                                    NotionOverflowMenu(
                                        expanded = menuOpen,
                                        onDismiss = { menuOpen = false },
                                        enabled = !busy,
                                        styleLabel = "Switch to Modern",
                                        onStyle = { stylePref.set(NotionLibraryStyle.MODERN) },
                                        onChangeCover = { coverPicker.launch("image/*") },
                                    )
                                }
                            },
                        )
                    },
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        if (busy) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        NotionSearchField(
                            query = query,
                            onQueryChange = { query = it },
                            shape = RoundedCornerShape(4.dp),
                        )
                        NotionFilterRow(options = types, selected = typeFilter, onSelect = { typeFilter = it })
                        NotionFilterRow(options = statuses, selected = statusFilter, onSelect = { statusFilter = it })

                        when {
                            err != null && entries.isEmpty() -> NotionCenterMessage(
                                text = err,
                                actionLabel = "Retry",
                                onAction = { refreshKey++ },
                                modifier = Modifier.fillMaxSize(),
                            )
                            loading && entries.isEmpty() -> Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                            filtered.isEmpty() -> NotionCenterMessage(
                                text = "Nothing here yet.",
                                actionLabel = null,
                                onAction = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                            else -> {
                                if (err != null) {
                                    Text(
                                        text = err,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                    )
                                }
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(vertical = 8.dp),
                                ) {
                                    items(filtered, key = { it.pageId }) { entry ->
                                        NotionEntryRow(
                                            entry = entry,
                                            modern = false,
                                            onOpen = { context.openInBrowser(entry.pageUrl) },
                                            onRestore = restoreFor(entry),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotionModernHeader(
    title: String,
    subtitle: String,
    bannerUrl: String?,
    bannerNote: String?,
) {
    var imageFailed by remember(bannerUrl) { mutableStateOf(false) }
    val hint = when {
        imageFailed -> "Cover found, but the image failed to load"
        bannerUrl == null -> bannerNote
        else -> null
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp),
    ) {
        if (bannerUrl != null) {
            AsyncImage(
                model = bannerUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { imageFailed = true },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Transparent,
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun NotionOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    enabled: Boolean,
    styleLabel: String,
    onStyle: () -> Unit,
    onChangeCover: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Change cover") },
            enabled = enabled,
            onClick = {
                onDismiss()
                onChangeCover()
            },
        )
        DropdownMenuItem(
            text = { Text(styleLabel) },
            onClick = {
                onDismiss()
                onStyle()
            },
        )
    }
}

@Composable
private fun NotionCircleButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = if (enabled) 0.45f else 0.25f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun NotionSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    shape: Shape,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        placeholder = { Text("Search your library") },
        singleLine = true,
        shape = shape,
    )
}

@Composable
private fun NotionFilterRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options, key = { it }) { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option) },
            )
        }
    }
}

@Composable
private fun NotionCenterMessage(
    text: String,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

/** Tap a row to expand its description; "Open in Notion" opens the page. */
@Composable
private fun NotionEntryRow(
    entry: NotionLibraryEntry,
    modern: Boolean,
    onOpen: () -> Unit,
    onRestore: (() -> Unit)?,
) {
    var expanded by remember(entry.pageId) { mutableStateOf(false) }

    val coverWidth = if (modern) 52.dp else 46.dp
    val coverHeight = if (modern) 74.dp else 66.dp
    val coverShape = RoundedCornerShape(if (modern) 10.dp else 6.dp)
    val coverModifier = Modifier
        .width(coverWidth)
        .height(coverHeight)
        .clip(coverShape)
        .background(MaterialTheme.colorScheme.surfaceVariant)

    val base = if (modern) {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    } else {
        Modifier.fillMaxWidth()
    }

    Column(modifier = base.clickable { expanded = !expanded }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (modern) 10.dp else 16.dp, vertical = if (modern) 10.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (entry.coverUrl != null) {
                AsyncImage(
                    model = entry.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = coverModifier,
                )
            } else {
                Box(modifier = coverModifier)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (entry.status.isNotBlank()) NotionStatusPill(entry.status)
                    if (entry.type.isNotBlank()) {
                        Text(
                            text = entry.type,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (entry.source.isNotBlank()) {
                    Text(
                        text = "via ${entry.source}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (entry.totalChapters > 0) {
                    val fraction = (entry.chapter / entry.totalChapters).toFloat().coerceIn(0f, 1f)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${entry.chapter.clean()} of ${entry.totalChapters.clean()} chapters read",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${(fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    NotionProgressBar(
                        progress = fraction,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    Text(
                        text = "Ch. ${entry.chapter.clean()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (entry.chapterName.isNotBlank()) {
                    Text(
                        text = entry.chapterName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            if (entry.score > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = " ${entry.score.clean()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (expanded) {
            Column(
                modifier = Modifier.padding(
                    start = if (modern) 12.dp else 16.dp,
                    end = if (modern) 12.dp else 16.dp,
                    bottom = 8.dp,
                ),
            ) {
                Text(
                    text = entry.description.ifBlank {
                        "No description saved yet. Tap refresh to copy it from your library."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entry.author.isNotBlank()) {
                    Text(
                        text = "Author: ${entry.author}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row {
                    TextButton(onClick = onOpen) {
                        Text("Open in Notion")
                    }
                    if (onRestore != null) {
                        TextButton(onClick = onRestore) {
                            Text("Restore to library")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Progress bar in the same style as the manga detail screen: a rounded filled part, a small gap,
 * then the remaining track with a dot at its end.
 */
@Composable
private fun NotionProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val fillColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp),
    ) {
        val h = size.height
        val w = size.width
        val gap = 4.dp.toPx()
        val radius = CornerRadius(h / 2, h / 2)
        val p = progress.coerceIn(0f, 1f)

        when {
            p <= 0f -> {
                drawRoundRect(color = trackColor, size = Size(w, h), cornerRadius = radius)
                drawCircle(color = fillColor, radius = h * 0.28f, center = Offset(w - h / 2, h / 2))
            }
            p >= 1f -> drawRoundRect(color = fillColor, size = Size(w, h), cornerRadius = radius)
            else -> {
                val fillEnd = max(w * p - gap / 2, h)
                val trackStart = max(w * p + gap / 2, fillEnd + gap)
                drawRoundRect(color = fillColor, size = Size(fillEnd, h), cornerRadius = radius)
                if (trackStart < w) {
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(trackStart, 0f),
                        size = Size(w - trackStart, h),
                        cornerRadius = radius,
                    )
                    drawCircle(color = fillColor, radius = h * 0.28f, center = Offset(w - h / 2, h / 2))
                }
            }
        }
    }
}

@Composable
private fun NotionStatusPill(status: String) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (status) {
        "Reading" -> scheme.primaryContainer to scheme.onPrimaryContainer
        "Completed" -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        "On Hold" -> scheme.secondaryContainer to scheme.onSecondaryContainer
        "Dropped" -> scheme.errorContainer to scheme.onErrorContainer
        else -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private fun Double.clean(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()

// ---------------------------------------------------------------------------------------------
// Notion API
// ---------------------------------------------------------------------------------------------

private class NotionConn(
    val databaseId: String,
    val client: OkHttpClient,
    val headers: Headers,
    val uploadHeaders: Headers,
)

private class NotionLibraryData(
    val title: String,
    val bannerUrl: String?,
    val bannerNote: String?,
    val propertyTypes: Map<String, String>,
    val entries: List<NotionLibraryEntry>,
)

private class NotionSyncResult(
    val updated: Map<String, NotionLibraryEntry>,
    val failed: Int,
)

private fun notionConn(tracker: NotionTracker): NotionConn {
    val databaseId = tracker.getUsername().replace("-", "")
    val secret = tracker.getPassword()
    if (databaseId.isBlank() || secret.isBlank()) {
        error("Log in to Notion first (Settings, Tracking).")
    }
    val headers = Headers.Builder()
        .add("Authorization", "Bearer $secret")
        .add("Notion-Version", "2022-06-28")
        .add("Content-Type", "application/json")
        .build()
    val uploadHeaders = Headers.Builder()
        .add("Authorization", "Bearer $secret")
        .add("Notion-Version", "2022-06-28")
        .build()
    return NotionConn(databaseId, Injekt.get<NetworkHelper>().client, headers, uploadHeaders)
}

private suspend fun notionPatch(conn: NotionConn, url: String, json: JsonObject) {
    val body = json.toString().toRequestBody("application/json".toMediaType())
    val request = Request.Builder().url(url).headers(conn.headers).patch(body).build()
    conn.client.newCall(request).awaitSuccess().close()
}

/** Loads the database info (title, banner, columns) and every page. Blocking parse, call on IO. */
private suspend fun fetchNotionLibrary(tracker: NotionTracker): NotionLibraryData {
    val conn = notionConn(tracker)

    val database = try {
        conn.client.newCall(GET("$NOTION_API/databases/${conn.databaseId}", conn.headers)).awaitSuccess()
            .use { Json.parseToJsonElement(it.body.string()).jsonObject }
    } catch (e: HttpException) {
        error("Notion returned ${e.code}. Check that the database is still shared with your integration.")
    }
    val title = (database["title"] as? JsonArray)?.plainText().orEmpty()
        .ifBlank { "Notion library" }
    var bannerUrl = (database["cover"] as? JsonObject)?.let { coverUrlOf(it) }
    var bannerNote: String? = null
    if (bannerUrl == null) {
        bannerNote = "This database has no cover set"
        val parentPageId = (database["parent"] as? JsonObject)?.get("page_id")?.jsonPrimitive?.contentOrNull
        if (parentPageId != null) {
            bannerUrl = fetchPageCover(conn, parentPageId)
            bannerNote = if (bannerUrl != null) null else "No cover on the database or its parent page"
        }
    }
    val propertyTypes = (database["properties"] as? JsonObject)
        ?.mapValues { (_, value) ->
            (value as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull.orEmpty()
        }
        .orEmpty()

    val entries = mutableListOf<NotionLibraryEntry>()
    var cursor: String? = null
    do {
        val body = buildJsonObject {
            put("page_size", 100)
            putJsonArray("sorts") {
                add(
                    buildJsonObject {
                        put("timestamp", "last_edited_time")
                        put("direction", "descending")
                    },
                )
            }
            cursor?.let { put("start_cursor", it) }
        }.toString().toRequestBody("application/json".toMediaType())

        val response = try {
            conn.client.newCall(POST("$NOTION_API/databases/${conn.databaseId}/query", conn.headers, body))
                .awaitSuccess()
        } catch (e: HttpException) {
            error("Notion returned ${e.code}. Check that the database is still shared with your integration.")
        }
        val root = response.use { Json.parseToJsonElement(it.body.string()).jsonObject }

        root["results"]?.jsonArray?.forEach { item ->
            parseNotionPage(item.jsonObject)?.let { entries += it }
        }
        val hasMore = root["has_more"]?.jsonPrimitive?.booleanOrNull == true
        cursor = root["next_cursor"]?.jsonPrimitive?.contentOrNull
    } while (hasMore && !cursor.isNullOrBlank())

    return NotionLibraryData(title, bannerUrl, bannerNote, propertyTypes, entries)
}

private suspend fun fetchPageCover(conn: NotionConn, pageId: String): String? {
    return try {
        val page = conn.client.newCall(GET("$NOTION_API/pages/$pageId", conn.headers)).awaitSuccess()
            .use { Json.parseToJsonElement(it.body.string()).jsonObject }
        (page["cover"] as? JsonObject)?.let { coverUrlOf(it) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null
    }
}

private fun coverUrlOf(cover: JsonObject): String? {
    val kind = cover["type"]?.jsonPrimitive?.contentOrNull ?: "external"
    return (cover[kind] as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull
        ?.takeIf { it.isNotBlank() }
}

private fun parseNotionPage(page: JsonObject): NotionLibraryEntry? {
    val pageId = page["id"]?.jsonPrimitive?.contentOrNull ?: return null
    val props = page["properties"] as? JsonObject ?: return null

    val title = props.values
        .mapNotNull { it as? JsonObject }
        .firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "title" }
        ?.let { titleProp -> (titleProp["title"] as? JsonArray)?.plainText() }
        .orEmpty()

    // Prefer the Cover column, fall back to the page banner the tracker sets.
    val bannerCover = (page["cover"] as? JsonObject)?.let { coverUrlOf(it) }
    val coverUrl = (props.urlValue(NotionTracker.COVER_PROPERTY) ?: bannerCover)
        ?.takeIf { it.isNotBlank() }

    return NotionLibraryEntry(
        pageId = pageId,
        title = title,
        type = props.selectName(NotionTracker.TYPE_PROPERTY).orEmpty(),
        status = props.selectName(NotionTracker.STATUS_PROPERTY).orEmpty(),
        chapter = props.number(NotionTracker.CHAPTER_PROPERTY) ?: 0.0,
        totalChapters = props.number(NotionTracker.TOTAL_CHAPTERS_PROPERTY) ?: 0.0,
        score = props.number(NotionTracker.SCORE_PROPERTY) ?: 0.0,
        coverUrl = coverUrl,
        pageUrl = page["url"]?.jsonPrimitive?.contentOrNull
            ?: "https://notion.so/${pageId.replace("-", "")}",
        source = props.textValue(SOURCE_PROPERTY).orEmpty(),
        description = props.textValue(DESCRIPTION_PROPERTY).orEmpty(),
        chapterName = props.textValue(NotionTracker.CHAPTER_NAME_PROPERTY).orEmpty(),
        author = props.textValue(NotionTracker.AUTHOR_PROPERTY).orEmpty(),
    )
}

private fun JsonArray.plainText(): String = joinToString("") { part ->
    (part as? JsonObject)?.get("plain_text")?.jsonPrimitive?.contentOrNull.orEmpty()
}

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonObject)?.get("number")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()

private fun JsonObject.selectName(name: String): String? {
    val prop = this[name] as? JsonObject ?: return null
    val option = (prop["select"] as? JsonObject) ?: (prop["status"] as? JsonObject)
    return option?.get("name")?.jsonPrimitive?.contentOrNull
}

/** Reads a select or rich_text column as plain text. */
private fun JsonObject.textValue(name: String): String? {
    val prop = this[name] as? JsonObject ?: return null
    val select = prop["select"] as? JsonObject
    if (select != null) return select["name"]?.jsonPrimitive?.contentOrNull
    return (prop["rich_text"] as? JsonArray)?.plainText()
}

private fun JsonObject.urlValue(name: String): String? =
    (this[name] as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull

// ---------------------------------------------------------------------------------------------
// Library -> Notion sync (Source + Description)
// ---------------------------------------------------------------------------------------------

private class LocalMeta(val source: String, val description: String, val mangaId: Long, val author: String)

private class PendingUpdate(
    val entry: NotionLibraryEntry,
    val props: JsonObject,
    val newSource: String?,
    val newDescription: String?,
    val newChapterName: String?,
    val newAuthor: String?,
)

/** Name of the chapter at [notionChapter], else the highest chapter marked read locally. */
private suspend fun localChapterName(
    getChapters: GetChaptersByMangaId,
    mangaId: Long,
    notionChapter: Double,
): String? {
    return runCatching {
        val chapters = getChapters.await(mangaId)
        val match = chapters.firstOrNull { notionChapter > 0 && it.chapterNumber == notionChapter }
            ?: chapters.filter { it.read }.maxByOrNull { it.chapterNumber }
        match?.name?.trim()?.take(200)?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

private fun pageIdFromUrl(url: String): String? {
    if (url.isBlank()) return null
    return url.substringAfterLast("/").substringBefore("?").replace("-", "").ifBlank { null }
}

/** Source names become Notion select options, which cannot contain commas and cap at 100 chars. */
private fun sanitizeSelect(name: String): String = name.replace(",", " ").trim().take(100)

private fun textPayload(type: String, value: String): JsonObject? = when (type) {
    "select" -> buildJsonObject {
        putJsonObject("select") { put("name", value) }
    }
    "rich_text" -> buildJsonObject {
        putJsonArray("rich_text") {
            add(
                buildJsonObject {
                    putJsonObject("text") { put("content", value) }
                },
            )
        }
    }
    else -> null
}

/**
 * Copies Source and Description from the local library into Notion for tracked entries.
 * Matches by the tracked page id first, then by exact (case-insensitive) title.
 * Only writes values that are missing or different, and never blanks an existing value.
 */
private suspend fun syncNotionMetadata(
    tracker: NotionTracker,
    data: NotionLibraryData,
    onProgress: (done: Int, total: Int) -> Unit,
): NotionSyncResult {
    val conn = notionConn(tracker)

    // 1) Make sure the two columns exist.
    val types = data.propertyTypes
    val missing = buildJsonObject {
        if (SOURCE_PROPERTY !in types) {
            putJsonObject(SOURCE_PROPERTY) { putJsonObject("select") {} }
        }
        if (DESCRIPTION_PROPERTY !in types) {
            putJsonObject(DESCRIPTION_PROPERTY) { putJsonObject("rich_text") {} }
        }
        if (NotionTracker.CHAPTER_NAME_PROPERTY !in types) {
            putJsonObject(NotionTracker.CHAPTER_NAME_PROPERTY) { putJsonObject("rich_text") {} }
        }
        if (NotionTracker.AUTHOR_PROPERTY !in types) {
            putJsonObject(NotionTracker.AUTHOR_PROPERTY) { putJsonObject("rich_text") {} }
        }
    }
    if (missing.isNotEmpty()) {
        notionPatch(
            conn,
            "$NOTION_API/databases/${conn.databaseId}",
            buildJsonObject { put("properties", missing) },
        )
    }

    // The progress bar column is a formula; added on its own so a problem there can't block the rest.
    if (NotionTracker.PROGRESS_PROPERTY !in types) {
        try {
            notionPatch(
                conn,
                "$NOTION_API/databases/${conn.databaseId}",
                buildJsonObject {
                    putJsonObject("properties") {
                        putJsonObject(NotionTracker.PROGRESS_PROPERTY) {
                            putJsonObject("formula") {
                                put("expression", NotionTracker.PROGRESS_FORMULA)
                            }
                        }
                    }
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Best effort only.
        }
    }

    val sourceType = types[SOURCE_PROPERTY] ?: "select"
    val descriptionType = types[DESCRIPTION_PROPERTY] ?: "rich_text"
    val chapterNameType = types[NotionTracker.CHAPTER_NAME_PROPERTY] ?: "rich_text"
    val authorType = types[NotionTracker.AUTHOR_PROPERTY] ?: "rich_text"

    // 2) Read source + description for every library item that has a Notion track.
    val byPageId = mutableMapOf<String, LocalMeta>()
    val byTitle = mutableMapOf<String, LocalMeta>()
    val getFavorites = Injekt.get<GetFavorites>()
    val getTracks = Injekt.get<GetTracks>()
    val getChapters = Injekt.get<GetChaptersByMangaId>()
    val sourceManager = Injekt.get<SourceManager>()
    getFavorites.await().forEach { manga ->
        val meta = LocalMeta(
            source = sourceManager.getOrStub(manga.source).name,
            description = manga.description.orEmpty(),
            mangaId = manga.id,
            author = manga.author.orEmpty(),
        )
        byTitle[manga.title.trim().lowercase()] = meta
        getTracks.await(manga.id)
            .filter { it.trackerId == tracker.id }
            .forEach { track -> pageIdFromUrl(track.remoteUrl)?.let { byPageId[it] = meta } }
    }

    // 3) Work out which pages actually need a write.
    val pending = mutableListOf<PendingUpdate>()
    data.entries.forEach { entry ->
        val local = byPageId[entry.pageId.replace("-", "")]
            ?: byTitle[entry.title.trim().lowercase()]
            ?: return@forEach

        val wantSource = sanitizeSelect(local.source)
        val wantDescription = local.description.replace("\r", "").trim().take(DESCRIPTION_LIMIT)
        val sourcePayload = if (wantSource.isNotBlank() && wantSource != entry.source.trim()) {
            textPayload(sourceType, wantSource)
        } else {
            null
        }
        val descriptionPayload = if (wantDescription.isNotBlank() && wantDescription != entry.description.trim()) {
            textPayload(descriptionType, wantDescription)
        } else {
            null
        }
        val wantChapterName = localChapterName(getChapters, local.mangaId, entry.chapter)
        val chapterNamePayload = if (wantChapterName != null && wantChapterName != entry.chapterName.trim()) {
            textPayload(chapterNameType, wantChapterName)
        } else {
            null
        }
        val wantAuthor = local.author.trim().take(200)
        val authorPayload = if (wantAuthor.isNotBlank() && wantAuthor != entry.author.trim()) {
            textPayload(authorType, wantAuthor)
        } else {
            null
        }
        if (sourcePayload == null && descriptionPayload == null && chapterNamePayload == null && authorPayload == null) {
            return@forEach
        }

        val props = buildJsonObject {
            if (sourcePayload != null) put(SOURCE_PROPERTY, sourcePayload)
            if (descriptionPayload != null) put(DESCRIPTION_PROPERTY, descriptionPayload)
            if (chapterNamePayload != null) put(NotionTracker.CHAPTER_NAME_PROPERTY, chapterNamePayload)
            if (authorPayload != null) put(NotionTracker.AUTHOR_PROPERTY, authorPayload)
        }
        pending += PendingUpdate(
            entry = entry,
            props = props,
            newSource = if (sourcePayload != null) wantSource else null,
            newDescription = if (descriptionPayload != null) wantDescription else null,
            newChapterName = if (chapterNamePayload != null) wantChapterName else null,
            newAuthor = if (authorPayload != null) wantAuthor else null,
        )
    }

    // 4) Write them, gently: Notion allows roughly 3 requests per second.
    val updated = mutableMapOf<String, NotionLibraryEntry>()
    var failed = 0
    pending.forEachIndexed { index, item ->
        onProgress(index, pending.size)
        var ok = false
        for (attempt in 0..1) {
            try {
                notionPatch(
                    conn,
                    "$NOTION_API/pages/${item.entry.pageId}",
                    buildJsonObject { put("properties", item.props) },
                )
                ok = true
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (e.code == 429 && attempt == 0) delay(1500) else break
            } catch (e: Throwable) {
                break
            }
        }
        if (ok) {
            updated[item.entry.pageId] = item.entry.copy(
                source = item.newSource ?: item.entry.source,
                description = item.newDescription ?: item.entry.description,
                chapterName = item.newChapterName ?: item.entry.chapterName,
                author = item.newAuthor ?: item.entry.author,
            )
        } else {
            failed++
        }
        onProgress(index + 1, pending.size)
        delay(350)
    }

    return NotionSyncResult(updated = updated, failed = failed)
}

// ---------------------------------------------------------------------------------------------
// Change the database cover (Notion file upload API)
// ---------------------------------------------------------------------------------------------

private const val MAX_COVER_BYTES = 4_500_000

private suspend fun fetchDatabaseCoverUrl(tracker: NotionTracker): String? {
    val conn = notionConn(tracker)
    val database = conn.client.newCall(GET("$NOTION_API/databases/${conn.databaseId}", conn.headers))
        .awaitSuccess()
        .use { Json.parseToJsonElement(it.body.string()).jsonObject }
    return (database["cover"] as? JsonObject)?.let { coverUrlOf(it) }
}

/** Downscales and re-encodes as JPEG so the file fits Notion's upload size limit. */
private fun shrinkImage(bytes: ByteArray): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 2400) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: error("Couldn't decode that image.")

    var quality = 88
    var out = ByteArray(0)
    do {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        out = stream.toByteArray()
        quality -= 10
    } while (out.size > MAX_COVER_BYTES && quality > 30)
    bitmap.recycle()
    return out
}

/**
 * Uploads the picked image to Notion and sets it as the database cover.
 * Steps: create a file upload, send the bytes, then attach it to the database.
 */
private suspend fun uploadDatabaseCover(context: Context, tracker: NotionTracker, uri: Uri) {
    val conn = notionConn(tracker)

    var mime = context.contentResolver.getType(uri) ?: "image/jpeg"
    var bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("Couldn't read that image.")
    if (bytes.size > MAX_COVER_BYTES) {
        bytes = shrinkImage(bytes)
        mime = "image/jpeg"
    }
    val extension = when (mime) {
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        else -> "jpg"
    }
    val filename = "koware-cover.$extension"

    try {
        // 1) Create the upload slot.
        val createBody = buildJsonObject {
            put("filename", filename)
            put("content_type", mime)
        }.toString().toRequestBody("application/json".toMediaType())
        val created = conn.client.newCall(POST("$NOTION_API/file_uploads", conn.headers, createBody))
            .awaitSuccess()
            .use { Json.parseToJsonElement(it.body.string()).jsonObject }
        val uploadId = created["id"]?.jsonPrimitive?.contentOrNull
            ?: error("Notion didn't return an upload id.")

        // 2) Send the bytes.
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", filename, bytes.toRequestBody(mime.toMediaType()))
            .build()
        conn.client.newCall(POST("$NOTION_API/file_uploads/$uploadId/send", conn.uploadHeaders, multipart))
            .awaitSuccess()
            .close()

        // 3) Attach it as the database cover.
        notionPatch(
            conn,
            "$NOTION_API/databases/${conn.databaseId}",
            buildJsonObject {
                putJsonObject("cover") {
                    put("type", "file_upload")
                    putJsonObject("file_upload") { put("id", uploadId) }
                }
            },
        )
    } catch (e: HttpException) {
        error(
            "Notion returned ${e.code}. Make sure the integration has permission to update content " +
                "and the image is a normal jpg, png, gif or webp.",
        )
    }
}
