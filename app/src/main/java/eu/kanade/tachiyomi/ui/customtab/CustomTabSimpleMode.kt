
package eu.kanade.tachiyomi.ui.customtab

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

private const val HTML_START = "<!-- KOWARE_SIMPLE_WIDGETS_START -->"
private const val HTML_END = "<!-- KOWARE_SIMPLE_WIDGETS_END -->"
private const val CSS_START = "/* KOWARE_SIMPLE_WIDGETS_START */"
private const val CSS_END = "/* KOWARE_SIMPLE_WIDGETS_END */"
private const val JS_START = "// KOWARE_SIMPLE_WIDGETS_START"
private const val JS_END = "// KOWARE_SIMPLE_WIDGETS_END"
private const val DATA_PREFIX = "<!-- KOWARE_WIDGET_DATA:"
private const val DATA_SUFFIX = " -->"

private data class SimpleWidget(
    val id: String,
    val type: String,
)

private data class WidgetDefinition(
    val type: String,
    val title: String,
    val description: String,
)

private val widgetDefinitions = listOf(
    WidgetDefinition(
        type = "welcome",
        title = "Welcome card",
        description = "A simple heading and description.",
    ),
    WidgetDefinition(
        type = "clock",
        title = "Digital clock",
        description = "Displays the current time.",
    ),
    WidgetDefinition(
        type = "quick-links",
        title = "Quick links",
        description = "A small row of useful links.",
    ),
    WidgetDefinition(
        type = "reading-stats",
        title = "Reading stats",
        description = "A placeholder card for future reading statistics.",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomTabSimpleMode(
    draft: CustomTabDraft,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    val widgets = remember(draft.saveId) {
        mutableStateListOf<SimpleWidget>().apply {
            addAll(readWidgets(draft.html.text))
        }
    }

    var bucketOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    fun updateDraft() {
        draft.html = draft.html.copy(
            text = replaceSection(
                source = draft.html.text,
                start = HTML_START,
                end = HTML_END,
                content = buildHtmlSection(widgets),
            ),
        )

        draft.css = draft.css.copy(
            text = replaceSection(
                source = draft.css.text,
                start = CSS_START,
                end = CSS_END,
                content = buildCssSection(widgets),
            ),
        )

        draft.js = draft.js.copy(
            text = replaceSection(
                source = draft.js.text,
                start = JS_START,
                end = JS_END,
                content = buildJsSection(widgets),
            ),
        )

        draft.save(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Simple Mode")
                        Text(
                            text = "Build your tab with widgets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    TextButton(onClick = {
                        updateDraft()
                        onBack()
                    }) {
                        Text("Done")
                    }
                },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 6.dp) {
                Button(
                    onClick = { bucketOpen = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Widgets,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Open widget bucket")
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = widgets.size.toString(),
                            modifier = Modifier.padding(
                                horizontal = 9.dp,
                                vertical = 3.dp,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (widgets.isEmpty()) {
            EmptyWidgetState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                onAddWidget = { bucketOpen = true },
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = "YOUR TAB",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Move widgets up or down to change their order.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                itemsIndexed(
                    items = widgets,
                    key = { _, widget -> widget.id },
                ) { index, widget ->
                    val definition = widgetDefinitions.first {
                        it.type == widget.type
                    }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Widgets,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = definition.title,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = definition.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    enabled = index > 0,
                                    onClick = {
                                        val item = widgets.removeAt(index)
                                        widgets.add(index - 1, item)
                                        updateDraft()
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Move up",
                                    )
                                }

                                IconButton(
                                    enabled = index < widgets.lastIndex,
                                    onClick = {
                                        val item = widgets.removeAt(index)
                                        widgets.add(index + 1, item)
                                        updateDraft()
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Move down",
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    widgets.removeAt(index)
                                    updateDraft()
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove ${definition.title}",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                item {
                    TextButton(
                        onClick = { bucketOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add another widget")
                    }
                }
            }
        }
    }

    if (bucketOpen) {
        ModalBottomSheet(
            onDismissRequest = { bucketOpen = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Widget bucket",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Choose a widget to add to your tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                widgetDefinitions.forEach { definition ->
                    val alreadyAdded = widgets.any { it.type == definition.type }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            widgets.add(
                                SimpleWidget(
                                    id = java.util.UUID.randomUUID().toString(),
                                    type = definition.type,
                                ),
                            )
                            updateDraft()
                            bucketOpen = false
                        },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Widgets,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    definition.title,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    definition.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add widget",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyWidgetState(
    modifier: Modifier = Modifier,
    onAddWidget: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ) {
            Icon(
                imageVector = Icons.Default.Widgets,
                contentDescription = null,
                modifier = Modifier
                    .padding(24.dp)
                    .size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Your tab is empty",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Add widgets from the bucket to build your own layout. " +
                "You can remove or rearrange them whenever you want.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

        FilledTonalButton(onClick = onAddWidget) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add your first widget")
        }
    }
}

private fun readWidgets(html: String): List<SimpleWidget> {
    val metadata = Regex(
        Regex.escape(DATA_PREFIX) + "(.*?)" + Regex.escape(DATA_SUFFIX),
        setOf(RegexOption.DOT_MATCHES_ALL),
    ).find(html)?.groupValues?.getOrNull(1) ?: return emptyList()

    return runCatching {
        val array = JSONArray(metadata)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val type = item.optString("type")
                if (widgetDefinitions.any { it.type == type }) {
                    add(
                        SimpleWidget(
                            id = item.optString("id").ifBlank {
                                java.util.UUID.randomUUID().toString()
                            },
                            type = type,
                        ),
                    )
                }
            }
        }
    }.getOrDefault(emptyList())
}

private fun buildHtmlSection(widgets: List<SimpleWidget>): String {
    if (widgets.isEmpty()) return ""

    val metadata = JSONArray().apply {
        widgets.forEach { widget ->
            put(
                JSONObject()
                    .put("id", widget.id)
                    .put("type", widget.type),
            )
        }
    }

    val content = widgets.joinToString("\n") { widget ->
        when (widget.type) {
            "welcome" -> """
                <section class="kw-widget kw-welcome">
                    <h2>Welcome</h2>
                    <p>Your space, your way.</p>
                </section>
            """.trimIndent()

            "clock" -> """
                <section class="kw-widget kw-clock">
                    <div class="kw-clock-time" data-koware-clock>--:--</div>
                    <div class="kw-clock-date" data-koware-date></div>
                </section>
            """.trimIndent()

            "quick-links" -> """
                <section class="kw-widget kw-links">
                    <h3>Quick links</h3>
                    <div class="kw-link-row">
                        <a href="https://github.com/Darkdk3/Koware">Koware</a>
                        <a href="https://github.com/">GitHub</a>
                    </div>
                </section>
            """.trimIndent()

            "reading-stats" -> """
                <section class="kw-widget kw-stats">
                    <h3>Reading stats</h3>
                    <p>Reading statistics can be connected here in a future version.</p>
                </section>
            """.trimIndent()

            else -> ""
        }
    }

    return """
        $HTML_START
        $DATA_PREFIX$metadata$DATA_SUFFIX
        <div class="kw-widget-layout">
        $content
        </div>
        $HTML_END
    """.trimIndent()
}

private fun buildCssSection(widgets: List<SimpleWidget>): String {
    if (widgets.isEmpty()) return ""

    return """
        $CSS_START
        .kw-widget-layout {
            display: grid;
            grid-template-columns: minmax(0, 1fr);
            gap: 12px;
            padding: 12px 0;
        }
        .kw-widget {
            box-sizing: border-box;
            padding: 18px;
            border-radius: 18px;
            background: var(--koware-surface, #2C2D34);
            color: var(--koware-foreground, #E3E2E6);
            overflow-wrap: anywhere;
        }
        .kw-widget h2, .kw-widget h3 {
            margin: 0 0 8px;
        }
        .kw-widget p {
            margin: 4px 0 0;
            opacity: .8;
        }
        .kw-clock-time {
            font-size: 2.3rem;
            font-weight: 600;
            font-variant-numeric: tabular-nums;
        }
        .kw-clock-date {
            opacity: .75;
            margin-top: 4px;
        }
        .kw-link-row {
            display: flex;
            flex-wrap: wrap;
            gap: 10px;
            margin-top: 12px;
        }
        .kw-link-row a {
            color: var(--koware-primary, #6582CE);
            text-decoration: none;
            padding: 8px 12px;
            border-radius: 10px;
            background: rgba(127,127,127,.12);
        }
        $CSS_END
    """.trimIndent()
}

private fun buildJsSection(widgets: List<SimpleWidget>): String {
    if (widgets.none { it.type == "clock" }) return ""

    return """
        $JS_START
        (() => {
            const updateKowareClock = () => {
                const now = new Date();
                document.querySelectorAll('[data-koware-clock]').forEach(el => {
                    el.textContent = now.toLocaleTimeString([], {
                        hour: '2-digit',
                        minute: '2-digit'
                    });
                });
                document.querySelectorAll('[data-koware-date]').forEach(el => {
                    el.textContent = now.toLocaleDateString([], {
                        weekday: 'long',
                        month: 'long',
                        day: 'numeric'
                    });
                });
            };
            updateKowareClock();
            if (window.__kowareClockTimer) {
                clearInterval(window.__kowareClockTimer);
            }
            window.__kowareClockTimer = setInterval(updateKowareClock, 1000);
        })();
        $JS_END
    """.trimIndent()
}

private fun replaceSection(
    source: String,
    start: String,
    end: String,
    content: String,
): String {
    val startIndex = source.indexOf(start)
    val endIndex = if (startIndex >= 0) {
        source.indexOf(end, startIndex + start.length)
    } else {
        -1
    }

    val withoutOld = if (startIndex >= 0 && endIndex >= 0) {
        val afterEnd = endIndex + end.length
        (source.substring(0, startIndex) + source.substring(afterEnd)).trim()
    } else {
        source.trim()
    }

    if (content.isBlank()) return withoutOld
    return if (withoutOld.isBlank()) {
        content
    } else {
        "$withoutOld\n\n$content"
    }
}
