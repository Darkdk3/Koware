package eu.kanade.tachiyomi.ui.customtab

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.util.Tab
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayInputStream

data object CustomTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val preferences = remember { Injekt.get<CustomTabPreferences>() }
            val name by preferences.name.collectAsState()

            return TabOptions(
                index = 6u,
                title = name,
                icon = rememberVectorPainter(Icons.Outlined.Code),
            )
        }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val colors = MaterialTheme.colorScheme
        val preferences = remember { Injekt.get<CustomTabPreferences>() }

        val revision by CustomTabStorage.revision.collectAsState()
        val safeMode by preferences.safeMode.collectAsState()
        val allowedHosts by preferences.allowedHosts.collectAsState()
        val activeId = remember(revision) { CustomTabStorage.activeId(context) }

        // App theme colors exposed to the page as CSS variables.
        val themeVars = ":root{" +
            "--bg:${colors.background.toCss()};" +
            "--fg:${colors.onBackground.toCss()};" +
            "--surface:${colors.surfaceVariant.toCss()};" +
            "--muted:${colors.onSurfaceVariant.toCss()};" +
            "--primary:${colors.primary.toCss()}}"

        // Safe mode settings are part of the key so the page reloads when
        // they change.
        val document = remember(revision, themeVars, safeMode, allowedHosts) {
            CustomTabStorage.buildDocument(context, themeVars)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (activeId == null) {
                EmptyState(
                    onOpenEditor = {
                        val save = CustomTabStorage.create(context, "My tab")
                        navigator.push(CustomTabEditorScreen(save.id))
                    },
                )
            } else {
                CustomTabWebView(
                    document = document,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Always visible, so saves and safe mode are one tap away.
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (activeId != null) {
                    IconButton(
                        onClick = {
                            navigator.push(CustomTabEditorScreen(activeId))
                        },
                        modifier = Modifier.background(
                            colors.surface.copy(alpha = 0.7f),
                            CircleShape,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit this save",
                        )
                    }
                }
                IconButton(
                    onClick = { navigator.push(CustomTabSettingsScreen) },
                    modifier = Modifier.background(
                        colors.surface.copy(alpha = 0.7f),
                        CircleShape,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Custom tab settings",
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onOpenEditor: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Code,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Build your own tab",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Add HTML, CSS, or JavaScript to make this tab yours.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onOpenEditor) {
                Text("Open editor")
            }
            // Import comes in a later phase.
            OutlinedButton(onClick = { }, enabled = false) {
                Text("Import files")
            }
        }
    }
}

@Composable
private fun CustomTabWebView(
    document: String,
    modifier: Modifier = Modifier,
) {
    val preferences = remember { Injekt.get<CustomTabPreferences>() }
    val webViewHolder = remember { arrayOfNulls<WebView>(1) }
    var canGoBack by remember { mutableStateOf(false) }

    // Lets back go through pages visited inside the tab (safe mode off).
    BackHandler(enabled = canGoBack) {
        webViewHolder[0]?.goBack()
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                webViewHolder[0] = this
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false

                webViewClient = object : WebViewClient() {

                    // Main-frame navigation (taps on links).
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val uri = request.url
                        if (uri.host == CustomTabStorage.BASE_HOST) return false

                        // Never follow intent:, tel:, and similar schemes.
                        if (uri.scheme != "http" && uri.scheme != "https") {
                            return true
                        }

                        // Safe mode off: free browsing inside the tab.
                        if (!preferences.safeMode.get()) return false

                        val allowed = uri.scheme == "https" &&
                            CustomTabSafety.isAllowed(
                                uri.host,
                                preferences.allowedHosts.get(),
                            )
                        if (allowed) return false

                        openInBrowser(context, uri)
                        return true
                    }

                    // Every request, including iframes, images, and scripts.
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        if (!preferences.safeMode.get()) return null

                        val uri = request.url
                        if (uri.scheme != "http" && uri.scheme != "https") {
                            return null
                        }

                        val allowed = uri.scheme == "https" &&
                            CustomTabSafety.isAllowed(
                                uri.host,
                                preferences.allowedHosts.get(),
                            )
                        if (allowed) return null

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
                        canGoBack = view.canGoBack()
                    }
                }
            }
        },
        update = { view ->
            // Only reload when the document actually changed.
            if (view.tag != document) {
                view.tag = document
                view.loadDataWithBaseURL(
                    CustomTabStorage.BASE_URL,
                    document,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
        onRelease = {
            webViewHolder[0] = null
            it.destroy()
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

private fun Color.toCss(): String = "#%06X".format(0xFFFFFF and toArgb())
