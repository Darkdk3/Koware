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
import tachiyomi.domain.history.interactor.GetHistory
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.coroutines.cancellation.CancellationException

/**
 * Handles calls from the page's Koware object.
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
            reply(id, error = e.message)
        } catch (e: Exception) {
            reply(id, error = "\"$method\" failed")
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

        "getHistory" -> getHistory(args)

        else -> throw ApiException(
            "Unknown function \"$method\". Call Koware.help() to see available functions.",
        )
    }

    /**
     * Returns genuine reading-history records from the app database.
     * The requested limit is clamped to 1..50.
     */
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
                        .put(
                            "readAt",
                            item.readAt?.time?.let { it } ?: JSONObject.NULL,
                        )
                        .put("readDuration", item.readDuration)
                        .put(
                            "coverUrl",
                            item.coverData.url ?: JSONObject.NULL,
                        )
                        .put("chapterRead", item.chapterRead)
                        .put("lastPageRead", item.lastPageRead)
                        .put("isNovel", item.isNovel)
                    ,
                )
            }
        }
    }

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