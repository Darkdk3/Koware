package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.theme.CustomTheme
import eu.kanade.presentation.theme.ThemeRole
import eu.kanade.presentation.util.Screen
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Edits the colors of the theme that is currently active. The first edit turns the active
 * built-in theme into the custom theme; later edits are saved automatically.
 */
object SettingsCustomThemeScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val isDark = isSystemInDarkTheme()
        val customEnabled by uiPreferences.customThemeEnabled.collectAsState()
        val scheme = MaterialTheme.colorScheme

        var selectedRole by remember { mutableStateOf(ThemeRole.PRIMARY) }
        // Color being dragged right now; only saved (and applied app-wide) when the drag ends.
        var live by remember { mutableStateOf<Color?>(null) }

        // These always come from the active theme, so the editor starts from what you see.
        val committed = CustomTheme.currentColor(scheme, selectedRole)

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Theme colors") },
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
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = if (customEnabled) {
                        "Custom theme. Changes are saved automatically."
                    } else {
                        "These are the colors of your current theme. Changing any of them creates a custom theme."
                    },
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
                        val swatchColor = if (selected && live != null) {
                            live!!
                        } else {
                            CustomTheme.currentColor(scheme, role)
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
                        CustomTheme.setOverride(uiPreferences, isDark, selectedRole, color)
                        live = null
                    },
                )

                Text(
                    text = "Preview",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ThemePreview(
                    role = selectedRole,
                    liveColor = live,
                )

                TextButton(
                    onClick = {
                        CustomTheme.reset(uiPreferences)
                        live = null
                    },
                    enabled = customEnabled,
                ) {
                    Text("Reset to the original theme")
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

/** Small sample UI that shows the edited color before it is applied app-wide. */
@Composable
private fun ThemePreview(
    role: ThemeRole,
    liveColor: Color?,
) {
    val scheme = MaterialTheme.colorScheme

    fun colorOf(r: ThemeRole): Color {
        return if (r == role && liveColor != null) liveColor else CustomTheme.currentColor(scheme, r)
    }

    val background = colorOf(ThemeRole.BACKGROUND)
    val surface = colorOf(ThemeRole.SURFACE)
    val primary = colorOf(ThemeRole.PRIMARY)
    val secondary = colorOf(ThemeRole.SECONDARY)
    val tertiary = colorOf(ThemeRole.TERTIARY)
    val error = colorOf(ThemeRole.ERROR)

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
