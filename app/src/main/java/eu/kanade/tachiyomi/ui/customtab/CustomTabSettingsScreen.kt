package eu.kanade.tachiyomi.ui.customtab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.PreferenceScreen
import eu.kanade.presentation.util.Screen
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object CustomTabSettingsScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val prefs = remember { Injekt.get<CustomTabPreferences>() }

        val revision by CustomTabStorage.revision.collectAsState()
        val saves = remember(revision) { CustomTabStorage.listSaves(context) }
        val activeId = remember(revision) { CustomTabStorage.activeId(context) }

        val tabName by prefs.name.collectAsState()
        val safeMode by prefs.safeMode.collectAsState()
        val hosts by prefs.allowedHosts.collectAsState()

        var showNameDialog by remember { mutableStateOf(false) }
        var showSafeDialog by remember { mutableStateOf(false) }
        var deleteTarget by remember {
            mutableStateOf<CustomTabStorage.Save?>(null)
        }

        val safeSwitch = Preference.PreferenceItem.SwitchPreference(
            preference = prefs.safeMode,
            title = "Safe mode",
            subtitle = if (safeMode) {
                "Only allowed sites can load"
            } else {
                "Any site can load"
            },
            onValueChanged = { on ->
                if (on) {
                    true
                } else {
                    // Ask first; the dialog turns it off if confirmed.
                    showSafeDialog = true
                    false
                }
            },
        )

        val allowedSites = Preference.PreferenceItem.TextPreference(
            title = "Allowed sites",
            subtitle = when (hosts.size) {
                0 -> "None"
                1 -> hosts.first()
                else -> "${hosts.sorted().first()} and ${hosts.size - 1} more"
            },
            onClick = { navigator.push(CustomTabAllowedSitesScreen) },
        )

        val items = listOf(
            Preference.PreferenceGroup(
                title = "Tab",
                preferenceItems = listOf(
                    Preference.PreferenceItem.TextPreference(
                        title = "Tab name",
                        subtitle = tabName,
                        icon = Icons.Outlined.Edit,
                        onClick = { showNameDialog = true },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = "Saves",
                preferenceItems = listOf(
                    Preference.PreferenceItem.CustomPreference(
                        title = "Saves",
                        content = {
                            SavesSection(
                                saves = saves,
                                activeId = activeId,
                                onSelect = { CustomTabStorage.setActive(context, it.id) },
                                onAdd = {
                                    val save = CustomTabStorage.create(
                                        context,
                                        "New save",
                                    )
                                    navigator.push(CustomTabEditorScreen(save.id))
                                },
                                onEdit = {
                                    navigator.push(CustomTabEditorScreen(it.id))
                                },
                                onDuplicate = {
                                    CustomTabStorage.duplicate(context, it.id)
                                },
                                onDelete = { deleteTarget = it },
                            )
                        },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = "Security",
                preferenceItems = if (safeMode) {
                    listOf(safeSwitch, allowedSites)
                } else {
                    listOf(safeSwitch)
                },
            ),
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Custom tab") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    },
                )
            },
        ) { contentPadding ->
            PreferenceScreen(
                items = items,
                contentPadding = contentPadding,
            )
        }

        if (showNameDialog) {
            NameDialog(
                initial = tabName,
                onDismiss = { showNameDialog = false },
                onConfirm = {
                    prefs.name.set(it)
                    showNameDialog = false
                },
            )
        }

        if (showSafeDialog) {
            AlertDialog(
                onDismissRequest = { showSafeDialog = false },
                title = { Text("Turn off safe mode?") },
                text = {
                    Text(
                        "The tab will be able to open any site. " +
                            "Only turn this off if you trust the pages you load.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            prefs.safeMode.set(false)
                            showSafeDialog = false
                        },
                    ) {
                        Text("Turn off")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSafeDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        deleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Delete \"${target.name}\"?") },
                text = { Text("This removes its HTML, CSS, and JS.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            CustomTabStorage.delete(context, target.id)
                            deleteTarget = null
                        },
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavesSection(
    saves: List<CustomTabStorage.Save>,
    activeId: String?,
    onSelect: (CustomTabStorage.Save) -> Unit,
    onAdd: () -> Unit,
    onEdit: (CustomTabStorage.Save) -> Unit,
    onDuplicate: (CustomTabStorage.Save) -> Unit,
    onDelete: (CustomTabStorage.Save) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Each save keeps its own HTML, CSS, and JS.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onAdd,
                modifier = Modifier.background(
                    MaterialTheme.colorScheme.secondaryContainer,
                    CircleShape,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Add save",
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (saves.isEmpty()) {
            Text(
                text = "No saves yet. Tap + to create one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            saves.forEach { save ->
                val selected = save.id == activeId
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(save) },
                    label = { Text(save.name) },
                    shape = RoundedCornerShape(percent = 50),
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }

        val active = saves.firstOrNull { it.id == activeId }
        if (active != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onEdit(active) }) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }
                TextButton(onClick = { onDuplicate(active) }) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Duplicate")
                }
                TextButton(onClick = { onDelete(active) }) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun NameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tab name") },
        text = {
            OutlinedTextField(
                value = draft,
                // Short, so the label fits in the navigation bar.
                onValueChange = { draft = it.take(12) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(draft.trim()) },
                enabled = draft.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

object CustomTabAllowedSitesScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val prefs = remember { Injekt.get<CustomTabPreferences>() }
        val hosts by prefs.allowedHosts.collectAsState()

        var input by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Allowed sites") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    },
                )
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            error = null
                        },
                        label = { Text("Add a site") },
                        placeholder = { Text("example.com") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { message -> { Text(message) } },
                        modifier = Modifier.weight(1f),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val host = CustomTabSafety.normalizeHost(input)
                            if (host == null) {
                                error = "Enter a valid site, like example.com"
                            } else {
                                prefs.allowedHosts.set(hosts + host)
                                input = ""
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("Add")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Subdomains are allowed too, over https only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (hosts.isEmpty()) {
                    Text(
                        text = "No sites allowed. Everything except your own page is blocked.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn {
                        items(hosts.sorted(), key = { it }) { host ->
                            ListItem(
                                headlineContent = { Text(host) },
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            prefs.allowedHosts.set(hosts - host)
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = "Remove $host",
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
