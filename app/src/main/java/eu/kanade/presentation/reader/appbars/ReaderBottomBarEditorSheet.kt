package eu.kanade.presentation.reader.appbars

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.util.isTabletUi
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.presentation.core.util.LocalHazeState
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun BottomBarEditorSheet(
    items: List<BottomBarItemState>,
    onItemsChange: (List<BottomBarItemState>) -> Unit,
    onDismiss: () -> Unit,
    itemInfo: @Composable (BottomBarItem) -> Pair<ImageVector, String>,
) {
    val mutableItems = remember(items) { items.toMutableStateList() }
    val isTabletUi = isTabletUi()
    val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
    val backgroundStyle by libraryPreferences.sheetBackgroundStyle.collectAsState()
    val opacityPercent by libraryPreferences.sheetOpacityPercent.collectAsState()
    val hazeState = LocalHazeState.current

    val containerAlpha = when (backgroundStyle) {
        LibraryPreferences.NavBarBackgroundStyle.Solid -> 1f
        LibraryPreferences.NavBarBackgroundStyle.Transparent,
        LibraryPreferences.NavBarBackgroundStyle.Frosted,
        LibraryPreferences.NavBarBackgroundStyle.Grainy,
        -> opacityPercent / 100f
    }
    val sheetHazeState = if (
        backgroundStyle == LibraryPreferences.NavBarBackgroundStyle.Frosted ||
        backgroundStyle == LibraryPreferences.NavBarBackgroundStyle.Grainy
    ) {
        hazeState
    } else {
        null
    }
    val sheetNoiseFactor = if (backgroundStyle == LibraryPreferences.NavBarBackgroundStyle.Grainy) {
        0.65f
    } else {
        0f
    }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        mutableItems.add(to.index, mutableItems.removeAt(from.index))
    }

    AdaptiveSheet(
        isTabletUi = isTabletUi,
        enableImplicitDismiss = true,
        onDismissRequest = {
            onItemsChange(mutableItems.toList())
            onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        containerAlpha = containerAlpha,
        hazeState = sheetHazeState,
        noiseFactor = sheetNoiseFactor,
    ) {
        Column {
            Text(
                text = "Customize Toolbar",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(mutableItems, key = { it.item.id }) { itemState ->
                    ReorderableItem(reorderState, key = itemState.item.id) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)
                        val (icon, label) = itemInfo(itemState.item)

                        Surface(shadowElevation = elevation) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DragHandle,
                                    contentDescription = "Drag to reorder",
                                    modifier = Modifier
                                        .draggableHandle()
                                        .padding(end = 12.dp),
                                )
                                Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
                                Text(label, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = itemState.enabled,
                                    onCheckedChange = { checked ->
                                        val idx = mutableItems.indexOf(itemState)
                                        if (idx != -1) mutableItems[idx] = itemState.copy(enabled = checked)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            TextButton(
                onClick = {
                    mutableItems.clear()
                    mutableItems.addAll(DefaultBottomBarItems.map { it.copy(enabled = it.defaultEnabled) })
                },
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(stringResource(MR.strings.label_default))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
