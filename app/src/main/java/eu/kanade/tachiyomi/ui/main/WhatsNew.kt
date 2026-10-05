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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import eu.kanade.tachiyomi.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.presentation.core.util.LocalHazeState

private const val PREFS_NAME = "whats_new"
private const val KEY_LAST_SEEN_BUILD = "last_seen_build"
private const val NOTES_ASSET = "whatsnew.md"

/**
 * Shows a one-time "What's new" overlay the first time a newer build is opened.
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
        WhatsNewOverlay(text = text, onDismiss = { notes = null })
    }
}

@Composable
private fun WhatsNewOverlay(text: String, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)

    val hazeState = LocalHazeState.current
    val shape = RoundedCornerShape(28.dp)
    val colors = MaterialTheme.colorScheme
    val enter = remember { MutableTransitionState(false).apply { targetState = true } }
    val lines = remember(text) {
        text.lines()
            .map { it.trim().removePrefix("- ").trim() }
            .filter { it.isNotEmpty() }
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
            .background(Color.Black.copy(alpha = 0.40f))
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
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 380.dp)
                    .clip(shape)
                    .then(glass)
                    .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
                    // Swallow taps on the card so they don't dismiss the overlay.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(24.dp),
            ) {
                Text(
                    text = "✨ What's new",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                )
                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    lines.forEach { line ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 7.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(colors.primary),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurface,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Got it")
                }
            }
        }
    }
}

private fun isFreshInstall(context: Context): Boolean {
    return runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime - info.firstInstallTime < 5_000
    }.getOrDefault(true)
}