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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

        val widthPercent by prefs.navBarWidthPercent.collectAsState()
        val heightDp by prefs.navBarHeightDp.collectAsState()
        val itemSpacingDp by prefs.navBarItemSpacingDp.collectAsState()
        val cornerRadiusDp by prefs.navBarCornerRadiusDp.collectAsState()
        val style by prefs.navBarBackgroundStyle.collectAsState()
        val opacityPercent by prefs.navBarOpacityPercent.collectAsState()
        val alwaysShowLabels by prefs.alwaysShowNavigationLabels.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Navigation bar") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    },
                )
            },
        ) { contentPadding ->
            Column(modifier = Modifier.padding(contentPadding)) {
                Box(modifier = Modifier.padding(16.dp)) {
                    NavBarPreview(
                        widthPercent = widthPercent,
                        heightDp = heightDp,
                        itemSpacingDp = itemSpacingDp,
                        cornerRadiusDp = cornerRadiusDp,
                        style = style,
                        opacityPercent = opacityPercent,
                        alwaysShowLabels = alwaysShowLabels,
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
                                onClick = { prefs.navBarBackgroundStyle.set(option) },
                                label = { Text(option.name) },
                            )
                        }
                    }
                    SliderRow(
                        label = "Opacity",
                        valueText = if (style == LibraryPreferences.NavBarBackgroundStyle.Solid) {
                            "100%"
                        } else {
                            "$opacityPercent%"
                        },
                        value = opacityPercent,
                        min = 0,
                        max = 100,
                        step = 5,
                        enabled = style != LibraryPreferences.NavBarBackgroundStyle.Solid,
                        onChange = { prefs.navBarOpacityPercent.set(it) },
                    )

                    SectionLabel("Size and shape")
                    SliderRow(
                        label = "Width",
                        valueText = "$widthPercent%",
                        value = widthPercent,
                        min = 50,
                        max = 100,
                        step = 5,
                        onChange = { prefs.navBarWidthPercent.set(it) },
                    )
                    SliderRow(
                        label = "Height",
                        valueText = "${heightDp}dp",
                        value = heightDp,
                        min = 56,
                        max = 120,
                        step = 4,
                        onChange = { prefs.navBarHeightDp.set(it) },
                    )
                    SliderRow(
                        label = "Item spacing",
                        valueText = "${itemSpacingDp}dp",
                        value = itemSpacingDp,
                        min = 0,
                        max = 24,
                        step = 1,
                        onChange = { prefs.navBarItemSpacingDp.set(it) },
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
                        onChange = { prefs.navBarCornerRadiusDp.set(it) },
                    )
                    Box(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

private data class PreviewTab(val label: String, val icon: ImageVector)

@Composable
private fun NavBarPreview(
    widthPercent: Int,
    heightDp: Int,
    itemSpacingDp: Int,
    cornerRadiusDp: Int,
    style: LibraryPreferences.NavBarBackgroundStyle,
    opacityPercent: Int,
    alwaysShowLabels: Boolean,
) {
    val hazeState = remember { HazeState() }
    var selected by remember { mutableIntStateOf(0) }
    val tabs = remember {
        listOf(
            PreviewTab("Library", Icons.Outlined.CollectionsBookmark),
            PreviewTab("Updates", Icons.Outlined.NewReleases),
            PreviewTab("History", Icons.Outlined.History),
            PreviewTab("Browse", Icons.Outlined.Explore),
            PreviewTab("More", Icons.Outlined.MoreHoriz),
        )
    }

    // Same logic as HomeScreen so the preview matches the real bar.
    val barShape = if (cornerRadiusDp < 0) {
        RoundedCornerShape(percent = 50)
    } else {
        RoundedCornerShape(cornerRadiusDp.dp)
    }
    val barAlpha = if (style == LibraryPreferences.NavBarBackgroundStyle.Solid) {
        1f
    } else {
        opacityPercent / 100f
    }
    val blurred = style == LibraryPreferences.NavBarBackgroundStyle.Frosted ||
        style == LibraryPreferences.NavBarBackgroundStyle.Grainy
    val noise = if (style == LibraryPreferences.NavBarBackgroundStyle.Grainy) 0.65f else 0f

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
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
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

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
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
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = textColor)
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min) / step - 1,
            enabled = enabled,
        )
    }
}
