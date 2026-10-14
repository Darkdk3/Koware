
package eu.kanade.tachiyomi.ui.customtab

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import eu.kanade.tachiyomi.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.GetTotalReadDuration
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.coroutines.cancellation.CancellationException

/**
 * Handles requests from a custom page's Koware JavaScript object.
 * Only explicitly registered, read-only functions are exposed.
 */
class KowareBridge(
    private val theme: () -> String,
) {
    private class ApiException(message: String) : Exception(message)

    suspend fun handle(raw: String): String {
        val request = runCatching { JSONObject(raw) }.getOrNull()
            ?: return reply(-1, error = "Invalid request")

        val id = request.optInt("id", -1)
        val method = request.optString("method")
        val args = request.optJSONArray("args") ?: JSONArray()

        return try {
            reply(id, result = dispatch(method, args))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            reply(id, error = e.message ?: "Invalid API request")
        } catch (e: Exception) {
            reply(id, error = "Koware.$method failed")
        }
    }

    private suspend fun dispatch(
        method: String,
        args: JSONArray,
    ): Any? = when (method) {
        "help" -> JSONArray().apply {
            CustomTabApi.methods.forEach { put(it.toJson()) }
        }

        "getInfo" -> JSONObject()
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("apiVersion", CustomTabApi.VERSION)

        "getTheme" -> JSONObject(theme())

        "getLibrary" -> getLibrary(args)

        "getManga" -> getManga(args)

        "getChapters" -> getChapters(args)

        "getCategories" -> getCategories()

        "getHistory" -> getHistory(args)

        "getStats" -> getStats()

        else -> throw ApiException(
            "Unknown function \"$method\". Call Koware.help() to list available methods.",
        )
    }

    private suspend fun getLibrary(args: JSONArray): JSONArray {
        val options = args.optJSONObject(0) ?: JSONObject()
        val limit = options.optInt("limit", 100).coerceIn(1, 500)

        val library = Injekt.get<GetLibraryManga>().await().take(limit)

        return JSONArray().apply {
            library.forEach { entry ->
                val manga = entry.manga

                put(
                    JSONObject()
                        .put("id", manga.id)
                        .put("title", manga.title)
                        .put("url", manga.url)
                        .put("sourceId", manga.source)
                        .put("favorite", manga.favorite)
                        .put("isNovel", manga.isNovel)
                        .put("author", manga.author.jsonValue())
                        .put("artist", manga.artist.jsonValue())
                        .put("description", manga.description.jsonValue())
                        .put("genres", manga.genre.toJsonArrayOrNull())
                        .put("alternativeTitles", JSONArray(manga.alternativeTitles))
                        .put("status", manga.status)
                        .put("thumbnailUrl", manga.thumbnailUrl.jsonValue())
                        .put("categories", JSONArray(entry.categories))
                        .put("totalChapters", entry.totalChapters)
                        .put("readCount", entry.readCount)
                        .put("unreadCount", entry.unreadCount)
                        .put("bookmarkCount", entry.bookmarkCount)
                        .put("downloadCount", entry.downloadCount)
                        .put("lastRead", entry.lastRead),
                )
            }
        }
    }

    /**
     * Returns metadata for a locally stored manga or JSON null when absent.
     */
    private suspend fun getManga(args: JSONArray): Any {
        val options = args.optJSONObject(0)
            ?: throw ApiException("Use Koware.getManga({ id: 123 })")

        val mangaId = options.optLong("id", -1L)
        if (mangaId <= 0L) {
            throw ApiException("getManga requires a positive local manga id.")
        }

        val manga = Injekt.get<GetManga>().await(mangaId)
            ?: return JSONObject.NULL

        return manga.toApiJson()
    }

    private suspend fun getChapters(args: JSONArray): JSONArray {
        val options = args.optJSONObject(0)
            ?: throw ApiException("Use Koware.getChapters({ mangaId: 123 })")

        val mangaId = options.optLong("mangaId", -1L)
        if (mangaId <= 0L) {
            throw ApiException("getChapters requires a positive mangaId.")
        }

        val limit = options.optInt("limit", 200).coerceIn(1, 500)
        val chapters = Injekt.get<GetChaptersByMangaId>()
            .await(mangaId)
            .take(limit)

        return JSONArray().apply {
            chapters.forEach { chapter ->
                put(
                    JSONObject()
                        .put("id", chapter.id)
                        .put("mangaId", chapter.mangaId)
                        .put("name", chapter.name)
                        .put("url", chapter.url)
                        .put("chapterNumber", chapter.chapterNumber)
                        .put("read", chapter.read)
                        .put("bookmark", chapter.bookmark)
                        .put("lastPageRead", chapter.lastPageRead)
                        .put("dateFetch", chapter.dateFetch)
                        .put("dateUpload", chapter.dateUpload)
                        .put("scanlator", chapter.scanlator.jsonValue())
                        .put("locked", chapter.locked),
                )
            }
        }
    }

    private suspend fun getCategories(): JSONArray {
        val categories = Injekt.get<GetCategories>().await()

        return JSONArray().apply {
            categories.forEach { category ->
                put(
                    JSONObject()
                        .put("id", category.id)
                        .put("name", category.name)
                        .put("order", category.order)
                        .put("flags", category.flags)
                        .put("contentType", category.contentType)
                        .put("isSystemCategory", category.isSystemCategory),
                )
            }
        }
    }

    private suspend fun getHistory(args: JSONArray): JSONArray {
        val options = args.optJSONObject(0) ?: JSONObject()
        val limit = options.optInt("limit", 10).coerceIn(1, 50)

        val history = Injekt.get<GetHistory>()
            .subscribe(query = "", limit = limit.toLong())
            .first()

        return JSONArray().apply {
            history.forEach { item ->
                put(
                    JSONObject()
                        .put("id", item.id)
                        .put("chapterId", item.chapterId)
                        .put("mangaId", item.mangaId)
                        .put("title", item.title)
                        .put("chapterNumber", item.chapterNumber)
                        .put("readAt", item.readAt?.time.jsonValue())
                        .put("readDuration", item.readDuration)
                        .put("coverUrl", item.coverData.url.jsonValue())
                        .put("chapterRead", item.chapterRead)
                        .put("lastPageRead", item.lastPageRead)
                        .put("isNovel", item.isNovel),
                )
            }
        }
    }

    private suspend fun getStats(): JSONObject {
        val libraryCount = Injekt.get<GetLibraryManga>().await().size
        val totalReadDuration = Injekt.get<GetTotalReadDuration>().await()

        return JSONObject()
            .put("libraryCount", libraryCount)
            .put("totalReadDurationMs", totalReadDuration)
    }

    private fun Manga.toApiJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("url", url)
        .put("sourceId", source)
        .put("favorite", favorite)
        .put("isNovel", isNovel)
        .put("author", author.jsonValue())
        .put("artist", artist.jsonValue())
        .put("description", description.jsonValue())
        .put("genres", genre.toJsonArrayOrNull())
        .put("alternativeTitles", JSONArray(alternativeTitles))
        .put("status", status)
        .put("thumbnailUrl", thumbnailUrl.jsonValue())
        .put("notes", notes)
        .put("initialized", initialized)
        .put("dateAdded", dateAdded)
        .put("lastUpdate", lastUpdate)
        .put("nextUpdate", nextUpdate)

    private fun String?.jsonValue(): Any =
        this ?: JSONObject.NULL

    private fun Long?.jsonValue(): Any =
        this ?: JSONObject.NULL

    private fun List<String>?.toJsonArrayOrNull(): Any =
        this?.let { JSONArray(it) } ?: JSONObject.NULL

    private fun reply(
        id: Int,
        result: Any? = null,
        error: String? = null,
    ): String = JSONObject().apply {
        put("id", id)

        if (error != null) {
            put("ok", false)
            put("error", error)
        } else {
            put("ok", true)
            put("result", result ?: JSONObject.NULL)
        }
    }.toString()
}

/**
 * Exposes the bridge only to the custom page's own origin.
 */
fun installKowareBridge(
    webView: WebView,
    scope: CoroutineScope,
    bridge: KowareBridge,
) {
    if (!WebViewFeature.isFeatureSupported(
            WebViewFeature.WEB_MESSAGE_LISTENER,
        )
    ) return

    WebViewCompat.addWebMessageListener(
        webView,
        CustomTabApi.CHANNEL,
        setOf(CustomTabStorage.BASE_ORIGIN),
        WebViewCompat.WebMessageListener {
                _, message, sourceOrigin, isMainFrame, replyProxy ->

            if (
                !isMainFrame ||
                sourceOrigin.toString() != CustomTabStorage.BASE_ORIGIN
            ) {
                return@WebMessageListener
            }

            val payload = message.data ?: return@WebMessageListener

            scope.launch {
                val response = withContext(Dispatchers.IO) {
                    bridge.handle(payload)
                }
                replyProxy.postMessage(response)
            }
        },
    )
}
