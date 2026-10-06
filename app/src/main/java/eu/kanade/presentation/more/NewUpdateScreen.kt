package eu.kanade.presentation.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.ui.more.NewUpdateScreenModel

private enum class Kind(val label: String, val emoji: String, val color: Color) {
    NEW("New", "✨", Color(0xFF7CE0A3)),
    FIXED("Fixes", "🐛", Color(0xFFFFB86B)),
}

private data class Entry(val kind: Kind, val text: String)

private val conventionalPrefix =
    Regex("^(feat|fix|perf|refactor|chore|docs|style|build|ci)(\\([^)]*\\))?!?:\\s*", RegexOption.IGNORE_CASE)

/** "- feat: ..." -> New, anything else -> Fixes. Only bullet lines count. */
private fun parseEntries(raw: String): List<Entry> {
    return raw.lines()
        .map { it.trim() }
        .filter { it.startsWith("- ") || it.startsWith("* ") }
        .map { line ->
            val clean = line.drop(2).trim()
            val match = conventionalPrefix.find(clean)
            val tag = match?.groupValues?.get(1)?.lowercase()
            val body = (if (match != null) clean.removeRange(match.range) else clean).trim()
            val lower = body.lowercase()
            val kind = when {
                tag == "feat" -> Kind.NEW
                tag != null -> Kind.FIXED
                lower.startsWith("add") || lower.startsWith("new") || lower.startsWith("create") ||
                    lower.startsWith("introduce") || lower.startsWith("implement") ||
                    lower.startsWith("support") -> Kind.NEW
                else -> Kind.FIXED
            }
            Entry(kind, body.replaceFirstChar { it.uppercase() })
        }
        .filter { it.text.isNotBlank() }
}

@Composable
fun NewUpdateScreen(
    versionName: String,
    stage: NewUpdateScreenModel.Stage,
    downloadProgress: () -> Int,
    changelogInfo: String,
    onOpenInBrowser: () -> Unit,
    onAcceptUpdate: () -> Unit,
    onRejectUpdate: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val hazeState = remember { HazeState() }
    val sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    val entries = remember(changelogInfo) { parseEntries(changelogInfo) }
    val progress = downloadProgress().coerceIn(0, 100)
    val brand = Brush.horizontalGradient(listOf(colors.primary, colors.tertiary))
    val disabledBrush = Brush.horizontalGradient(
        listOf(colors.onSurface.copy(alpha = 0.14f), colors.onSurface.copy(alpha = 0.14f)),
    )
    val downloading = stage == NewUpdateScreenModel.Stage.Downloading

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        // Hero background. This is also what the glass sheet blurs.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.primary.copy(alpha = 0.55f),
                            colors.tertiary.copy(alpha = 0.32f),
                            colors.background,
                        ),
                    ),
                ),
        ) {
            GlowBlob(Modifier.align(Alignment.TopStart).offset(x = (-80).dp, y = (-40).dp), colors.primary, 320.dp)
            GlowBlob(Modifier.align(Alignment.TopEnd).offset(x = 70.dp, y = 120.dp), colors.tertiary, 280.dp)
            GlowBlob(Modifier.align(Alignment.CenterStart).offset(x = (-60).dp, y = 40.dp), colors.secondary, 240.dp)
        }

        // Hero content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val iconShape = RoundedCornerShape(16.dp)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(iconShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), iconShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NewReleases,
                        contentDescription = null,
                        tint = colors.onSurface,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Chip("Koware", Color.White.copy(alpha = 0.2f), colors.onSurface)
            }

            Spacer(Modifier.height(32.dp))
            Text(
                text = "New version\nis ready",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("r${BuildConfig.COMMIT_COUNT}", Color.White.copy(alpha = 0.14f), colors.onSurface)
                Text("→", color = colors.primary)
                Chip(versionName, colors.primary.copy(alpha = 0.28f), colors.onSurface)
            }
        }

        // Glass bottom-sheet style panel
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.66f)
                .clip(sheetShape)
                .hazeEffect(state = hazeState) {
                    style = HazeStyle(
                        backgroundColor = colors.surface,
                        tint = HazeTint(colors.surfaceContainer.copy(alpha = 0.40f)),
                        blurRadius = 28.dp,
                        noiseFactor = 0f,
                    )
                }
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.08f)),
                    ),
                    sheetShape,
                ),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(colors.onSurface.copy(alpha = 0.3f)),
            )
            Text(
                text = "What changed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 10.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                if (entries.isEmpty()) {
                    Text(
                        text = changelogInfo.trim().ifBlank { "No changelog provided for this build." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                    )
                } else {
                    Kind.entries.forEach { kind ->
                        val items = entries.filter { it.kind == kind }
                        if (items.isNotEmpty()) {
                            Chip(
                                text = "${kind.emoji} ${kind.label} · ${items.size}",
                                background = kind.color.copy(alpha = 0.20f),
                                foreground = kind.color,
                                bold = true,
                            )
                            Spacer(Modifier.height(8.dp))
                            items.forEach { entry ->
                                EntryRow(entry)
                                Spacer(Modifier.height(7.dp))
                            }
                            Spacer(Modifier.height(7.dp))
                        }
                    }
                }
            }

            // Footer
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(top = 10.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 10.dp),
            ) {
                if (stage == NewUpdateScreenModel.Stage.Failed) {
                    Text(
                        text = "Download failed. Check your connection and retry.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (downloading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(colors.onSurface.copy(alpha = 0.14f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress / 100f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(brand),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onRejectUpdate) { Text("Later") }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (downloading) disabledBrush else brand)
                            .clickable(enabled = !downloading, onClick = onAcceptUpdate)
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when (stage) {
                                NewUpdateScreenModel.Stage.Available -> "Download update"
                                NewUpdateScreenModel.Stage.Downloading -> "Downloading… $progress%"
                                NewUpdateScreenModel.Stage.Downloaded -> "Install now"
                                NewUpdateScreenModel.Stage.Failed -> "Retry download"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (downloading) colors.onSurface else colors.onPrimary,
                        )
                    }
                }
                TextButton(
                    onClick = onOpenInBrowser,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("View on GitHub")
                }
            }
        }
    }
}

@Composable
private fun GlowBlob(modifier: Modifier, color: Color, size: Dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent))),
    )
}

@Composable
private fun Chip(text: String, background: Color, foreground: Color, bold: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 11.dp, vertical = 4.dp),
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
private fun EntryRow(entry: Entry) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(entry.kind.color),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = entry.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}