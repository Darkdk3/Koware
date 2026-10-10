package eu.kanade.tachiyomi.ui.customtab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class ModernPage { Overview, Edit, Preview, Api }

/**
 * Modern editor: the three files are separate numbered parts, each edited on
 * its own screen, so they never look like one script running together.
 */
@Composable
internal fun CustomTabModernEditor(draft: CustomTabDraft) {
    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow
    val theme = rememberCustomTabTheme()

    var page by remember { mutableStateOf(ModernPage.Overview) }
    var partKey by remember { mutableStateOf("js") }
    var saved by remember { mutableStateOf(true) }
    var firstRun by remember { mutableStateOf(true) }

    // Saves by itself a moment after the last change.
    LaunchedEffect(draft.name, draft.html.text, draft.css.text, draft.js.text) {
        if (firstRun) {
            firstRun = false
            return@LaunchedEffect
        }
        saved = false
        delay(700)
        withContext(Dispatchers.IO) { draft.save(context) }
        saved = true
    }

    // Leaving early still keeps the last edits.
    DisposableEffect(Unit) {
        onDispose {
            if (!saved) draft.save(context)
        }
    }

    BackHandler(enabled = page != ModernPage.Overview) {
        page = ModernPage.Overview
    }

    val document = draft.document(theme.vars)

    when (page) {
        ModernPage.Overview -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { CustomTabNameField(draft) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { navigator.pop() }) {
                            Text("Done")
                        }
                    },
                )
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (saved) "All changes saved" else "Saving...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Preview",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { page = ModernPage.Preview }) {
                            Icon(
                                imageVector = Icons.Outlined.Fullscreen,
                                contentDescription = "Open full preview",
                            )
                        }
                    }
                    CustomTabWebView(
                        document = document,
                        theme = theme.json,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Parts",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Three separate files. The app puts them together in " +
                        "order when the tab loads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(12.dp))

                CustomTabParts.forEachIndexed { index, info ->
                    PartCard(
                        info = info,
                        lines = draft.get(info.key).text.count { it == '\n' } + 1,
                        enabled = info.key !in draft.disabled,
                        isLast = index == CustomTabParts.lastIndex,
                        onToggle = { draft.setEnabled(context, info.key, it) },
                        onOpen = {
                            partKey = info.key
                            page = ModernPage.Edit
                        },
                    )
                }

                Card(
                    onClick = { page = ModernPage.Api },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Book,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "API reference",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Koware functions you can use in Behavior",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        ModernPage.Edit -> {
            val info = CustomTabParts.first { it.key == partKey }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "${draft.name.ifBlank { "My tab" }} > ${info.title}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = info.file,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { page = ModernPage.Overview }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                )
                            }
                        },
                        actions = {
                            FilledTonalButton(onClick = { page = ModernPage.Preview }) {
                                Icon(
                                    imageVector = Icons.Outlined.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Preview")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        },
                    )
                },
            ) { contentPadding ->
                Column(
                    modifier = Modifier
                        .padding(contentPadding)
                        .consumeWindowInsets(contentPadding)
                        .imePadding(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StepDot(step = info.step, enabled = true)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Step ${info.step} of ${CustomTabParts.size}. ${info.runNote}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    CodeField(
                        value = draft.get(partKey),
                        onValueChange = { draft.set(partKey, it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(8.dp),
                    )

                    SymbolRow(
                        onInsert = { draft.set(partKey, draft.get(partKey).insert(it)) },
                    )
                }
            }
        }

        ModernPage.Preview -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Preview") },
                    navigationIcon = {
                        IconButton(onClick = { page = ModernPage.Overview }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    },
                )
            },
        ) { contentPadding ->
            Column(modifier = Modifier.padding(contentPadding)) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CustomTabParts.forEach { info ->
                        val on = info.key !in draft.disabled
                        FilterChip(
                            selected = on,
                            onClick = { draft.setEnabled(context, info.key, !on) },
                            label = { Text("${info.step} ${info.title}") },
                            shape = RoundedCornerShape(percent = 50),
                        )
                    }
                }

                CustomTabWebView(
                    document = document,
                    theme = theme.json,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(8.dp),
                )

                Text(
                    text = "Parts that are switched off are skipped, just like when " +
                        "the tab loads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        ModernPage.Api -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("API reference") },
                    navigationIcon = {
                        IconButton(onClick = { page = ModernPage.Overview }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    },
                )
            },
        ) { contentPadding ->
            CustomTabApiReference(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize(),
            )
        }
    }
}

@Composable
private fun StepDot(step: Int, enabled: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = step.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** One numbered part on the overview, joined to the next by a vertical line. */
@Composable
private fun PartCard(
    info: PartInfo,
    lines: Int,
    enabled: Boolean,
    isLast: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StepDot(step = info.step, enabled = enabled)
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(2.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }

        Card(
            onClick = onOpen,
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 10.dp)
                .alpha(if (enabled) 1f else 0.55f),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = info.title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = info.file,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = onToggle)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (enabled) {
                        info.description
                    } else {
                        "Off. Skipped when the tab loads."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$lines lines",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Edit",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
