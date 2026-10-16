
package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.customtab.CustomTabPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.presentation.core.components.material.NavigationBar
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt

object SettingsNavBarScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val prefs = remember { Injekt.get<LibraryPreferences>() }
        val customTabPrefs = remember { Injekt.get<CustomTabPreferences>() }

        val widthPercent by prefs.navBarWidthPercent.collectAsState()
        val heightDp by prefs.navBarHeightDp.collectAsState()
        val itemSpacingDp by prefs.navBarItemSpacingDp.collectAsState()
        val cornerRadiusDp by prefs.navBarCornerRadiusDp.collectAsState()
        val style by prefs.navBarBackgroundStyle.collectAsState()
        val opacityPercent by prefs.navBarOpacityPercent.collectAsState()
        val alwaysShowLabels by prefs.alwaysShowNavigationLabels.collectAsState()
        val customTabEnabled by customTabPrefs.enabled.collectAsState()
        val customTabName by customTabPrefs.name.collectAsState()
        val savedTabOrder by prefs.navBarItemOrder.collectAsState()

        val tabOrder = remember(savedTabOrder) {
            parseSettingsNavBarOrder(savedTabOrder)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Navigation bar") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                )
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize(),
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    NavBarPreview(
                        widthPercent = widthPercent,
                        heightDp = heightDp,
                        itemSpacingDp = itemSpacingDp,
                        cornerRadiusDp = cornerRadiusDp,
                        style = style,
                        opacityPercent = opacityPercent,
                        alwaysShowLabels = alwaysShowLabels,
                        customTabEnabled = customTabEnabled,
                        customTabName = customTabName,
                        tabOrder = tabOrder,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SectionLabel("Background")

                    FlowRow(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LibraryPreferences.NavBarBackgroundStyle.entries.forEach { option ->
                            FilterChip(
                                selected = option == style,
                                onClick = {
                                    prefs.navBarBackgroundStyle.set(option)
                                },
                                label = { Text(option.name) },
                            )
                        }
                    }

                    SliderRow(
                        label = "Opacity",
                        valueText = if (
                            style == LibraryPreferences.NavBarBackgroundStyle.Solid
                        ) {
                            "100%"
                        } else {
                            "$opacityPercent%"
                        },
                        value = opacityPercent,
                        min = 0,
                        max = 100,
                        step = 5,
                        enabled = style !=
                            LibraryPreferences.NavBarBackgroundStyle.Solid,
                        onChange = {
                            prefs.navBarOpacityPercent.set(it)
                        },
                    )

                    SectionLabel("Tabs")

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 16.dp,
                                vertical = 12.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "Custom tab",
                                style = MaterialTheme.typography.bodyLarge,
                            )

                            Text(
                                text = "Adds a tab you can build with HTML, CSS, and JS",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Switch(
                            checked = customTabEnabled,
                            onCheckedChange = {
                                customTabPrefs.enabled.set(it)
                            },
                        )
                    }

                    SectionLabel("Reorder tabs")

                    Text(
                        text = "Use the arrows to change the order of navigation items. " +
                            "Your order is saved automatically. Disabled tabs keep their positions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = 16.dp,
                            vertical = 4.dp,
                        ),
                    )

                    tabOrder.forEachIndexed { index, key ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 2.dp,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = settingsNavBarTabLabel(key),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )

                            IconButton(
                                enabled = index > 0,
                                onClick = {
                                    val updated = tabOrder.toMutableList()
                                    val previous = updated[index - 1]
                                    updated[index - 1] = updated[index]
                                    updated[index] = previous
                                    prefs.navBarItemOrder.set(updated.joinToString(","))
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowUp,
                                    contentDescription = "Move ${settingsNavBarTabLabel(key)} up",
                                )
                            }

                            IconButton(
                                enabled = index < tabOrder.lastIndex,
                                onClick = {
                                    val updated = tabOrder.toMutableList()
                                    val next = updated[index + 1]
                                    updated[index + 1] = updated[index]
                                    updated[index] = next
                                    prefs.navBarItemOrder.set(updated.joinToString(","))
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Move ${settingsNavBarTabLabel(key)} down",
                                )
                            }
                        }
                    }

                    SectionLabel("Size and shape")

                    SliderRow(
                        label = "Width",
                        valueText = "$widthPercent%",
                        value = widthPercent,
                        min = 50,
                        max = 100,
                        step = 5,
                        onChange = {
                            prefs.navBarWidthPercent.set(it)
                        },
                    )

                    SliderRow(
                        label = "Height",
                        valueText = "${heightDp}dp",
                        value = heightDp,
                        min = 56,
                        max = 120,
                        step = 4,
                        onChange = {
                            prefs.navBarHeightDp.set(it)
                        },
                    )

                    SliderRow(
                        label = "Item spacing",
                        valueText = "${itemSpacingDp}dp",
                        value = itemSpacingDp,
                        min = 0,
                        max = 24,
                        step = 1,
                        onChange = {
                            prefs.navBarItemSpacingDp.set(it)
                        },
                    )

                    SliderRow(
                        label = "Corner radius",
                        valueText = when {
                            cornerRadiusDp < 0 -> "Pill"
                            cornerRadiusDp == 0 -> "Square"
                            else -> "${cornerRadiusDp}dp"
                        },
                        value = cornerRadiusDp,
                        min = -1,
                        max = 56,
                        step = 1,
                        onChange = {
                            prefs.navBarCornerRadiusDp.set(it)
                        },
                    )

                    Box(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

private data class PreviewTab(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun NavBarPreview(
    widthPercent: Int,
    heightDp: Int,
    itemSpacingDp: Int,
    cornerRadiusDp: Int,
    style: LibraryPreferences.NavBarBackgroundStyle,
    opacityPercent: Int,
    alwaysShowLabels: Boolean,
    customTabEnabled: Boolean,
    customTabName: String,
    tabOrder: List<String>,
) {
    val hazeState = remember { HazeState() }
    var selectedKey by remember { mutableStateOf("library") }

    val tabs = remember(customTabEnabled, customTabName, tabOrder) {
        buildList {
            add(
                PreviewTab(
                    "library",
                    "Library",
                    Icons.Outlined.CollectionsBookmark,
                ),
            )
            add(
                PreviewTab(
                    "updates",
                    "Updates",
                    Icons.Outlined.NewReleases,
                ),
            )
            add(
                PreviewTab(
                    "history",
                    "History",
                    Icons.Outlined.History,
                ),
            )
            add(
                PreviewTab(
                    "browse",
                    "Browse",
                    Icons.Outlined.Explore,
                ),
            )
            if (customTabEnabled) {
                add(
                    PreviewTab(
                        "custom",
                        customTabName,
                        Icons.Outlined.Code,
                    ),
                )
            }
            add(
                PreviewTab(
                    "more",
                    "More",
                    Icons.Outlined.MoreHoriz,
                ),
            )
        }.sortedBy { tab ->
            val index = tabOrder.indexOf(tab.key)
            if (index >= 0) index else Int.MAX_VALUE
        }
    }

    val selectedIndex = tabs.indexOfFirst { it.key == selectedKey }
        .coerceAtLeast(0)

    val barShape = if (cornerRadiusDp < 0) {
        RoundedCornerShape(percent = 50)
    } else {
        RoundedCornerShape(cornerRadiusDp.dp)
    }

    val barAlpha = if (
        style == LibraryPreferences.NavBarBackgroundStyle.Solid
    ) {
        1f
    } else {
        opacityPercent / 100f
    }

    val blurred =
        style == LibraryPreferences.NavBarBackgroundStyle.Frosted ||
            style == LibraryPreferences.NavBarBackgroundStyle.Grainy

    val noise =
        if (style == LibraryPreferences.NavBarBackgroundStyle.Grainy) {
            0.65f
        } else {
            0f
        }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                ),
            contentAlignment = Alignment.Center,
        ) {
            NavigationBar(
                shape = barShape,
                height = heightDp.dp,
                itemSpacing = itemSpacingDp.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                containerAlpha = barAlpha,
                hazeState = if (blurred) hazeState else null,
                noiseFactor = noise,
                modifier = Modifier.fillMaxWidth(widthPercent / 100f),
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = {
                            selectedKey = tab.key
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        alwaysShowLabel = alwaysShowLabels,
                    )
                }
            }
        }
    }
}

private val SettingsNavBarDefaultOrder = listOf(
    "novels",
    "library",
    "updates",
    "history",
    "browse",
    "custom",
    "more",
)

private fun parseSettingsNavBarOrder(raw: String): List<String> {
    val saved = raw.split(",")
        .map(String::trim)
        .filter { it in SettingsNavBarDefaultOrder }
        .distinct()

    return saved + SettingsNavBarDefaultOrder.filterNot { it in saved }
}

private fun settingsNavBarTabLabel(key: String): String = when (key) {
    "novels" -> "Novels"
    "library" -> "Library"
    "updates" -> "Updates"
    "history" -> "History"
    "browse" -> "Browse"
    "custom" -> "Custom"
    "more" -> "More"
    else -> key
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 8.dp,
        ),
    )
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    onChange: (Int) -> Unit,
    enabled: Boolean = true,
) {
    val textColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Column(
        modifier = Modifier.padding(
            horizontal = 16.dp,
            vertical = 4.dp,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )

            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Slider(
            value = value.toFloat(),
            onValueChange = {
                onChange(it.roundToInt())
            },
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min) / step - 1,
            enabled = enabled,
        )
    }
}
