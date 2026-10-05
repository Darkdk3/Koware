package eu.kanade.tachiyomi.ui.main

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.updater.RELEASE_URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.presentation.core.util.LocalHazeState

private const val PREFS_NAME = "whats_new"
private const val KEY_LAST_SEEN_BUILD = "last_seen_build"
private const val NOTES_ASSET = "whatsnew.md"

private enum class ChangeKind(val label: String, val emoji: String, val color: Color) {
    NEW("New", "✨", Color(0xFF7CE0A3)),
    FIXED("Fixes", "🐛", Color(0xFFFFB86B)),
}

private data class Change(val kind: ChangeKind, val text: String)

private val conventionalPrefix =
    Regex("^(feat|fix|perf|refactor|chore|docs|style|build|ci)(\\([^)]*\\))?!?:\\s*", RegexOption.IGNORE_CASE)

/**
 * "- feat: ..." (CI: commit added a brand-new file) -> New.
 * Anything else (CI writes "- fix: ...") -> Fixes.
 * Lines without a prefix fall back to the first word: add/new/create... -> New, else Fixes.
 */
private fun parseChange(raw: String): Change {
    val line = raw.trim().removePrefix("- ").trim()
    val match = conventionalPrefix.find(line)
    val tag = match?.groupValues?.get(1)?.lowercase()
    val body = (if (match != null) line.removeRange(match.range) else line).trim()
    val lower = body.lowercase()

    val kind = when {
        tag == "feat" -> ChangeKind.NEW
        tag != null -> ChangeKind.FIXED
        lower.startsWith("add") || lower.startsWith("new") || lower.startsWith("create") ||
            lower.startsWith("introduce") || lower.startsWith("implement") ||
            lower.startsWith("support") -> ChangeKind.NEW
        else -> ChangeKind.FIXED
    }
    return Change(kind, body.replaceFirstChar { it.uppercase() })
}

/**
 * Shows a one-time "What's new" overlay the first time a newer build is opened.
 * Notes come from assets/whatsnew.md, which CI generates from the commits.
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
        WhatsNewOverlay(text = text, onDismiss = { notes = null })
    }
}

@Composable
private fun WhatsNewOverlay(text: String, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)

    val hazeState = LocalHazeState.current
    val uriHandler = LocalUriHandler.current
    val shape = RoundedCornerShape(28.dp)
    val colors = MaterialTheme.colorScheme
    val enter = remember { MutableTransitionState(false).apply { targetState = true } }
    val changes = remember(text) {
        text.lines().filter { it.isNotBlank() }.map(::parseChange)
    }

    // Same frosted-glass recipe as the navigation bar.
    val glass = if (hazeState != null) {
        Modifier.hazeEffect(state = hazeState) {
            style = HazeStyle(
                backgroundColor = colors.surface,
                tint = HazeTint(colors.surfaceContainer.copy(alpha = 0.35f)),
                blurRadius = 28.dp,
                noiseFactor = 0f,
            )
        }
    } else {
        Modifier.background(colors.surfaceContainer.copy(alpha = 0.92f))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visibleState = enter,
            enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.92f, animationSpec = tween(250)),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .widthIn(max = 380.dp)
                    .clip(shape)
                    .then(glass)
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.08f)),
                        ),
                        shape,
                    )
                    // Swallow taps on the card so they don't dismiss the overlay.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                // Accent strip
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Brush.horizontalGradient(listOf(colors.primary, colors.tertiary, colors.secondary))),
                )

                Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)) {
                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(colors.primary, colors.tertiary))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "K",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.onPrimary,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Koware updated",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface,
                            )
                            Text(
                                text = "Here's what changed",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurface.copy(alpha = 0.7f),
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Info chips
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("v${BuildConfig.VERSION_NAME}", colors.primary.copy(alpha = 0.22f), colors.primary)
                        Chip("Build r${BuildConfig.COMMIT_COUNT}", Color.White.copy(alpha = 0.12f), colors.onSurface)
                        Chip(
                            "${changes.size} ${if (changes.size == 1) "change" else "changes"}",
                            Color.White.copy(alpha = 0.12f),
                            colors.onSurface,
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.14f)),
                    )
                    Spacer(Modifier.height(14.dp))

                    // Grouped changes
                    Column(
                        modifier = Modifier
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        ChangeKind.entries.forEach { kind ->
                            val items = changes.filter { it.kind == kind }
                            if (items.isNotEmpty()) {
                                Chip(
                                    text = "${kind.emoji} ${kind.label} · ${items.size}",
                                    background = kind.color.copy(alpha = 0.20f),
                                    foreground = kind.color,
                                    bold = true,
                                )
                                Spacer(Modifier.height(8.dp))
                                items.forEach { change ->
                                    ChangeRow(change)
                                    Spacer(Modifier.height(7.dp))
                                }
                                Spacer(Modifier.height(7.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Footer
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { runCatching { uriHandler.openUri(RELEASE_URL) } }) {
                            Text("Full changelog")
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Brush.horizontalGradient(listOf(colors.primary, colors.tertiary)))
                                .clickable(onClick = onDismiss)
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Got it",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, background: Color, foreground: Color, bold: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
            color = foreground,
        )
    }
}

@Composable
private fun ChangeRow(change: Change) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(change.kind.color),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = change.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun isFreshInstall(context: Context): Boolean {
    return runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime - info.firstInstallTime < 5_000
    }.getOrDefault(true)
}