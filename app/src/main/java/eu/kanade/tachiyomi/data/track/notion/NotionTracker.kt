// FILE: app/src/main/java/eu/kanade/tachiyomi/data/track/notion/NotionTracker.kt

package eu.kanade.tachiyomi.data.track.notion

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R // TODO: add R.drawable.ic_tracker_notion - no Notion icon
                              // exists yet; reuse a placeholder drawable or design one,
                              // same way the app icon work was done earlier
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import logcat.LogPriority
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.model.Track as DomainTrack

/**
 * Notion tracker - syncs reading progress to a page in a Notion database you control.
 *
 * Setup on the user's end (document this in the login screen / a help link):
 * 1. Create an "Internal Integration" at https://www.notion.so/my-integrations, copy its
 *    secret token (starts with "secret_" or "ntn_").
 * 2. Create (or reuse) a database in Notion with these properties:
 *      - Title (the default title property - any name is fine, it's always the title type)
 *      - "Status"  (Select property) with options named exactly: Reading, Completed,
 *        Plan to Read, On Hold, Dropped
 *      - "Chapter" (Number property)
 * 3. Open that database, click "..." -> Connections -> add your integration.
 * 4. Copy the database's ID from its URL (the 32-char id segment before any "?").
 *
 * In Koware's login screen: username field = database ID, password field = integration secret.
 * Reuses BaseTracker's existing username/password storage - same pattern NovelUpdates.kt
 * already uses for its cookie string, just repurposed for these two values instead.
 */
class NotionTracker(id: Long) : BaseTracker(id, "Notion") {

    private val json: Json by injectLazy()
    private val apiBase = "https://api.notion.com/v1"
    private val notionVersion = "2022-06-28"

    override fun getLogo() = R.drawable.ic_tracker_notion

    override fun getStatusList() = listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ)

    override fun getStatus(status: Long): StringResource? {
        return when (status) {
            READING -> MR.strings.reading
            COMPLETED -> MR.strings.completed
            ON_HOLD -> MR.strings.on_hold
            DROPPED -> MR.strings.dropped
            PLAN_TO_READ -> MR.strings.plan_to_read
            else -> null
        }
    }

    override fun getReadingStatus() = READING
    override fun getRereadingStatus() = READING
    override fun getCompletionStatus() = COMPLETED

    override fun getScoreList(): List<String> = emptyList() // no score concept for this tracker

    override fun indexToScore(index: Int): Double = 0.0

    override fun displayScore(track: DomainTrack): String = "-"

    private fun statusToNotionName(status: Long): String = when (status) {
        READING -> "Reading"
        COMPLETED -> "Completed"
        PLAN_TO_READ -> "Plan to Read"
        ON_HOLD -> "On Hold"
        DROPPED -> "Dropped"
        else -> "Reading"
    }

    private fun notionNameToStatus(name: String?): Long = when (name) {
        "Reading" -> READING
        "Completed" -> COMPLETED
        "Plan to Read" -> PLAN_TO_READ
        "On Hold" -> ON_HOLD
        "Dropped" -> DROPPED
        else -> READING
    }

    /** username field repurposed to hold the Notion database ID; password holds the secret. */
    private fun getDatabaseId(): String = getUsername()
    private fun getSecret(): String = getPassword()

    private fun authHeaders(): Headers = Headers.Builder()
        .add("Authorization", "Bearer ${getSecret()}")
        .add("Notion-Version", notionVersion)
        .add("Content-Type", "application/json")
        .build()

    /**
     * Self-contained PATCH builder - NovelUpdates.kt (the reference file this tracker was
     * built from) only demonstrated GET/POST helpers, so rather than assume a matching PATCH
     * extension exists elsewhere in the codebase, this builds the request directly. Same call
     * shape as GET/POST though: `client.newCall(patchRequest(url, headers, body)).awaitSuccess()`.
     */
    private fun patchRequest(url: String, headers: Headers, body: RequestBody): Request =
        Request.Builder()
            .url(url)
            .headers(headers)
            .patch(body)
            .build()

    override suspend fun login(username: String, password: String) {
        // No real "login" call in Notion's model - the token is either valid or it isn't,
        // verified lazily on first real request. Just store the two values.
        saveCredentials(username, password)
    }

    override suspend fun search(query: String): List<TrackSearch> {
        val body = buildJsonObject {
            putJsonObject("filter") {
                put("property", "title")
                putJsonObject("title") {
                    put("contains", query)
                }
            }
        }.toString().toRequestBody("application/json".toMediaType())

        return try {
            val response = client.newCall(
                POST("$apiBase/databases/${getDatabaseId()}/query", authHeaders(), body),
            ).awaitSuccess()
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            val results = root["results"]?.jsonArray ?: JsonArray(emptyList())

            results.map { result ->
                val page = result.jsonObject
                val pageId = page["id"]?.jsonPrimitive?.content.orEmpty()
                val properties = page["properties"]?.jsonObject
                val title = extractTitle(properties)

                TrackSearch.create(id).apply {
                    this.title = title
                    remote_id = pageId.hashCode().toLong().let { if (it < 0) -it else it }
                    tracking_url = pageId // real Notion page id, not a browsable URL - see bind()
                    summary = ""
                    cover_url = ""
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Notion search failed" }
            emptyList()
        }
    }

    /**
     * Looks for an existing row (page) in the shared database matching this title, so the
     * same novel always ends up as one persistent row rather than a fresh duplicate every
     * time bind() runs (app restart, re-adding to library, etc).
     */
    private suspend fun findExistingPage(title: String): String? {
        val body = buildJsonObject {
            putJsonObject("filter") {
                put("property", "title")
                putJsonObject("title") {
                    put("equals", title)
                }
            }
        }.toString().toRequestBody("application/json".toMediaType())

        return try {
            val response = client.newCall(
                POST("$apiBase/databases/${getDatabaseId()}/query", authHeaders(), body),
            ).awaitSuccess()
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            val results = root["results"]?.jsonArray ?: JsonArray(emptyList())
            results.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Notion: existing-page lookup failed" }
            null
        }
    }

    /**
     * Binds this novel to a row in the shared database. Reuses an existing row if one already
     * matches by exact title - so every novel gets exactly one persistent row, all living in
     * the single database you configured, rather than a new page created on every bind().
     * When reusing an existing row, its current status/progress is adopted (mirrors how
     * NovelUpdates.bind() reads back the remote state rather than blindly overwriting it).
     */
    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        val existingPageId = findExistingPage(track.title)

        if (existingPageId != null) {
            track.tracking_url = existingPageId
            track.remote_id = existingPageId.hashCode().toLong().let { if (it < 0) -it else it }
            // Adopt whatever's already in Notion for this row, same as NovelUpdates does.
            return refresh(track)
        }

        val initialStatus = if (hasReadChapters) READING else PLAN_TO_READ

        val body = buildJsonObject {
            putJsonObject("parent") {
                put("database_id", getDatabaseId())
            }
            putJsonObject("properties") {
                putJsonObject("Title") {
                    putJsonArray("title") {
                        add(
                            buildJsonObject {
                                putJsonObject("text") {
                                    put("content", track.title)
                                }
                            },
                        )
                    }
                }
                putJsonObject("Status") {
                    putJsonObject("select") {
                        put("name", statusToNotionName(initialStatus))
                    }
                }
                putJsonObject("Chapter") {
                    put("number", track.last_chapter_read)
                }
            }
        }.toString().toRequestBody("application/json".toMediaType())

        val response = client.newCall(POST("$apiBase/pages", authHeaders(), body)).awaitSuccess()
        val created = json.parseToJsonElement(response.body.string()).jsonObject
        val pageId = created["id"]?.jsonPrimitive?.content.orEmpty()

        // Track.remote_id is a Long, but Notion page ids are UUID strings, so the real id
        // lives in tracking_url (already a persisted String field - same trick NovelUpdates.kt
        // uses for its own non-numeric ids). remote_id gets a hash purely for equality/display;
        // it's never used to look the page back up - tracking_url is the source of truth.
        track.tracking_url = pageId
        track.remote_id = pageId.hashCode().toLong().let { if (it < 0) -it else it }
        track.status = initialStatus

        return track
    }

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        val pageId = resolvePageId(track) ?: return track

        val body = buildJsonObject {
            putJsonObject("properties") {
                putJsonObject("Status") {
                    putJsonObject("select") {
                        put("name", statusToNotionName(track.status))
                    }
                }
                putJsonObject("Chapter") {
                    put("number", track.last_chapter_read)
                }
            }
        }.toString().toRequestBody("application/json".toMediaType())

        client.newCall(patchRequest("$apiBase/pages/$pageId", authHeaders(), body)).awaitSuccess()
        return track
    }

    override suspend fun refresh(track: Track): Track {
        val pageId = resolvePageId(track) ?: return track

        val response = client.newCall(GET("$apiBase/pages/$pageId", authHeaders())).awaitSuccess()
        val page = json.parseToJsonElement(response.body.string()).jsonObject
        val properties = page["properties"]?.jsonObject

        val statusName = properties?.get("Status")?.jsonObject?.get("select")?.jsonObject
            ?.get("name")?.jsonPrimitive?.content
        track.status = notionNameToStatus(statusName)

        val chapterNum = properties?.get("Chapter")?.jsonObject?.get("number")?.jsonPrimitive
            ?.content?.toDoubleOrNull()
        if (chapterNum != null) track.last_chapter_read = chapterNum

        return track
    }

    /** tracking_url holds the real Notion page id (see the comment in bind() above). */
    private fun resolvePageId(track: Track): String? {
        return track.tracking_url.ifBlank { null }
    }

    private fun extractTitle(properties: JsonObject?): String {
        val titleProp = properties?.values?.firstOrNull { prop ->
            prop.jsonObject["type"]?.jsonPrimitive?.content == "title"
        }?.jsonObject
        val titleArray = titleProp?.get("title")?.jsonArray ?: return ""
        return titleArray.joinToString("") { it.jsonObject["plain_text"]?.jsonPrimitive?.content.orEmpty() }
    }

    companion object {
        const val READING = 1L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_READ = 5L
    }
}
