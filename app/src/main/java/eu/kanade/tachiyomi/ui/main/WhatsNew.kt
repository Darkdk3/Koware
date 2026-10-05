package eu.kanade.tachiyomi.ui.main

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "whats_new"
private const val KEY_LAST_SEEN_BUILD = "last_seen_build"
private const val NOTES_ASSET = "whatsnew.md"

/**
 * Shows a one-time "What's new" dialog the first time a newer build is opened.
 * Notes come from assets/whatsnew.md, which CI generates from commit titles.
 */
@Composable
fun ShowWhatsNew() {
    val context = LocalContext.current
    var notes by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = BuildConfig.COMMIT_COUNT.toIntOrNull() ?: return@LaunchedEffect
        val lastSeen = prefs.getInt(KEY_LAST_SEEN_BUILD, -1)

        if (lastSeen == current) return@LaunchedEffect

        // Brand-new install: nothing is "new" yet, just remember this build.
        if (lastSeen == -1 && isFreshInstall(context)) {
            prefs.edit().putInt(KEY_LAST_SEEN_BUILD, current).apply()
            return@LaunchedEffect
        }

        val text = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(NOTES_ASSET).bufferedReader().use { it.readText() }
            }.getOrNull()
        }

        // Mark as seen right away so it can never show twice.
        prefs.edit().putInt(KEY_LAST_SEEN_BUILD, current).apply()

        if (!text.isNullOrBlank()) notes = text.trim()
    }

    notes?.let { text ->
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        AlertDialog(
            onDismissRequest = { notes = null },
            title = { Text("What's new in ${BuildConfig.VERSION_NAME}") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    lines.forEach { line ->
                        Text(
                            text = if (line.startsWith("- ")) "• ${line.removePrefix("- ")}" else line,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { notes = null }) {
                    Text("Got it")
                }
            },
        )
    }
}

private fun isFreshInstall(context: Context): Boolean {
    return runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime - info.firstInstallTime < 5_000
    }.getOrDefault(true)
}
