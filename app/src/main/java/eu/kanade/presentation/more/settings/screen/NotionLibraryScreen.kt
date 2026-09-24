package eu.kanade.presentation.more.settings.screen

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.data.track.notion.NotionTracker
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.system.openInBrowser
import kotlinx.coroutines.Dispatchers
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
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
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
)

private const val FILTER_ALL = "All"
private const val NOTION_API = "https://api.notion.com/v1"

/**
 * Full-screen list of everything in the Notion tracking database.
 * Reuses the login (database id + secret) already stored by [NotionTracker].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotionLibraryDialogContent(
    tracker: NotionTracker,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current

    var entries by remember { mutableStateOf<List<NotionLibraryEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf(FILTER_ALL) }
    var statusFilter by remember { mutableStateOf(FILTER_ALL) }

    LaunchedEffect(refreshKey) {
        loading = true
        errorMessage = null
        try {
            entries = withContext(Dispatchers.IO) { fetchNotionLibrary(tracker) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            errorMessage = e.message ?: "Couldn't load your Notion library."
        }
        loading = false
    }

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
                                    text = "${filtered.size} of ${entries.size} entries",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { refreshKey++ }, enabled = !loading) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Refresh",
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
                    if (loading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text("Search your library") },
                        singleLine = true,
                    )
                    NotionFilterRow(options = types, selected = typeFilter, onSelect = { typeFilter = it })
                    NotionFilterRow(options = statuses, selected = statusFilter, onSelect = { statusFilter = it })

                    val err = errorMessage
                    when {
                        err != null && entries.isEmpty() -> NotionCenterMessage(
                            text = err,
                            actionLabel = "Retry",
                            onAction = { refreshKey++ },
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
                                    NotionLibraryRow(
                                        entry = entry,
                                        onClick = { context.openInBrowser(entry.pageUrl) },
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
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
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

@Composable
private fun NotionLibraryRow(
    entry: NotionLibraryEntry,
    onClick: () -> Unit,
) {
    val coverModifier = Modifier
        .width(46.dp)
        .height(66.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (entry.totalChapters > 0) {
                    LinearProgressIndicator(
                        progress = { (entry.chapter / entry.totalChapters).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp),
                    )
                    Text(
                        text = "${entry.chapter.clean()} / ${entry.totalChapters.clean()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "Ch. ${entry.chapter.clean()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

/** Pulls every page of the database (most recently edited first). Runs blocking parsing, call on IO. */
private suspend fun fetchNotionLibrary(tracker: NotionTracker): List<NotionLibraryEntry> {
    val databaseId = tracker.getUsername().replace("-", "")
    val secret = tracker.getPassword()
    if (databaseId.isBlank() || secret.isBlank()) {
        error("Log in to Notion first (Settings, Tracking).")
    }

    val client = Injekt.get<NetworkHelper>().client
    val headers = Headers.Builder()
        .add("Authorization", "Bearer $secret")
        .add("Notion-Version", "2022-06-28")
        .add("Content-Type", "application/json")
        .build()

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
            client.newCall(POST("$NOTION_API/databases/$databaseId/query", headers, body)).awaitSuccess()
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

    return entries
}

private fun parseNotionPage(page: JsonObject): NotionLibraryEntry? {
    val pageId = page["id"]?.jsonPrimitive?.contentOrNull ?: return null
    val props = page["properties"] as? JsonObject ?: return null

    val title = props.values
        .mapNotNull { it as? JsonObject }
        .firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "title" }
        ?.let { titleProp ->
            (titleProp["title"] as? JsonArray)?.joinToString("") { part ->
                (part as? JsonObject)?.get("plain_text")?.jsonPrimitive?.contentOrNull.orEmpty()
            }
        }
        .orEmpty()

    // Prefer the Cover column, fall back to the page banner the tracker sets.
    val bannerCover = (page["cover"] as? JsonObject)?.let { cover ->
        val kind = cover["type"]?.jsonPrimitive?.contentOrNull ?: "external"
        (cover[kind] as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull
    }
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
    )
}

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonObject)?.get("number")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()

private fun JsonObject.selectName(name: String): String? {
    val prop = this[name] as? JsonObject ?: return null
    val option = (prop["select"] as? JsonObject) ?: (prop["status"] as? JsonObject)
    return option?.get("name")?.jsonPrimitive?.contentOrNull
}

private fun JsonObject.urlValue(name: String): String? =
    (this[name] as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull
