package eu.kanade.tachiyomi.ui.customtab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.UiStyle
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Entry point for editing a save. Follows the app's UI style: Legacy shows
 * every file as a tab on one screen, Modern shows the files as separate parts.
 */
class CustomTabEditorScreen(
    private val saveId: String,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val uiStyle by uiPreferences.uiStyle.collectAsState()

        // Plain remember (not rememberSaveable): large text can overflow the
        // saved-state bundle and crash the app.
        val draft = remember { CustomTabDraft(context, saveId) }

        if (uiStyle == UiStyle.MODERN) {
            CustomTabModernEditor(draft)
        } else {
            CustomTabLegacyEditor(draft)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomTabLegacyEditor(draft: CustomTabDraft) {
    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow
    val theme = rememberCustomTabTheme()

    var selected by remember { mutableStateOf(0) }
    var showPreview by remember { mutableStateOf(false) }

    val tabs = listOf("HTML", "CSS", "JS", "API")

    Scaffold(
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
                    IconButton(onClick = { showPreview = true }) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = "Preview",
                        )
                    }
                    TextButton(
                        onClick = {
                            draft.save(context)
                            context.toast("Saved")
                            navigator.pop()
                        },
                    ) {
                        Text("Save")
                    }
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
            TabRow(selectedTabIndex = selected) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selected == index,
                        onClick = { selected = index },
                        text = { Text(title) },
                    )
                }
            }

            if (selected == 3) {
                CustomTabApiReference(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                val part = CustomTabParts[selected]

                // Parts switched off in the Modern editor stay off here too.
                if (part.key in draft.disabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "This part is switched off, so the tab skips it.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { draft.setEnabled(context, part.key, true) },
                        ) {
                            Text("Turn on")
                        }
                    }
                }

                CodeField(
                    value = draft.get(part.key),
                    onValueChange = { draft.set(part.key, it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(8.dp),
                )

                SymbolRow(
                    onInsert = { draft.set(part.key, draft.get(part.key).insert(it)) },
                )
            }
        }
    }

    if (showPreview) {
        ModalBottomSheet(
            onDismissRequest = { showPreview = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Text(
                text = "Preview",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            CustomTabWebView(
                document = draft.document(theme.vars),
                theme = theme.json,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp)
                    .padding(16.dp),
            )
        }
    }
}
