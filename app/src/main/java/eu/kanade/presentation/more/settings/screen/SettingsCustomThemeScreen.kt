@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package eu.kanade.presentation.more.settings.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.theme.CustomTheme
import eu.kanade.presentation.theme.SavedCustomTheme
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.presentation.theme.ThemeRole
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Edits and saves custom themes. Edits stay in a draft (shown in the live preview) until the
 * save icon is tapped. Saved themes are listed at the top; tap one to edit and apply it,
 * long-press to delete it.
 */
object SettingsCustomThemeScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val isDark = isSystemInDarkTheme()
        val amoled by uiPreferences.themeDarkAmoled.collectAsState()
        val savedRaw by uiPreferences.savedCustomThemes.collectAsState()
        val savedThemes = remember(savedRaw) { CustomTheme.parseSaved(savedRaw) }

        val initial = remember { initialDraft(uiPreferences) }
        var editingId by remember { mutableStateOf(initial.id) }
        var name by remember { mutableStateOf(initial.name) }
        var base by remember { mutableStateOf(initial.base) }
        var light by remember { mutableStateOf(initial.light) }
        var dark by remember { mutableStateOf(initial.dark) }
        var dirty by remember { mutableStateOf(initial.unsaved) }

        var selectedRole by remember { mutableStateOf(ThemeRole.PRIMARY) }
        // Color being dragged right now; only added to the draft when the drag ends.
        var live by remember { mutableStateOf<Color?>(null) }
        var confirmDiscard by remember { mutableStateOf(false) }
        var deleteTarget by remember { mutableStateOf<SavedCustomTheme?>(null) }

        fun loadTheme(theme: SavedCustomTheme) {
            editingId = theme.id
            name = theme.name
            base = runCatching { AppTheme.valueOf(theme.base) }.getOrDefault(AppTheme.DEFAULT)
            light = CustomTheme.parse(theme.light)
            dark = CustomTheme.parse(theme.dark)
            dirty = false
            live = null
            CustomTheme.activate(uiPreferences, theme)
        }

        fun startNewTheme() {
            editingId = null
            name = ""
            base = uiPreferences.appTheme.get()
            light = emptyMap()
            dark = emptyMap()
            dirty = false
            live = null
        }

        fun save() {
            editingId = CustomTheme.saveTheme(uiPreferences, editingId, name, base, light, dark)
            dirty = false
            context.toast("Theme saved")
        }

        fun leave() {
            if (dirty) confirmDiscard = true else navigator.pop()
        }

        BackHandler(enabled = dirty) { confirmDiscard = true }

        if (confirmDiscard) {
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                title = { Text("Discard changes?") },
                text = { Text("Your edits to this theme haven't been saved.") },
                confirmButton = {
                    TextButton(onClick = { navigator.pop() }) { Text("Discard") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") }
                },
            )
        }

        deleteTarget?.let { target ->
            val targetLabel = CustomTheme.displayName(
                target,
                savedThemes.indexOfFirst { it.id == target.id }.coerceAtLeast(0),
            )
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Delete theme?") },
                text = { Text("\"$targetLabel\" will be removed from your saved themes.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            CustomTheme.delete(uiPreferences, target.id)
                            if (editingId == target.id) startNewTheme()
                            deleteTarget = null
                        },
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
                },
            )
        }

        BaseSchemeProvider(base = base, amoled = amoled) { baseScheme ->
            // The draft theme: the base theme plus the edits made so far in this mode.
            val draftScheme = remember(baseScheme, light, dark, isDark) {
                CustomTheme.apply(baseScheme, if (isDark) dark else light, isDark)
            }
            val liveColor = live
            val previewScheme = if (liveColor != null) {
                CustomTheme.apply(draftScheme, mapOf(selectedRole to liveColor), isDark)
            } else {
                draftScheme
            }
            val committed = CustomTheme.currentColor(draftScheme, selectedRole)

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Theme colors") },
                        navigationIcon = {
                            IconButton(onClick = { leave() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { save() }) {
                                BadgedBox(badge = { if (dirty) Badge() }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Save,
                                        contentDescription = "Save theme",
                                    )
                                }
                            }
                        },
                    )
                },
            ) { contentPadding ->
                Column(
                    modifier = Modifier
                        .padding(contentPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "Saved themes",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        savedThemes.forEachIndexed { index, theme ->
                            SavedThemeCard(
                                theme = theme,
                                label = CustomTheme.displayName(theme, index),
                                selected = theme.id == editingId,
                                isDark = isDark,
                                amoled = amoled,
                                onClick = { loadTheme(theme) },
                                onLongClick = { deleteTarget = theme },
                            )
                        }
                        NewThemeCard(onClick = { startNewTheme() })
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            dirty = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Theme name (optional)") },
                        singleLine = true,
                    )

                    Text(
                        text = "Changes stay in the preview until you tap the save icon.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Role swatches
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ThemeRole.entries.forEach { role ->
                            val selected = role == selectedRole
                            val swatchColor = if (selected && liveColor != null) {
                                liveColor
                            } else {
                                CustomTheme.currentColor(draftScheme, role)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(swatchColor, RoundedCornerShape(12.dp))
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                        .clickable {
                                            selectedRole = role
                                            live = null
                                        },
                                )
                                Text(
                                    text = role.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }

                    Text(
                        text = "${selectedRole.label}: ${selectedRole.description}",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    ColorPicker(
                        color = committed,
                        onLive = { live = it },
                        onCommit = { color ->
                            val edited = (if (isDark) dark else light).toMutableMap()
                            edited[selectedRole] = color
                            if (isDark) dark = edited else light = edited
                            dirty = true
                            live = null
                        },
                    )

                    Text(
                        text = "Preview",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        ThemeMiniPreview(
                            scheme = previewScheme,
                            selected = true,
                            modifier = Modifier.width(120.dp),
                        )
                    }
                    ThemePreview(scheme = previewScheme)

                    TextButton(
                        onClick = {
                            light = emptyMap()
                            dark = emptyMap()
                            dirty = true
                            live = null
                        },
                        enabled = light.isNotEmpty() || dark.isNotEmpty(),
                    ) {
                        Text("Reset colors")
                    }
                }
            }
        }
    }
}

private data class Draft(
    val id: String?,
    val name: String,
    val base: AppTheme,
    val light: Map<ThemeRole, Color>,
    val dark: Map<ThemeRole, Color>,
    val unsaved: Boolean,
)

/** Starts from the active custom theme if there is one, otherwise from the current built-in theme. */
private fun initialDraft(prefs: UiPreferences): Draft {
    val saved = CustomTheme.parseSaved(prefs.savedCustomThemes.get())
    val match = saved.firstOrNull { it.id == prefs.activeCustomThemeId.get() }
    return if (prefs.customThemeEnabled.get()) {
        Draft(
            id = match?.id,
            name = match?.name.orEmpty(),
            base = runCatching { AppTheme.valueOf(prefs.customThemeBase.get()) }
                .getOrDefault(AppTheme.DEFAULT),
            light = CustomTheme.parse(prefs.customThemeLight.get()),
            dark = CustomTheme.parse(prefs.customThemeDark.get()),
            // An enabled custom theme that was never saved can still be saved from here.
            unsaved = match == null,
        )
    } else {
        Draft(
            id = null,
            name = "",
            base = prefs.appTheme.get(),
            light = emptyMap(),
            dark = emptyMap(),
            unsaved = false,
        )
    }
}

/**
 * Gives [content] the plain built-in color scheme for [base], while the surrounding UI keeps
 * using the app's current theme.
 */
@Composable
private fun BaseSchemeProvider(
    base: AppTheme,
    amoled: Boolean,
    content: @Composable (ColorScheme) -> Unit,
) {
    val appScheme = MaterialTheme.colorScheme
    val appTypography = MaterialTheme.typography
    val appShapes = MaterialTheme.shapes
    TachiyomiTheme(appTheme = base, amoled = amoled) {
        val baseScheme = MaterialTheme.colorScheme
        MaterialTheme(
            colorScheme = appScheme,
            typography = appTypography,
            shapes = appShapes,
        ) {
            content(baseScheme)
        }
    }
}

@Composable
private fun SavedThemeCard(
    theme: SavedCustomTheme,
    label: String,
    selected: Boolean,
    isDark: Boolean,
    amoled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val base = remember(theme.base) {
        runCatching { AppTheme.valueOf(theme.base) }.getOrDefault(AppTheme.DEFAULT)
    }
    BaseSchemeProvider(base = base, amoled = amoled) { baseScheme ->
        val scheme = remember(baseScheme, theme.light, theme.dark, isDark) {
            CustomTheme.apply(
                baseScheme,
                CustomTheme.parse(if (isDark) theme.dark else theme.light),
                isDark,
            )
        }
        Column(modifier = Modifier.width(96.dp)) {
            ThemeMiniPreview(
                scheme = scheme,
                selected = selected,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 2,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun NewThemeCard(
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.width(96.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .border(
                    width = 4.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(17.dp),
                )
                .padding(4.dp)
                .clip(RoundedCornerShape(13.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "New",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Minimal phone-shaped preview, the same look as the built-in theme cards, drawn from [scheme]. */
@Composable
private fun ThemeMiniPreview(
    scheme: ColorScheme,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .border(
                width = 4.dp,
                color = if (selected) scheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(17.dp),
            )
            .padding(4.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(scheme.background)
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier
                },
            ),
    ) {
        // App bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.8f)
                    .weight(0.7f)
                    .padding(end = 4.dp)
                    .background(color = scheme.onSurface, shape = MaterialTheme.shapes.small),
            )
            Box(
                modifier = Modifier.weight(0.3f),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = scheme.primary,
                    )
                }
            }
        }

        // Cover
        Box(
            modifier = Modifier
                .padding(start = 8.dp, top = 2.dp)
                .background(color = scheme.outlineVariant, shape = MaterialTheme.shapes.small)
                .fillMaxWidth(0.5f)
                .aspectRatio(2f / 3f),
        ) {
            Row(
                modifier = Modifier
                    .padding(4.dp)
                    .size(width = 24.dp, height = 16.dp)
                    .clip(RoundedCornerShape(5.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(12.dp)
                        .background(scheme.tertiary),
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(12.dp)
                        .background(scheme.secondary),
                )
            }
        }

        // Bottom bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(color = scheme.surfaceContainer) {
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(17.dp)
                            .background(color = scheme.primary, shape = CircleShape),
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .alpha(0.6f)
                            .height(17.dp)
                            .weight(1f)
                            .background(color = scheme.onSurface, shape = MaterialTheme.shapes.small),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorPicker(
    color: Color,
    onLive: (Color) -> Unit,
    onCommit: (Color) -> Unit,
) {
    val initial = remember(color) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) }
    }
    var hue by remember(color) { mutableFloatStateOf(initial[0]) }
    var sat by remember(color) { mutableFloatStateOf(initial[1]) }
    var value by remember(color) { mutableFloatStateOf(initial[2]) }

    val currentOnLive by rememberUpdatedState(onLive)
    val currentOnCommit by rememberUpdatedState(onCommit)

    fun current(): Color = Color.hsv(hue.coerceIn(0f, 360f), sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f))

    fun pick(offset: Offset, size: IntSize) {
        val radius = size.width / 2f
        val dx = offset.x - radius
        val dy = offset.y - radius
        val distance = sqrt(dx * dx + dy * dy).coerceAtMost(radius)
        var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        if (angle < 0f) angle += 360f
        hue = angle
        sat = if (radius > 0f) distance / radius else 0f
        currentOnLive(current())
    }

    val hueColors = remember { (0..360 step 30).map { Color.hsv(it.toFloat(), 1f, 1f) } }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .size(220.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { pick(it, size) },
                            onDragEnd = { currentOnCommit(current()) },
                            onDragCancel = { currentOnCommit(current()) },
                            onDrag = { change, _ ->
                                change.consume()
                                pick(change.position, size)
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            pick(offset, size)
                            currentOnCommit(current())
                        }
                    },
            ) {
                val radius = size.minDimension / 2f
                val center = Offset(radius, radius)

                drawCircle(
                    brush = Brush.sweepGradient(hueColors, center = center),
                    radius = radius,
                    center = center,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, Color.Transparent),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 1f - value.coerceIn(0f, 1f)),
                    radius = radius,
                    center = center,
                )

                val angle = Math.toRadians(hue.toDouble())
                val marker = Offset(
                    x = center.x + cos(angle).toFloat() * sat * radius,
                    y = center.y + sin(angle).toFloat() * sat * radius,
                )
                drawCircle(
                    color = Color.White,
                    radius = 9.dp.toPx(),
                    center = marker,
                    style = Stroke(width = 3.dp.toPx()),
                )
                drawCircle(
                    color = Color.Black,
                    radius = 10.5.dp.toPx(),
                    center = marker,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }

        LabeledSlider(
            label = "Hue",
            value = hue,
            range = 0f..360f,
            onChange = {
                hue = it
                currentOnLive(current())
            },
            onFinished = { currentOnCommit(current()) },
        )
        LabeledSlider(
            label = "Saturation",
            value = sat,
            range = 0f..1f,
            onChange = {
                sat = it
                currentOnLive(current())
            },
            onFinished = { currentOnCommit(current()) },
        )
        LabeledSlider(
            label = "Brightness",
            value = value,
            range = 0f..1f,
            onChange = {
                value = it
                currentOnLive(current())
            },
            onFinished = { currentOnCommit(current()) },
        )

        Text(
            text = "#%06X".format(current().toArgb() and 0xFFFFFF),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.size(width = 92.dp, height = 24.dp),
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = range,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Small sample UI that shows the draft colors before they are saved. */
@Composable
private fun ThemePreview(
    scheme: ColorScheme,
) {
    val background = CustomTheme.currentColor(scheme, ThemeRole.BACKGROUND)
    val surface = CustomTheme.currentColor(scheme, ThemeRole.SURFACE)
    val primary = CustomTheme.currentColor(scheme, ThemeRole.PRIMARY)
    val secondary = CustomTheme.currentColor(scheme, ThemeRole.SECONDARY)
    val tertiary = CustomTheme.currentColor(scheme, ThemeRole.TERTIARY)
    val error = CustomTheme.currentColor(scheme, ThemeRole.ERROR)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(surface, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Series title",
                style = MaterialTheme.typography.titleMedium,
                color = CustomTheme.onColorFor(surface),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewPill("Read", primary)
                PreviewPill("Library", secondary)
                PreviewPill("New", tertiary)
                PreviewPill("Failed", error)
            }
        }
    }
}

@Composable
private fun PreviewPill(
    text: String,
    color: Color,
) {
    Box(
        modifier = Modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = CustomTheme.onColorFor(color),
        )
    }
}