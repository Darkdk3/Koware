package eu.kanade.tachiyomi.ui.customtab

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Keeps the user's custom.html / custom.css / custom.js in the app's private
 * storage and builds the single page that the tab's WebView loads.
 */
object CustomTabStorage {

    const val HTML = "custom.html"
    const val CSS = "custom.css"
    const val JS = "custom.js"

    /** Base URL for the page. Links to any other host open in the browser. */
    const val BASE_URL = "https://koware.local/"
    const val BASE_HOST = "koware.local"

    private val _revision = MutableStateFlow(0)

    /** Bumps every time the files are saved, so the tab knows to reload. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private fun dir(context: Context): File =
        File(context.filesDir, "custom_tab").apply { mkdirs() }

    fun read(context: Context, name: String): String? {
        val file = File(dir(context), name)
        return if (file.exists()) file.readText() else null
    }

    fun hasContent(context: Context): Boolean =
        File(dir(context), HTML).exists()

    fun saveAll(context: Context, html: String, css: String, js: String) {
        File(dir(context), HTML).writeText(html)
        File(dir(context), CSS).writeText(css)
        File(dir(context), JS).writeText(js)
        _revision.value = _revision.value + 1
    }

    /**
     * Order matters: app theme variables, then the base stylesheet, then the
     * user's CSS (so it can override anything), then the user's HTML and JS.
     */
    fun buildDocument(context: Context, themeVars: String): String {
        val html = read(context, HTML).orEmpty()
        val css = read(context, CSS).orEmpty()
        val js = read(context, JS).orEmpty()

        return """
            <!doctype html>
            <html>
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>$themeVars</style>
            <style>$BASE_CSS</style>
            <style>$css</style>
            </head>
            <body>
            $html
            <script>$js</script>
            </body>
            </html>
        """.trimIndent()
    }

    private const val BASE_CSS = """
        *{box-sizing:border-box}
        html,body{margin:0;background:var(--bg);color:var(--fg);
          font-family:Roboto,system-ui,sans-serif;line-height:1.5;font-size:16px}
        body{padding:16px 16px 112px}
        h1,h2,h3{margin:0 0 8px}
        a{color:var(--primary)}
        button{background:var(--primary);color:var(--bg);border:0;
          border-radius:20px;padding:10px 20px;font-size:16px}
        img,video,iframe{max-width:100%}
    """

    const val STARTER_HTML = """<h1 id="title">My custom tab</h1>
<p class="card">Tap the pencil button to edit this page.</p>
<button id="btn">Tap me</button>
<p id="out"></p>
"""

    const val STARTER_CSS = """.card {
  background: var(--surface);
  color: var(--muted);
  padding: 12px 16px;
  border-radius: 16px;
}
"""

    const val STARTER_JS = """let taps = 0;
document.getElementById('btn').addEventListener('click', () => {
  taps += 1;
  document.getElementById('out').textContent = 'Taps: ' + taps;
});
"""
}
