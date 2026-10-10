package eu.kanade.tachiyomi.ui.customtab

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import eu.kanade.tachiyomi.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Handles calls from the page's Koware object. Everything here is read-only.
 */
class KowareBridge(
    private val theme: () -> String,
) {

    private class ApiException(message: String) : Exception(message)

    /** Takes the raw JSON request from the page and returns the raw JSON reply. */
    suspend fun handle(raw: String): String {
        val request = runCatching { JSONObject(raw) }.getOrNull()
            ?: return reply(id = -1, error = "Invalid request")

        val id = request.optInt("id", -1)
        val method = request.optString("method")
        val args = request.optJSONArray("args") ?: JSONArray()

        return try {
            reply(id = id, result = dispatch(method, args))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            reply(id = id, error = e.message)
        } catch (e: Exception) {
            // Keep internals out of the page; the message names the function only.
            reply(id = id, error = "\"$method\" failed")
        }
    }

    @Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
    private suspend fun dispatch(method: String, args: JSONArray): Any? =
        when (method) {
            "help" -> JSONArray().apply {
                CustomTabApi.methods.forEach { put(it.toJson()) }
            }

            "getInfo" -> JSONObject()
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("apiVersion", CustomTabApi.VERSION)

            "getTheme" -> JSONObject(theme())

            else -> throw ApiException(
                "Unknown function \"$method\". Call Koware.help() to see what is available.",
            )
        }

    private fun reply(id: Int, result: Any? = null, error: String? = null): String =
        JSONObject().apply {
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
 * Exposes the bridge to the custom page only. The listener is limited to the
 * page's own origin, so other sites, iframes, and anything loaded when safe
 * mode is off never see it. If the device can't restrict origins, nothing is
 * exposed at all.
 */
fun installKowareBridge(
    webView: WebView,
    scope: CoroutineScope,
    bridge: KowareBridge,
) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return

    WebViewCompat.addWebMessageListener(
        webView,
        CustomTabApi.CHANNEL,
        setOf(CustomTabStorage.BASE_ORIGIN),
        WebViewCompat.WebMessageListener { _, message, sourceOrigin, isMainFrame, replyProxy ->
            if (!isMainFrame || sourceOrigin.toString() != CustomTabStorage.BASE_ORIGIN) {
                return@WebMessageListener
            }
            val payload = message.data ?: return@WebMessageListener

            scope.launch {
                val response = withContext(Dispatchers.IO) { bridge.handle(payload) }
                replyProxy.postMessage(response)
            }
        },
    )
}
