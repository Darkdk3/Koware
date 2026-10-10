package eu.kanade.tachiyomi.ui.customtab

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What each file is for, in the order the app puts them together. */
internal data class PartInfo(
    val key: String,
    val step: Int,
    val title: String,
    val file: String,
    val description: String,
    val runNote: String,
)

internal val CustomTabParts = listOf(
    PartInfo(
        key = "html",
        step = 1,
        title = "Structure",
        file = CustomTabStorage.HTML,
        description = "Builds the page. Loads first.",
        runNote = "Loads first. Builds the page.",
    ),
    PartInfo(
        key = "css",
        step = 2,
        title = "Style",
        file = CustomTabStorage.CSS,
        description = "Changes how the page looks.",
        runNote = "Applied after the structure.",
    ),
    PartInfo(
        key = "js",
        step = 3,
        title = "Behavior",
        file = CustomTabStorage.JS,
        description = "Makes the page do things. Runs last.",
        runNote = "Runs last, after the page is built.",
    ),
)

/** Everything being edited for one save, shared by both editor styles. */
internal class CustomTabDraft(context: Context, val saveId: String) {

    var name by mutableStateOf(CustomTabStorage.nameOf(context, saveId))

    var html by mutableStateOf(
        TextFieldValue(
            CustomTabStorage.read(context, saveId, CustomTabStorage.HTML)
                ?: CustomTabStorage.STARTER_HTML,
        ),
    )
    var css by mutableStateOf(
        TextFieldValue(
            CustomTabStorage.read(context, saveId, CustomTabStorage.CSS)
                ?: CustomTabStorage.STARTER_CSS,
        ),
    )
    var js by mutableStateOf(
        TextFieldValue(
            CustomTabStorage.read(context, saveId, CustomTabStorage.JS)
                ?: CustomTabStorage.STARTER_JS,
        ),
    )

    var disabled by mutableStateOf(CustomTabStorage.disabledParts(context, saveId))

    fun get(part: String): TextFieldValue = when (part) {
        "html" -> html
        "css" -> css
        else -> js
    }

    fun set(part: String, value: TextFieldValue) {
        when (part) {
            "html" -> html = value
            "css" -> css = value
            else -> js = value
        }
    }

    fun save(context: Context) {
        CustomTabStorage.write(
            context = context,
            id = saveId,
            name = name.trim().ifBlank { "Untitled" },
            html = html.text,
            css = css.text,
            js = js.text,
        )
    }

    fun setEnabled(context: Context, part: String, enabled: Boolean) {
        CustomTabStorage.setPartEnabled(context, saveId, part, enabled)
        disabled = CustomTabStorage.disabledParts(context, saveId)
    }

    /** The page as it would load right now, including unsaved edits. */
    fun document(themeVars: String): String = CustomTabStorage.composeDocument(
        themeVars = themeVars,
        html = html.text,
        css = css.text,
        js = js.text,
        disabled = disabled,
    )
}

internal fun TextFieldValue.insert(text: String): TextFieldValue {
    val start = selection.min
    val end = selection.max
    return copy(
        text = this.text.replaceRange(start, end, text),
        selection = TextRange(start + text.length),
    )
}

/** Save name shown as an editable title. */
@Composable
internal fun CustomTabNameField(
    draft: CustomTabDraft,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = draft.name,
            onValueChange = { draft.name = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = "Rename",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Code editor with line numbers. Lines scroll sideways instead of wrapping. */
@Composable
internal fun CodeField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lineCount = value.text.count { it == '\n' } + 1
    val focusRequester = remember { FocusRequester() }
    val codeStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .verticalScroll(rememberScrollState())
            .heightIn(min = 320.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { focusRequester.requestFocus() }
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = (1..lineCount).joinToString("\n"),
            style = codeStyle.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.End,
            ),
            modifier = Modifier
                .width(40.dp)
                .padding(end = 8.dp),
        )

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = codeStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(end = 12.dp)
                .focusRequester(focusRequester),
        )
    }
}

/** Keys that are slow to type on a phone keyboard. Sits above the keyboard. */
@Composable
internal fun SymbolRow(
    onInsert: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keys = listOf(
        "Tab" to "  ",
        "<" to "<",
        ">" to ">",
        "{" to "{",
        "}" to "}",
        "(" to "(",
        ")" to ")",
        "[" to "[",
        "]" to "]",
        ";" to ";",
        "\"" to "\"",
        "'" to "'",
        "=" to "=",
        "/" to "/",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        keys.forEach { (label, text) ->
            Surface(
                onClick = { onInsert(text) },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = label,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}
