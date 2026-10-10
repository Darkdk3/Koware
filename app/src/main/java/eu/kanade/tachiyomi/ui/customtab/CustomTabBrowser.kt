package eu.kanade.tachiyomi.ui.customtab

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayInputStream

/** The page for the active save, ready to load. */
internal class LoadedPage(val activeId: String?, val document: String)

/**
 * One WebView plus everything that goes with it (bridge, safe mode rules,
 * back history). It outlives the composable, so the tab can keep one alive
 * between visits instead of rebuilding it every time.
 */
@SuppressLint("SetJavaScriptEnabled")
internal class CustomTabBrowser(appContext: Context) {

    // Starts on the app context and is pointed at the activity only while the
    // WebView is on screen, so a kept WebView never holds on to an activity.
    private val contextWrapper = MutableContextWrapper(appContext)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile var theme: String = "{}"
    @Volatile var safeMode: Boolean = true
    @Volatile var allowedHosts: Set<String> = CustomTabSafety.DEFAULT_HOSTS

    private val bridge = KowareBridge { theme }

    val canGoBack = mutableStateOf(false)

    private var firstLoad = true

    val webView: WebView = WebView(contextWrapper).apply {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.textZoom = 100

        // Koware JS API, only for the custom page's own origin.
        installKowareBridge(this, scope, bridge)

        webViewClient = object : WebViewClient() {

            // Main-frame navigation (taps on links).
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                val uri = request.url
                if (uri.host == CustomTabStorage.BASE_HOST) return false

                // Never follow intent:, tel:, and similar schemes.
                if (uri.scheme != "http" && uri.scheme != "https") return true

                // Safe mode off: free browsing inside the tab.
                if (!safeMode) return false

                if (uri.scheme == "https" && CustomTabSafety.isAllowed(uri.host, allowedHosts)) {
                    return false
                }

                openInBrowser(view.context, uri)
                return true
            }

            // Every request, including iframes, images, and scripts.
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                if (!safeMode) return null

                val uri = request.url
                if (uri.scheme != "http" && uri.scheme != "https") return null

                if (uri.scheme == "https" && CustomTabSafety.isAllowed(uri.host, allowedHosts)) {
                    return null
                }

                return WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    403,
                    "Blocked by safe mode",
                    emptyMap(),
                    ByteArrayInputStream(ByteArray(0)),
                )
            }

            override fun doUpdateVisitedHistory(
                view: WebView,
                url: String?,
                isReload: Boolean,
            ) {
                canGoBack.value = view.canGoBack()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                // Fade in once, so the first load never flashes half-built.
                view.animate().alpha(1f).setDuration(150).start()
            }
        }
    }

    /** Loads the page, unless it is already the one showing. */
    fun load(document: String) {
        if (webView.tag == document) return
        webView.tag = document
        if (firstLoad) {
            firstLoad = false
            webView.alpha = 0f
        }
        webView.loadDataWithBaseURL(
            CustomTabStorage.BASE_URL,
            document,
            "text/html",
            "utf-8",
            null,
        )
    }

    fun attach(activityContext: Context) {
        contextWrapper.setBaseContext(activityContext)
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.onResume()
    }

    fun detach(appContext: Context) {
        webView.onPause()
        (webView.parent as? ViewGroup)?.removeView(webView)
        contextWrapper.setBaseContext(appContext)
    }

    fun destroy() {
        scope.cancel()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }
}

/** Keeps the tab's WebView alive between visits. */
internal object CustomTabBrowserCache {

    private var browser: CustomTabBrowser? = null

    /** Last page the tab showed, so coming back doesn't start from blank. */
    var lastPage: LoadedPage? = null

    fun get(appContext: Context): CustomTabBrowser =
        browser ?: CustomTabBrowser(appContext).also { browser = it }
}

/**
 * Shows a custom page. The tab uses a shared, kept-alive WebView (instant to
 * come back to, and the page keeps its state). Editor previews pass
 * shared = false and get their own, which is thrown away when they close.
 * Either way the page behaves exactly like the real tab: same safe mode
 * rules and the same Koware bridge.
 */
@Composable
internal fun CustomTabWebView(
    document: String,
    theme: String,
    modifier: Modifier = Modifier,
    shared: Boolean = false,
) {
    val appContext = LocalContext.current.applicationContext
    val preferences = remember { Injekt.get<CustomTabPreferences>() }
    val safeMode by preferences.safeMode.collectAsState()
    val allowedHosts by preferences.allowedHosts.collectAsState()
    val background = MaterialTheme.colorScheme.background

    val browser = remember(shared) {
        if (shared) CustomTabBrowserCache.get(appContext) else CustomTabBrowser(appContext)
    }

    SideEffect {
        browser.theme = theme
        browser.safeMode = safeMode
        browser.allowedHosts = allowedHosts
        browser.webView.setBackgroundColor(background.toArgb())
    }

    // Lets back go through pages visited inside the tab (safe mode off).
    BackHandler(enabled = browser.canGoBack.value) {
        browser.webView.goBack()
    }

    AndroidView(
        modifier = modifier,
        factory = { activityContext ->
            browser.attach(activityContext)
            browser.webView
        },
        update = { browser.load(document) },
        onRelease = {
            if (shared) browser.detach(appContext) else browser.destroy()
        },
    )
}

private fun openInBrowser(context: Context, uri: Uri) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
