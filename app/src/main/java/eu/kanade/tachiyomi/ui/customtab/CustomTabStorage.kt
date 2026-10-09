package eu.kanade.tachiyomi.ui.customtab

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Keeps every save (its own custom.html / custom.css / custom.js) in the app's
 * private storage and builds the single page the tab's WebView loads.
 *
 * Layout: custom_tab/saves/<id>/{custom.html,custom.css,custom.js,name.txt}
 *         custom_tab/active.txt  (id of the save shown in the tab)
 */
object CustomTabStorage {

    const val HTML = "custom.html"
    const val CSS = "custom.css"
    const val JS = "custom.js"
    private const val NAME = "name.txt"

    /** Base URL for the page. */
    const val BASE_URL = "https://koware.local/"
    const val BASE_HOST = "koware.local"

    data class Save(val id: String, val name: String)

    private val _revision = MutableStateFlow(0)

    /** Bumps on every change, so screens and the tab know to refresh. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private fun bump() {
        _revision.value = _revision.value + 1
    }

    private fun root(context: Context): File =
        File(context.filesDir, "custom_tab").apply { mkdirs() }

    private fun savesDir(context: Context): File =
        File(root(context), "saves").apply { mkdirs() }

    private fun saveDir(context: Context, id: String): File =
        File(savesDir(context), id)

    private fun activeFile(context: Context): File =
        File(root(context), "active.txt")

    private fun newId(context: Context): String {
        var id = System.currentTimeMillis()
        while (saveDir(context, id.toString()).exists()) id += 1
        return id.toString()
    }

    /** Moves the old single-page files (before saves existed) into a save. */
    private fun migrateLegacy(context: Context) {
        val root = root(context)
        val legacyHtml = File(root, HTML)
        if (!legacyHtml.exists()) return

        val id = newId(context)
        val dir = saveDir(context, id).apply { mkdirs() }

        File(dir, HTML).writeText(legacyHtml.readText())
        File(root, CSS).takeIf { it.exists() }?.let {
            File(dir, CSS).writeText(it.readText())
        }
        File(root, JS).takeIf { it.exists() }?.let {
            File(dir, JS).writeText(it.readText())
        }
        File(dir, NAME).writeText("My tab")

        legacyHtml.delete()
        File(root, CSS).delete()
        File(root, JS).delete()

        if (!activeFile(context).exists()) {
            activeFile(context).writeText(id)
        }
    }

    fun listSaves(context: Context): List<Save> {
        migrateLegacy(context)
        return savesDir(context)
            .listFiles { file -> file.isDirectory }
            .orEmpty()
            .sortedBy { it.name }
            .map { dir ->
                val name = File(dir, NAME)
                    .takeIf { it.exists() }
                    ?.readText()
                    ?.trim()
                    .orEmpty()
                    .ifBlank { "Untitled" }
                Save(id = dir.name, name = name)
            }
    }

    fun activeId(context: Context): String? {
        val saves = listSaves(context)
        val stored = activeFile(context)
            .takeIf { it.exists() }
            ?.readText()
            ?.trim()
        return saves.firstOrNull { it.id == stored }?.id
            ?: saves.firstOrNull()?.id
    }

    fun setActive(context: Context, id: String) {
        activeFile(context).writeText(id)
        bump()
    }

    fun nameOf(context: Context, id: String): String =
        listSaves(context).firstOrNull { it.id == id }?.name.orEmpty()

    fun read(context: Context, id: String, file: String): String? {
        val f = File(saveDir(context, id), file)
        return if (f.exists()) f.readText() else null
    }

    fun write(
        context: Context,
        id: String,
        name: String,
        html: String,
        css: String,
        js: String,
    ) {
        val dir = saveDir(context, id).apply { mkdirs() }
        File(dir, NAME).writeText(name)
        File(dir, HTML).writeText(html)
        File(dir, CSS).writeText(css)
        File(dir, JS).writeText(js)
        bump()
    }

    /** Creates a save with the starter page and makes it the active one. */
    fun create(context: Context, name: String): Save {
        migrateLegacy(context)
        val id = newId(context)
        write(context, id, name, STARTER_HTML, STARTER_CSS, STARTER_JS)
        setActive(context, id)
        return Save(id, name)
    }

    fun duplicate(context: Context, id: String): Save {
        val newId = newId(context)
        val name = nameOf(context, id) + " copy"
        write(
            context,
            newId,
            name,
            read(context, id, HTML).orEmpty(),
            read(context, id, CSS).orEmpty(),
            read(context, id, JS).orEmpty(),
        )
        setActive(context, newId)
        return Save(newId, name)
    }

    fun delete(context: Context, id: String) {
        saveDir(context, id).deleteRecursively()
        bump()
    }

    /**
     * Order matters: app theme variables, then the base stylesheet, then the
     * user's CSS (so it can override anything), then the user's HTML and JS.
     */
    fun buildDocument(context: Context, themeVars: String): String {
        val id = activeId(context) ?: return ""
        val html = read(context, id, HTML).orEmpty()
        val css = read(context, id, CSS).orEmpty()
        val js = read(context, id, JS).orEmpty()

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
