package eu.kanade.tachiyomi.ui.customtab

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast

object CustomTabEditorScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        // Plain remember (not rememberSaveable): large text can overflow the
        // saved-state bundle and crash the app.
        var html by remember {
            mutableStateOf(
                CustomTabStorage.read(context, CustomTabStorage.HTML)
                    ?: CustomTabStorage.STARTER_HTML,
            )
        }
        var css by remember {
            mutableStateOf(
                CustomTabStorage.read(context, CustomTabStorage.CSS)
                    ?: CustomTabStorage.STARTER_CSS,
            )
        }
        var js by remember {
            mutableStateOf(
                CustomTabStorage.read(context, CustomTabStorage.JS)
                    ?: CustomTabStorage.STARTER_JS,
            )
        }
        var selected by remember { mutableStateOf(0) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Edit custom tab") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                CustomTabStorage.saveAll(context, html, css, js)
                                context.toast("Saved")
                                navigator.pop()
                            },
                        ) {
                            Text("Save")
                        }
                    },
                )
            },
        ) { contentPadding ->
            Column(modifier = Modifier.padding(contentPadding)) {
                TabRow(selectedTabIndex = selected) {
                    listOf("HTML", "CSS", "JS").forEachIndexed { index, title ->
                        Tab(
                            selected = selected == index,
                            onClick = { selected = index },
                            text = { Text(title) },
                        )
                    }
                }

                OutlinedTextField(
                    value = when (selected) {
                        0 -> html
                        1 -> css
                        else -> js
                    },
                    onValueChange = {
                        when (selected) {
                            0 -> html = it
                            1 -> css = it
                            else -> js = it
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    ),
                )
            }
        }
    }
}
