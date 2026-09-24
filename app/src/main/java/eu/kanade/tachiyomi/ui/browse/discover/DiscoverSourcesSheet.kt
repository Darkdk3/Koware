package eu.kanade.tachiyomi.ui.browse.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet

/**
 * Entry point for the Sources picker, shared by the novel and manga Discover tabs.
 * Modern: tonal "Sources · N" button. Legacy: plain icon button.
 */
@Composable
internal fun DiscoverSourcesButton(
    isModern: Boolean,
    sourceCount: Int,
    onClick: () -> Unit,
) {
    if (isModern) {
        FilledTonalButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier.height(32.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Sources · $sourceCount",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    } else {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = "Sources",
            )
        }
    }
}

/**
 * Source picker. Modern: the app's own AdaptiveSheet (picks up the sheet background style,
 * opacity and frosted/grainy blur from the library settings). Legacy: a plain dialog.
 * Ticking boxes only edits a local copy; [onApply] gets the final selection.
 */
@Composable
internal fun DiscoverSourcesSheet(
    isModern: Boolean,
    options: List<DiscoverSourceOption>,
    selected: Set<Long>,
    onDismiss: () -> Unit,
    onApply: (Set<Long>) -> Unit,
) {
    var pending by remember(options, selected) { mutableStateOf(selected) }
    val onToggle = { id: Long -> pending = if (id in pending) pending - id else pending + id }

    if (isModern) {
        AdaptiveSheet(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 20.dp),
            ) {
                Text(
                    text = "Discover sources",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "Choose which sources load into your feed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )

                SourceCheckList(options = options, pending = pending, onToggle = onToggle)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    TextButton(onClick = { pending = options.map { it.id }.toSet() }) {
                        Text("Select all")
                    }
                    TextButton(onClick = { pending = emptySet() }) {
                        Text("Clear")
                    }
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(onClick = { onApply(pending) }) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Apply (${pending.size})")
                    }
                }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Discover sources") },
            text = {
                Column {
                    SourceCheckList(options = options, pending = pending, onToggle = onToggle)
                    Row {
                        TextButton(onClick = { pending = options.map { it.id }.toSet() }) {
                            Text("Select all")
                        }
                        TextButton(onClick = { pending = emptySet() }) {
                            Text("Clear")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onApply(pending) }) {
                    Text("Apply (${pending.size})")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SourceCheckList(
    options: List<DiscoverSourceOption>,
    pending: Set<Long>,
    onToggle: (Long) -> Unit,
) {
    if (options.isEmpty()) {
        Text(
            text = "No sources installed yet.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        return
    }

    Column(
        modifier = Modifier
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        options.forEach { option ->
            val checked = option.id in pending
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { onToggle(option.id) },
            ) {
                Checkbox(checked = checked, onCheckedChange = null)
                Spacer(Modifier.width(14.dp))
                Text(
                    text = option.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (option.isPinned) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "Pinned",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
