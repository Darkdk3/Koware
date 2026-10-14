package eu.kanade.tachiyomi.ui.customtab

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Expandable API reference for the methods registered in CustomTabApi.
 */
@Composable
fun CustomTabApiReference(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val expandedSections = remember {
        mutableStateMapOf<String, Boolean>()
    }

    val sections = remember {
        CustomTabApi.methods.groupBy { it.category }.toList()
    }

    fun copyText(label: String, value: String) {
        val clipboard = context.getSystemService(
            Context.CLIPBOARD_SERVICE,
        ) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText(label, value),
        )

        Toast.makeText(
            context,
            "$label copied",
            Toast.LENGTH_SHORT,
        ).show()
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Koware API Explorer",
                style = MaterialTheme.typography.headlineSmall,
            )

            Text(
                text = "API v${CustomTabApi.VERSION} · " +
                    "${CustomTabApi.methods.size} methods",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "Expand a section to inspect methods, parameters, " +
                    "return values, and examples. Copy the complete " +
                    "reference or copy individual sections and methods.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Button(
                onClick = {
                    copyText(
                        "Koware API reference",
                        buildCompleteReference(),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Copy Everything")
            }

            Text(
                text = "Copies the full reference into one text document.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                items = sections,
                key = { it.first },
            ) { (category, methods) ->
                val expanded = expandedSections[category] ?: false

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedSections[category] = !expanded
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.titleMedium,
                                )

                                Text(
                                    text = "${methods.size} methods",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Text(
                                text = if (expanded) "Collapse ▲" else "Expand ▼",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        if (expanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                TextButton(
                                    onClick = {
                                        copyText(
                                            "$category API reference",
                                            methods.joinToString("\n\n") {
                                                methodDocumentation(it)
                                            },
                                        )
                                    },
                                ) {
                                    Text("Copy this section")
                                }

                                methods.forEach { method ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = method.signature,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.primary,
                                            )

                                            Text(
                                                text = method.description,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )

                                            Text(
                                                text = "Parameters",
                                                style = MaterialTheme.typography.labelLarge,
                                            )

                                            Text(
                                                text = method.parameters,
                                                style = MaterialTheme.typography.bodySmall,
                                            )

                                            Text(
                                                text = "Returns",
                                                style = MaterialTheme.typography.labelLarge,
                                            )

                                            Text(
                                                text = method.returns,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                            )

                                            Text(
                                                text = "Example",
                                                style = MaterialTheme.typography.labelLarge,
                                            )

                                            Text(
                                                text = method.example,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        MaterialTheme.colorScheme.surfaceVariant,
                                                        RoundedCornerShape(8.dp),
                                                    )
                                                    .padding(10.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                            )

                                            TextButton(
                                                onClick = {
                                                    copyText(
                                                        method.name,
                                                        methodDocumentation(method),
                                                    )
                                                },
                                            ) {
                                                Text("Copy method")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "This reference describes methods registered " +
                        "in the current bridge. It does not imply access " +
                        "to APIs that have not been implemented.",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun methodDocumentation(
    method: CustomTabApi.Method,
): String = buildString {
    appendLine(method.signature)
    appendLine("Category: ${method.category}")
    appendLine()
    appendLine("Description:")
    appendLine(method.description)
    appendLine()
    appendLine("Parameters:")
    appendLine(method.parameters)
    appendLine()
    appendLine("Returns:")
    appendLine(method.returns)
    appendLine()
    appendLine("Example:")
    appendLine(method.example)
}

private fun buildCompleteReference(): String = buildString {
    appendLine("KOWARE JAVASCRIPT API REFERENCE")
    appendLine("API version: ${CustomTabApi.VERSION}")
    appendLine("Registered methods: ${CustomTabApi.methods.size}")
    appendLine()
    appendLine("All methods return promises. Use await inside an async function.")
    appendLine()

    CustomTabApi.methods.forEach { method ->
        appendLine(methodDocumentation(method))
        appendLine("=".repeat(60))
        appendLine()
    }

    appendLine("END OF REFERENCE")
}