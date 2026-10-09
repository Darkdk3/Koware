package eu.kanade.tachiyomi.ui.customtab

import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

        val revision by CustomTabStorage.revision.collectAsState()
        val hasContent = remember(revision) { CustomTabStorage.hasContent(context) }

        if (!hasContent) {
            EmptyState(onOpenEditor = { navigator.push(CustomTabEditorScreen) })
            return
        }

        // App theme colors exposed to the page as CSS variables.
        val themeVars = ":root{" +
            "--bg:${colors.background.toCss()};" +
            "--fg:${colors.onBackground.toCss()};" +
            "--surface:${colors.surfaceVariant.toCss()};" +
            "--muted:${colors.onSurfaceVariant.toCss()};" +
            "--primary:${colors.primary.toCss()}}"

        val document = remember(revision, themeVars) {
            CustomTabStorage.buildDocument(context, themeVars)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            CustomTabWebView(
                document = document,
                modifier = Modifier.fillMaxSize(),
            )

            IconButton(
                onClick = { navigator.push(CustomTabEditorScreen) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(colors.surface.copy(alpha = 0.7f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Edit custom tab",
                )
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
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val uri = request.url
                        if (uri.host == CustomTabStorage.BASE_HOST) return false

                        // Anything else opens in the browser, never in the tab.
                        if (uri.scheme == "http" || uri.scheme == "https") {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, uri)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                        return true
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
        onRelease = { it.destroy() },
    )
}

private fun Color.toCss(): String = "#%06X".format(0xFFFFFF and toArgb())
