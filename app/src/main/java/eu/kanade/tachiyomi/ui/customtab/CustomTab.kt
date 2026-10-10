package eu.kanade.tachiyomi.ui.customtab

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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.util.Tab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
        val theme = rememberCustomTabTheme()

        // Reading the files happens off the main thread, so opening the tab
        // never waits on storage. The last page is reused until the new one is
        // ready, so coming back to the tab doesn't flash.
        val page by produceState<LoadedPage?>(
            initialValue = CustomTabBrowserCache.lastPage,
            revision,
            theme.vars,
        ) {
            val loaded = withContext(Dispatchers.IO) {
                val id = CustomTabStorage.activeId(context)
                LoadedPage(
                    activeId = id,
                    document = if (id == null) {
                        ""
                    } else {
                        CustomTabStorage.buildDocument(context, theme.vars)
                    },
                )
            }
            CustomTabBrowserCache.lastPage = loaded
            value = loaded
        }

        val loaded = page
        val activeId = loaded?.activeId

        // statusBarsPadding keeps the page and the buttons below the status
        // bar, like the toolbar on the Novels tab.
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            when {
                loaded == null -> Unit

                activeId == null -> EmptyState(
                    onOpenEditor = {
                        val save = CustomTabStorage.create(context, "My tab")
                        navigator.push(CustomTabEditorScreen(save.id))
                    },
                )

                else -> CustomTabWebView(
                    document = loaded.document,
                    theme = theme.json,
                    shared = true,
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
                            colors.surfaceVariant.copy(alpha = 0.85f),
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
                        colors.surfaceVariant.copy(alpha = 0.85f),
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

/** App colors, as CSS variables for the page and as JSON for Koware.getTheme(). */
internal class CustomTabTheme(val vars: String, val json: String)

@Composable
internal fun rememberCustomTabTheme(): CustomTabTheme {
    val colors = MaterialTheme.colorScheme

    val vars = ":root{" +
        "--bg:${colors.background.toCss()};" +
        "--fg:${colors.onBackground.toCss()};" +
        "--surface:${colors.surfaceVariant.toCss()};" +
        "--muted:${colors.onSurfaceVariant.toCss()};" +
        "--primary:${colors.primary.toCss()}}"

    val json = JSONObject()
        .put("isDark", colors.background.luminance() < 0.5f)
        .put("background", colors.background.toCss())
        .put("foreground", colors.onBackground.toCss())
        .put("surface", colors.surfaceVariant.toCss())
        .put("muted", colors.onSurfaceVariant.toCss())
        .put("primary", colors.primary.toCss())
        .toString()

    return remember(vars, json) { CustomTabTheme(vars, json) }
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

private fun Color.toCss(): String = "#%06X".format(0xFFFFFF and toArgb())
