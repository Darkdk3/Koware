package eu.kanade.tachiyomi.data.track.notion

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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
 * Notion tracker - syncs reading progress to pages in a Notion database you control.
 *
 * Setup on the user's end (documented in the login dialog):
 * 1. Create an "Internal Integration" at https://www.notion.so/my-integrations, copy its
 *    secret token (starts with "secret_" or "ntn_").
 * 2. Open the database you want to use, click "..." -> Connections -> add your integration.
 *    The tracker can also list / auto-create the recommended database for you.
 * 3. Paste the secret, then pick or create the database. The app verifies the token and the
 *    database sharing permission, and auto-provisions the recommended columns:
 *    Title, Type, Status, Chapter, Score, Total Chapters.
 *
 * Login screen: secret field = integration secret, database field = database ID or URL.
 * Reuses BaseTracker's username/password storage the same way other trackers do.
 */
class NotionTracker(id: Long) : BaseTracker(id, "Notion") {

    private val json: Json by injectLazy()
    private val apiBase = "https://api.notion.com/v1"
    private val notionVersion = "2022-06-28"

    private var schemaCache: SchemaInfo? = null

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

    override fun getScoreList(): List<String> = emptyList()

    override fun indexToScore(index: Int): Double = 0.0

    override fun displayScore(track: DomainTrack): String = "-"

    /** username field holds the Notion database ID (hyphens stripped); password holds the secret. */
    private fun getDatabaseId(): String = normalizeDatabaseId(getUsername()).orEmpty()

    private fun getSecret(): String = getPassword()

    private fun authHeaders(secret: String = getSecret()): Headers = Headers.Builder()
        .add("Authorization", "Bearer $secret")
        .add("Notion-Version", notionVersion)
        .add("Content-Type", "application/json")
        .build()

    private fun patchRequest(url: String, headers: Headers, body: RequestBody): Request =
        Request.Builder()
            .url(url)
            .headers(headers)
            .patch(body)
            .build()

    /**
     * Validates the secret and the database sharing permission, then makes sure the
     * recommended columns exist. Throws with a user-friendly message on failure.
     */
    override suspend fun login(username: String, password: String) {
        val databaseId = normalizeDatabaseId(username)
            ?: throw IllegalStateException("The database ID or URL doesn't look right.")
        if (password.isBlank()) throw IllegalStateException("Enter the integration secret.")

        // Check the token itself first (401 = bad secret).
        try {
            client.newCall(GET("$apiBase/users/me", authHeaders(password))).awaitSuccess().close()
        } catch (e: HttpException) {
            throw IllegalStateException(
                "That integration secret was rejected (${e.code}). " +
                    "Create a new one at notion.so/my-integrations.",
            )
        }

        // Check the database is reachable AND shared with the integration.
        val schema = try {
            fetchDatabaseSchema(databaseId, password)
        } catch (e: HttpException) {
            when (e.code) {
                404 -> throw IllegalStateException(
                    "Database not found. Open the database in Notion, share it with your " +
                        "integration (top-right ... -> Connections), then retry.",
                )
                403 -> throw IllegalStateException(
                    "The integration can see the token but Notion blocked access (${e.code}). " +
                        "Share the database with the integration first.",
                )
                else -> throw IllegalStateException("Couldn't reach the database (${e.code}).")
            }
        }

        applyRecommendedSchema(schema, databaseId, password)

        saveCredentials(databaseId, password)
        saveDisplayUsername(extractDatabaseTitle(schema).ifBlank { databaseId })
        schemaCache = null
    }

    /**
     * Lists every database the integration can currently access. This is the "link via
     * permissions" check: databases appear here only if the user shared them with the
     * integration inside Notion.
     */
    suspend fun fetchDatabases(secret: String): List<Pair<String, String>> {
        val body = buildJsonObject {
            putJsonObject("filter") {
                put("value", "database")
                put("property", "object")
            }
        }.toString().toRequestBody("application/json".toMediaType())

        val response = client.newCall(POST("$apiBase/search", authHeaders(secret), body)).awaitSuccess()
        val root = json.parseToJsonElement(response.body.string()).jsonObject
        response.close()
        val results = root["results"]?.jsonArray ?: JsonArray(emptyList())

        return results.mapNotNull { item ->
            val database = item.jsonObject
            val databaseId = database["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val title = database["title"]?.jsonArray.orEmpty()
                .joinToString("") { it.jsonObject["plain_text"]?.jsonPrimitive?.content.orEmpty() }
            databaseId to title
        }
    }

    /**
     * Creates a ready-to-use tracking database inside a page the integration can access,
     * then stores it as the active database. Returns the new database ID.
     */
    suspend fun createTrackingDatabase(parentPageIdOrUrl: String, secret: String): String {
        val parentPageId = normalizeDatabaseId(parentPageIdOrUrl)
            ?: throw IllegalStateException("The page ID or URL doesn't look right.")
        if (secret.isBlank()) throw IllegalStateException("Enter the integration secret first.")

        val body = buildJsonObject {
            putJsonObject("parent") {
                put("type", "page_id")
                put("page_id", parentPageId)
            }
            putJsonArray("title") {
                add(
                    buildJsonObject {
                        putJsonObject("text") {
                            put("content", "Koware Tracking")
                        }
                    },
                )
            }
            putJsonObject("properties") {
                putJsonObject(TITLE_PROPERTY) {
                    putJsonObject("title") {}
                }
                putJsonObject(TYPE_PROPERTY) {
                    putJsonObject("select") {
                        putJsonArray("options") {
                            MEDIA_TYPES.forEach { type ->
                                add(buildJsonObject { put("name", type) })
                            }
                        }
                    }
                }
                putJsonObject(STATUS_PROPERTY) {
                    putJsonObject("select") {
                        putJsonArray("options") {
                            STATUS_NAMES.forEach { name ->
                                add(buildJsonObject { put("name", name) })
                            }
                        }
                    }
                }
                putJsonObject(CHAPTER_PROPERTY) {
                    putJsonObject("number") {}
                }
                putJsonObject(SCORE_PROPERTY) {
                    putJsonObject("number") {}
                }
                putJsonObject(TOTAL_CHAPTERS_PROPERTY) {
                    putJsonObject("number") {}
                }
                putJsonObject(COVER_PROPERTY) {
                    putJsonObject("url") {}
                }
            }
        }.toString().toRequestBody("application/json".toMediaType())

        val response = client.newCall(POST("$apiBase/databases", authHeaders(secret), body)).awaitSuccess()
        val created = json.parseToJsonElement(response.body.string()).jsonObject
        response.close()
        val databaseId = created["id"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Notion didn't return a database ID.")

        saveCredentials(databaseId, secret)
        saveDisplayUsername(extractDatabaseTitle(created).ifBlank { databaseId })
        schemaCache = null
        return databaseId
    }

    override suspend fun search(query: String): List<TrackSearch> {
        // Empty query = browse the whole database instead of filtering by title.
        if (query.isBlank()) return fetchAllEntries()

        val schema = ensureSchemaLoaded() ?: return emptyList()
        val titleProperty = schema.titleProperty

        val body = buildJsonObject {
            putJsonObject("filter") {
                put("property", titleProperty)
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
            response.close()
            (root["results"]?.jsonArray ?: JsonArray(emptyList())).map { result ->
                toTrackSearch(result.jsonObject)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Notion search failed" }
            emptyList()
        }
    }

    /** Creates a new page (row) in the database and returns it as a search result. */
    suspend fun createEntry(title: String, coverUrl: String? = null): TrackSearch {
        val schema = requireNotNull(ensureSchemaLoaded()) {
            "Notion isn't connected. Go to Settings -> Tracking and log in first."
        }
        val databaseId = getDatabaseId()
        val typeKey = schema.keyFor(TYPE_PROPERTY)
        val statusKey = schema.keyFor(STATUS_PROPERTY)
        val chapterKey = schema.keyFor(CHAPTER_PROPERTY)
        val defaultType = defaultMediaType()

        val body = buildJsonObject {
            putJsonObject("parent") {
                put("database_id", databaseId)
            }
            putJsonObject("properties") {
                putJsonObject(schema.titleProperty) {
                    putJsonArray("title") {
                        add(
                            buildJsonObject {
                                putJsonObject("text") {
                                    put("content", title)
                                }
                            },
                        )
                    }
                }
                if (typeKey != null) {
                    putJsonObject(typeKey) {
                        putJsonObject("select") {
                            if (defaultType.isNotBlank()) put("name", defaultType)
                        }
                    }
                }
                if (statusKey != null) {
                    putJsonObject(statusKey) {
                        putJsonObject("select") {
                            put("name", statusToNotionName(PLAN_TO_READ))
                        }
                    }
                }
                if (chapterKey != null) {
                    putJsonObject(chapterKey) {
                        put("number", 0.0)
                    }
                }
                if (coverUrl.isNullOrBlank().not()) {
                    schema.keyFor(COVER_PROPERTY)?.let { coverKey ->
                        putJsonObject(coverKey) {
                            put("url", coverUrl)
                        }
                    }
                }
            }
        }.toString().toRequestBody("application/json".toMediaType())

        val response = client.newCall(POST("$apiBase/pages", authHeaders(), body)).awaitSuccess()
        val created = json.parseToJsonElement(response.body.string()).jsonObject
        response.close()
        val pageId = created["id"]?.jsonPrimitive?.content.orEmpty()

        if (coverUrl.isNullOrBlank().not()) {
            runCatching {
                val coverBody = buildJsonObject {
                    putJsonObject("cover") {
                        put("type", "external")
                        putJsonObject("external") {
                            put("url", coverUrl)
                        }
                    }
                }.toString().toRequestBody("application/json".toMediaType())
                client.newCall(patchRequest("$apiBase/pages/$pageId", authHeaders(), coverBody)).awaitSuccess().close()
            }.onFailure {
                logcat(LogPriority.WARN, it) { "Notion: failed to set cover for $pageId" }
            }
        }

        return TrackSearch.create(id).apply {
            this.title = title
            this.cover_url = coverUrl.orEmpty()
            remote_id = hashPageId(pageId)
            tracking_url = pageId
        }
    }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        val schema = ensureSchemaLoaded() ?: return track
        val existingPageId = findExistingPage(track.title)

        if (existingPageId != null) {
            track.tracking_url = existingPageId
            track.remote_id = hashPageId(existingPageId)
            // Adopt whatever is already in Notion for this row.
            return refresh(track)
        }

        val initialStatus = if (hasReadChapters) READING else PLAN_TO_READ
        val databaseId = getDatabaseId()
        val typeKey = schema.keyFor(TYPE_PROPERTY)
        val statusKey = schema.keyFor(STATUS_PROPERTY)
        val chapterKey = schema.keyFor(CHAPTER_PROPERTY)
        val scoreKey = schema.keyFor(SCORE_PROPERTY)
        val totalChaptersKey = schema.keyFor(TOTAL_CHAPTERS_PROPERTY)
        val coverUrl = (track as? TrackSearch)?.cover_url

        val body = buildJsonObject {
            putJsonObject("parent") {
                put("database_id", databaseId)
            }
            putJsonObject("properties") {
                putJsonObject(schema.titleProperty) {
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
                if (typeKey != null) {
                    putJsonObject(typeKey) {
                        putJsonObject("select") {
                            if (defaultMediaType().isNotBlank()) put("name", defaultMediaType())
                        }
                    }
                }
                if (statusKey != null) {
                    putJsonObject(statusKey) {
                        putJsonObject("select") {
                            put("name", statusToNotionName(initialStatus))
                        }
                    }
                }
                if (chapterKey != null) {
                    putJsonObject(chapterKey) {
                        put("number", track.last_chapter_read)
                    }
                }
                if (scoreKey != null && track.score > 0) {
                    putJsonObject(scoreKey) {
                        put("number", track.score)
                    }
                }
                if (totalChaptersKey != null && track.total_chapters > 0) {
                    putJsonObject(totalChaptersKey) {
                        put("number", track.total_chapters.toDouble())
                    }
                }
                if (coverUrl.isNullOrBlank().not()) {
                    schema.keyFor(COVER_PROPERTY)?.let { coverKey ->
                        putJsonObject(coverKey) {
                            put("url", coverUrl)
                        }
                    }
                }
            }
        }.toString().toRequestBody("application/json".toMediaType())

        val response = client.newCall(POST("$apiBase/pages", authHeaders(), body)).awaitSuccess()
        val created = json.parseToJsonElement(response.body.string()).jsonObject
        response.close()
        val pageId = created["id"]?.jsonPrimitive?.content.orEmpty()

        track.tracking_url = pageId
        track.remote_id = hashPageId(pageId)
        track.status = initialStatus

        // Page-level cover, shown at the top of the row in Notion.
        if (coverUrl.isNullOrBlank().not()) {
            runCatching {
                val coverBody = buildJsonObject {
                    putJsonObject("cover") {
                        put("type", "external")
                        putJsonObject("external") {
                            put("url", coverUrl)
                        }
                    }
                }.toString().toRequestBody("application/json".toMediaType())
                client.newCall(patchRequest("$apiBase/pages/$pageId", authHeaders(), coverBody)).awaitSuccess().close()
            }.onFailure {
                logcat(LogPriority.WARN, it) { "Notion: failed to set cover for $pageId" }
            }
        }

        return track
    }

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        ensureSchemaLoaded() ?: return track
        val pageId = resolvePageId(track) ?: return track
        val schema = schemaCache ?: return track
        val statusKey = schema.keyFor(STATUS_PROPERTY)
        val chapterKey = schema.keyFor(CHAPTER_PROPERTY)
        val scoreKey = schema.keyFor(SCORE_PROPERTY)
        val totalChaptersKey = schema.keyFor(TOTAL_CHAPTERS_PROPERTY)

        val body = buildJsonObject {
            putJsonObject("properties") {
                if (statusKey != null) {
                    putJsonObject(statusKey) {
                        putJsonObject("select") {
                            put("name", statusToNotionName(track.status))
                        }
                    }
                }
                if (chapterKey != null) {
                    putJsonObject(chapterKey) {
                        put("number", track.last_chapter_read)
                    }
                }
                if (scoreKey != null && track.score > 0) {
                    putJsonObject(scoreKey) {
                        put("number", track.score)
                    }
                }
                if (totalChaptersKey != null && track.total_chapters > 0) {
                    putJsonObject(totalChaptersKey) {
                        put("number", track.total_chapters.toDouble())
                    }
                }
            }
        }.toString().toRequestBody("application/json".toMediaType())

        client.newCall(patchRequest("$apiBase/pages/$pageId", authHeaders(), body)).awaitSuccess().close()
        return track
    }

    override suspend fun refresh(track: Track): Track {
        ensureSchemaLoaded() ?: return track
        val pageId = resolvePageId(track) ?: return track
        val schema = schemaCache ?: return track

        val response = client.newCall(GET("$apiBase/pages/$pageId", authHeaders())).awaitSuccess()
        val page = json.parseToJsonElement(response.body.string()).jsonObject
        response.close()
        val properties = page["properties"]?.jsonObject ?: return track

        schema.keyFor(STATUS_PROPERTY)?.let { statusKey ->
            val statusName = properties[statusKey]?.jsonObject?.get("select")
                ?.objectOrNull()?.get("name")?.jsonPrimitive?.contentOrNull
            track.status = notionNameToStatus(statusName)
        }
        schema.keyFor(CHAPTER_PROPERTY)?.let { chapterKey ->
            val chapter = properties[chapterKey]?.jsonObject?.get("number")
                ?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            if (chapter != null) track.last_chapter_read = chapter
        }
        schema.keyFor(SCORE_PROPERTY)?.let { scoreKey ->
            val score = properties[scoreKey]?.jsonObject?.get("number")
                ?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            if (score != null) track.score = score
        }
        schema.keyFor(TOTAL_CHAPTERS_PROPERTY)?.let { totalKey ->
            val total = properties[totalKey]?.jsonObject?.get("number")
                ?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            if (total != null) track.total_chapters = total.toLong()
        }

        return track
    }

    /** Fetches every page in the database, paginated, returning them as search results. */
    private suspend fun fetchAllEntries(): List<TrackSearch> {
        val schema = ensureSchemaLoaded() ?: return emptyList()
        val databaseId = getDatabaseId()
        val entries = mutableListOf<TrackSearch>()
        var startCursor: String? = null

        while (true) {
            val body = buildJsonObject {
                put("page_size", 100)
                if (startCursor != null) put("start_cursor", startCursor!!)
            }.toString().toRequestBody("application/json".toMediaType())

            val response = try {
                client.newCall(POST("$apiBase/databases/$databaseId/query", authHeaders(), body)).awaitSuccess()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Notion: query failed" }
                break
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            response.close()

            val results = root["results"]?.jsonArray ?: JsonArray(emptyList())
            entries += results.map { result -> toTrackSearch(result.jsonObject) }

            val hasMore = root["has_more"]?.jsonPrimitive?.content == "true"
            startCursor = root["next_cursor"]?.jsonPrimitive?.content
            if (!hasMore || startCursor.isNullOrBlank()) break
        }

        return entries.sortedBy { it.title.lowercase(java.util.Locale.ROOT) }
    }

    /** Looks for an existing row (page) matching this title, to avoid duplicates. */
    private suspend fun findExistingPage(title: String): String? {
        val schema = ensureSchemaLoaded() ?: return null
        val titleProperty = schema.titleProperty

        val body = buildJsonObject {
            putJsonObject("filter") {
                put("property", titleProperty)
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
            response.close()
            (root["results"]?.jsonArray ?: JsonArray(emptyList()))
                .firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Notion: existing-page lookup failed" }
            null
        }
    }

    private suspend fun fetchDatabaseSchema(databaseId: String, secret: String): JsonObject {
        val response = client.newCall(GET("$apiBase/databases/$databaseId", authHeaders(secret))).awaitSuccess()
        val schema = json.parseToJsonElement(response.body.string()).jsonObject
        response.close()
        return schema
    }

    /**
     * Loads (and caches) the database schema so property names are resolved dynamically
     * instead of assuming the layout. Also provisions missing recommended columns.
     */
    private suspend fun ensureSchemaLoaded(): SchemaInfo? {
        val databaseId = getDatabaseId()
        val secret = getSecret()
        if (databaseId.isBlank() || secret.isBlank()) return null
        if (schemaCache?.databaseId == databaseId) return schemaCache

        return try {
            val schema = fetchDatabaseSchema(databaseId, secret)
            val properties = schema["properties"]?.jsonObject ?: JsonObject(emptyMap())
            val titleProperty = properties.entries.firstOrNull { (_, value) ->
                value.jsonObject["type"]?.jsonPrimitive?.content == "title"
            }?.key ?: TITLE_PROPERTY

            val added = applyRecommendedSchema(schema, databaseId, secret)
            val info = SchemaInfo(
                databaseId = databaseId,
                titleProperty = titleProperty,
                properties = properties.keys + added,
            )
            schemaCache = info
            info
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Notion: could not load schema for $databaseId" }
            null
        }
    }

    /**
     * Best-effort: adds the recommended columns (Type, Status, Chapter, Score, Total Chapters)
     * to an existing database if the integration has update permission, and appends any
     * missing status/type options to existing select columns. Returns the keys it added so
     * callers can update their schema cache without another round-trip.
     */
    private suspend fun applyRecommendedSchema(schema: JsonObject, databaseId: String, secret: String): Set<String> {
        val existing = schema["properties"]?.jsonObject ?: return emptySet()

        val additions = buildJsonObject {
            if (!existing.containsKey(TYPE_PROPERTY)) {
                putJsonObject(TYPE_PROPERTY) {
                    putJsonObject("select") {
                        putJsonArray("options") {
                            MEDIA_TYPES.forEach { type ->
                                add(buildJsonObject { put("name", type) })
                            }
                        }
                    }
                }
            }
            if (!existing.containsKey(STATUS_PROPERTY)) {
                putJsonObject(STATUS_PROPERTY) {
                    putJsonObject("select") {
                        putJsonArray("options") {
                            STATUS_NAMES.forEach { name ->
                                add(buildJsonObject { put("name", name) })
                            }
                        }
                    }
                }
            }
            if (!existing.containsKey(CHAPTER_PROPERTY)) {
                putJsonObject(CHAPTER_PROPERTY) { putJsonObject("number") {} }
            }
            if (!existing.containsKey(SCORE_PROPERTY)) {
                putJsonObject(SCORE_PROPERTY) { putJsonObject("number") {} }
            }
            if (!existing.containsKey(TOTAL_CHAPTERS_PROPERTY)) {
                putJsonObject(TOTAL_CHAPTERS_PROPERTY) { putJsonObject("number") {} }
            }
            if (!existing.containsKey(COVER_PROPERTY)) {
                putJsonObject(COVER_PROPERTY) { putJsonObject("url") {} }
            }
        }

        // Merge missing select options into existing select columns.
        listOf(TYPE_PROPERTY to MEDIA_TYPES, STATUS_PROPERTY to STATUS_NAMES).forEach { (property, desired) ->
            val existingType = existing[property]?.jsonObject?.get("type")?.jsonPrimitive?.content
            if (existingType == "select") {
                val existingOptions = existing[property]?.jsonObject?.get("select")?.jsonObject
                    ?.get("options")?.jsonArray.orEmpty()
                    .mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
                    .toSet()
                val missing = desired.filter { it !in existingOptions }
                if (missing.isNotEmpty() && !additions.containsKey(property)) {
                    additions.put(property, buildJsonObject {
                        putJsonObject("select") {
                            putJsonArray("options") {
                                (existingOptions + desired).forEach { name ->
                                    add(buildJsonObject { put("name", name) })
                                }
                            }
                        }
                    })
                }
            }
        }

        if (additions.isEmpty()) return emptySet()

        runCatching {
            val body = buildJsonObject {
                putJsonObject("properties") {
                    additions.forEach { (name, value) -> put(name, value) }
                }
            }.toString().toRequestBody("application/json".toMediaType())
            client.newCall(patchRequest("$apiBase/databases/$databaseId", authHeaders(secret), body)).awaitSuccess().close()
        }.onFailure {
            logcat(LogPriority.WARN, it) { "Notion: could not provision schema columns for $databaseId" }
            return emptySet()
        }
        return additions.keys
    }

    private fun toTrackSearch(page: JsonObject): TrackSearch {
        val pageId = page["id"]?.jsonPrimitive?.content.orEmpty()
        val properties = page["properties"]?.jsonObject
        val schema = schemaCache

        return TrackSearch.create(id).apply {
            title = extractTitle(properties)
            remote_id = hashPageId(pageId)
            tracking_url = pageId
            cover_url = schema?.keyFor(COVER_PROPERTY)?.let { coverKey ->
                properties?.get(coverKey)?.jsonObject?.get("url")
                    ?.jsonPrimitive?.contentOrNull.orEmpty()
            }.orEmpty()
            publishing_type = schema?.keyFor(TYPE_PROPERTY)?.let { typeKey ->
                properties?.get(typeKey)?.jsonObject?.get("select")
                    ?.objectOrNull()?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
            }.orEmpty()
            schema?.keyFor(TOTAL_CHAPTERS_PROPERTY)?.let { totalKey ->
                val total = properties?.get(totalKey)?.jsonObject?.get("number")
                    ?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                if (total != null) total_chapters = total.toLong()
            }
            schema?.keyFor(SCORE_PROPERTY)?.let { scoreKey ->
                val score = properties?.get(scoreKey)?.jsonObject?.get("number")
                    ?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                if (score != null) this.score = score
            }
        }
    }

    /** tracking_url holds the real Notion page id (see the comment in bind() above). */
    private fun resolvePageId(track: Track): String? {
        return track.tracking_url.ifBlank { null }
    }

    /** Notion uses JSON null for empty select/url values; .jsonObject would throw on those. */
    private fun JsonElement?.objectOrNull(): JsonObject? = this as? JsonObject

    private fun extractTitle(properties: JsonObject?): String {
        val titleProp = properties?.values?.firstOrNull { prop ->
            prop.jsonObject["type"]?.jsonPrimitive?.content == "title"
        }?.jsonObject
        val titleArray = titleProp?.get("title")?.jsonArray ?: return ""
        return titleArray.joinToString("") { it.jsonObject["plain_text"]?.jsonPrimitive?.content.orEmpty() }
    }

    private fun extractDatabaseTitle(database: JsonObject): String {
        return (database["title"]?.jsonArray ?: JsonArray(emptyList()))
            .joinToString("") { it.jsonObject["plain_text"]?.jsonPrimitive?.content.orEmpty() }
    }

    private fun defaultMediaType(): String {
        return trackPreferences.notionDefaultMediaType.get().trim().ifBlank { DEFAULT_MEDIA_TYPE }
    }

    private fun hashPageId(pageId: String): Long {
        return pageId.hashCode().toLong().let { if (it < 0) -it else it }
    }

    companion object {
        const val TITLE_PROPERTY = "Title"
        const val TYPE_PROPERTY = "Type"
        const val STATUS_PROPERTY = "Status"
        const val CHAPTER_PROPERTY = "Chapter"
        const val SCORE_PROPERTY = "Score"
        const val TOTAL_CHAPTERS_PROPERTY = "Total Chapters"
        const val COVER_PROPERTY = "Cover"

        const val DEFAULT_MEDIA_TYPE = "Manga"

        /** Select options written to the Type column: book, novel, manga, manhwa, etc. */
        val MEDIA_TYPES = listOf(
            "Manga", "Manhwa", "Manhua", "Webtoon", "Comic",
            "Novel", "Light Novel", "Book", "One-shot", "Doujin", "Other",
        )

        val STATUS_NAMES = listOf(
            "Reading", "Completed", "Plan to Read", "On Hold", "Dropped",
        )

        const val READING = 1L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_READ = 5L

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

        /** Accepts a bare ID or a notion.so URL; strips hyphens for a canonical ID. */
        private fun normalizeDatabaseId(input: String): String? {
            if (input.isBlank()) return null
            val trimmed = input.trim()
            val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                .find(trimmed)?.value
            val id = uuid ?: trimmed.substringAfterLast('/').substringBefore('?')
            val cleaned = id.lowercase(java.util.Locale.ROOT).replace("-", "")
            return cleaned.takeIf { it.length == 32 || Regex("^[0-9a-fA-F]{32}$").matches(cleaned) }
        }
    }

    private data class SchemaInfo(
        val databaseId: String,
        val titleProperty: String,
        val properties: Set<String>,
    ) {
        fun keyFor(desired: String): String? = desired.takeIf { it in properties }
    }
}
