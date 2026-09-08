package eu.kanade.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import tachiyomi.presentation.core.components.AdaptiveSheet as AdaptiveSheetImpl
import tachiyomi.presentation.core.util.LocalHazeState

/**
 * Dialog showing update changelog with the same style as AdaptiveSheet.
 * Uses haze effect when available for frosted background.
 */
@Composable
fun UpdateChangelogDialog(
    versionName: String,
    changelog: String,
    onDismissRequest: () -> Unit,
) {
    val hazeState = LocalHazeState.current
    
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AdaptiveSheetImpl(
            isTabletUi = false,
            enableImplicitDismiss = true,
            onDismissRequest = onDismissRequest,
            modifier = Modifier.fillMaxWidth(0.9f),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            containerAlpha = 1f,
            hazeState = hazeState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Updated to v$versionName",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "What's New:",
                    style = MaterialTheme.typography.titleMedium,
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = changelog,
                    style = MaterialTheme.typography.bodyMedium,
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Thank you for updating!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
