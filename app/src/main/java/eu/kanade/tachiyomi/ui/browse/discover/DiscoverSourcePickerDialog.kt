// FILE: app/src/main/java/eu/kanade/tachiyomi/ui/browse/discover/DiscoverSourcePickerDialog.kt

package eu.kanade.tachiyomi.ui.browse.discover

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.CatalogueSource

/**
 * Lets the user pick which novel sources feed the Discover tab —
 * same idea as TachiyomiSY/Komikku's per-source toggle on feed-style screens.
 */
@Composable
fun DiscoverSourcePickerDialog(
    availableSources: List<CatalogueSource>,
    enabledSourceKeys: Set<String>,
    onToggleSource: (CatalogueSource) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discover sources") },
        text = {
            LazyColumn {
                items(availableSources, key = { it.id }) { source ->
                    val isEnabled = source.id.toString() in enabledSourceKeys
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = isEnabled,
                            onCheckedChange = { onToggleSource(source) },
                        )
                        Text(source.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}
