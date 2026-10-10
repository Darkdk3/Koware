package eu.kanade.tachiyomi.ui.customtab

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Stores custom tab saves and provides built-in presets.
 *
 * Layout:
 * custom_tab/saves/<id>/{custom.html,custom.css,custom.js,name.txt}
 * custom_tab/active.txt
 */
object CustomTabStorage {

    const val HTML = "custom.html"
    const val CSS = "custom.css"
    const val JS = "custom.js"

    private const val NAME = "name.txt"

    const val BASE_URL = "https://koware.local/"
    const val BASE_HOST = "koware.local"
    const val BASE_ORIGIN = "https://koware.local"

    data class Save(val id: String, val name: String)

    data class Preset(
        val name: String,
        val description: String,
        val html: String,
        val css: String,
        val js: String,
    )

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private fun bump() {
        _revision.value++
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
        while (saveDir(context, id.toString()).exists()) id++
        return id.toString()
    }

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
        val target = File(saveDir(context, id), file)
        return if (target.exists()) target.readText() else null
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

    fun create(context: Context, name: String): Save {
        migrateLegacy(context)

        val id = newId(context)
        write(
            context,
            id,
            name,
            STARTER_HTML,
            STARTER_CSS,
            STARTER_JS,
        )
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
     * Apply a preset to an existing save.
     * This intentionally replaces that save's HTML, CSS, and JS.
     */
    fun applyPreset(
        context: Context,
        id: String,
        preset: Preset,
    ) {
        write(
            context = context,
            id = id,
            name = preset.name,
            html = preset.html,
            css = preset.css,
            js = preset.js,
        )
    }

    /**
     * Built-in Custom Tab presets.
     * The inspector uses only the existing Koware JS bridge.
     */
    val PRESETS: List<Preset> = listOf(
        Preset(
            name = "JS Bridge Inspector",
            description = "Inspect available Koware API methods, app version, and theme colors.",
            html = INSPECTOR_HTML,
            css = INSPECTOR_CSS,
            js = INSPECTOR_JS,
        ),
    )

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
            <script>${CustomTabApi.shim()}</script>
            <script>$js</script>
            </body>
            </html>
        """.trimIndent()
    }

    private const val BASE_CSS = """
        *{box-sizing:border-box}
        html,body{
          margin:0;
          background:var(--bg);
          color:var(--fg);
          font-family:Roboto,system-ui,sans-serif;
          line-height:1.5;
          font-size:16px
        }
        body{padding:16px 16px 112px}
        h1,h2,h3{margin:0 0 8px}
        a{color:var(--primary)}
        button{
          background:var(--primary);
          color:var(--bg);
          border:0;
          border-radius:20px;
          padding:10px 20px;
          font-size:16px
        }
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

    private const val INSPECTOR_HTML = """
        <main class="inspector">
          <header class="hero">
            <span class="eyebrow">KOWARE · DEVELOPER TOOLS</span>
            <h1>JS Bridge Inspector</h1>
            <p>Inspect the JavaScript bridge available to this custom tab.</p>
            <div class="actions">
              <button id="refresh" type="button">Refresh inspection</button>
              <span id="status" role="status">Waiting…</span>
            </div>
          </header>

          <section class="panel">
            <h2>App information</h2>
            <div class="info">
              <div><small>App version</small><strong id="app-version">—</strong></div>
              <div><small>API version</small><strong id="api-version">—</strong></div>
              <div><small>Bridge</small><strong id="bridge-status">Not checked</strong></div>
            </div>
          </section>

          <section class="panel">
            <h2>Theme colors</h2>
            <div id="theme-colors" class="swatches">
              <p>Run the inspection to view colors.</p>
            </div>
            <pre id="theme-json">Waiting for Koware.getTheme()…</pre>
          </section>

          <section class="panel">
            <h2>Available API methods</h2>
            <p id="method-count">Not checked</p>
            <div id="methods"></div>
          </section>

          <footer>Read-only diagnostics · No data is sent anywhere</footer>
        </main>
    """

    private const val INSPECTOR_CSS = """
        .inspector{max-width:760px;margin:0 auto}
        .hero{
          padding:22px 18px;
          border:1px solid var(--primary);
          border-radius:22px;
          background:var(--surface);
          margin-bottom:14px
        }
        .eyebrow{
          color:var(--primary);
          font-size:11px;
          font-weight:800;
          letter-spacing:.14em
        }
        h1{font-size:28px;line-height:1.15;margin-top:10px}
        h2{font-size:17px}
        p,small,#status,footer{color:var(--muted)}
        .actions,.info{
          display:flex;
          align-items:center;
          justify-content:space-between;
          gap:12px;
          flex-wrap:wrap;
          margin-top:16px
        }
        .panel{
          padding:16px;
          margin:12px 0;
          border:1px solid var(--muted);
          border-radius:18px;
          background:var(--surface)
        }
        .info>div{
          display:flex;
          flex-direction:column;
          gap:4px;
          min-width:90px
        }
        .info strong{overflow-wrap:anywhere}
        .swatches{
          display:grid;
          grid-template-columns:repeat(auto-fit,minmax(95px,1fr));
          gap:8px;
          margin:14px 0
        }
        .swatch{
          overflow:hidden;
          border:1px solid var(--muted);
          border-radius:12px
        }
        .swatch-color{height:38px}
        .swatch-label{padding:8px;font-size:11px}
        .swatch-label code{display:block;overflow-wrap:anywhere}
        pre{
          white-space:pre-wrap;
          overflow-wrap:anywhere;
          font-size:11px;
          padding:12px;
          border-radius:12px;
          background:var(--bg);
          color:var(--fg)
        }
        .method{
          padding:12px;
          margin:10px 0;
          border-radius:12px;
          background:var(--bg)
        }
        .method h3{
          color:var(--primary);
          font-size:13px;
          overflow-wrap:anywhere
        }
        .method p{font-size:13px}
        .method pre{padding:8px}
        footer{text-align:center;padding:14px;font-size:12px}
        button{font-weight:700;cursor:pointer}
        button:disabled{opacity:.6}
    """

    private const val INSPECTOR_JS = """
        const byId = id => document.getElementById(id);

        function element(tag, className, text) {
          const node = document.createElement(tag);
          if (className) node.className = className;
          if (text !== undefined) node.textContent = String(text);
          return node;
        }

        async function inspectKoware() {
          const button = byId('refresh');
          button.disabled = true;
          byId('status').textContent = 'Inspecting…';

          try {
            if (!window.Koware) {
              throw new Error('Koware bridge is unavailable. Open this preset inside Koware.');
            }

            const [info, theme, methods] = await Promise.all([
              Koware.getInfo(),
              Koware.getTheme(),
              Koware.help()
            ]);

            byId('app-version').textContent = info.appVersion;
            byId('api-version').textContent = info.apiVersion;
            byId('bridge-status').textContent = 'Connected';
            byId('status').textContent = 'Inspection complete';
            byId('method-count').textContent = methods.length + ' method(s) available';

            const swatches = byId('theme-colors');
            swatches.replaceChildren();

            [
              ['Background', theme.background],
              ['Foreground', theme.foreground],
              ['Surface', theme.surface],
              ['Muted', theme.muted],
              ['Primary', theme.primary]
            ].forEach(([label, value]) => {
              const card = element('div', 'swatch');
              const color = element('div', 'swatch-color');
              color.style.backgroundColor = value;

              const caption = element('div', 'swatch-label');
              caption.append(
                element('strong', '', label),
                element('code', '', value)
              );

              card.append(color, caption);
              swatches.append(card);
            });

            byId('theme-json').textContent = JSON.stringify(theme, null, 2);

            const list = byId('methods');
            list.replaceChildren();

            methods.forEach(method => {
              const card = element('article', 'method');
              card.append(
                element('h3', '', method.signature || method.name),
                element('p', '', method.description || 'No description'),
                element('p', '', 'Returns: ' + (method.returns || 'Not specified')),
                element('pre', '', method.example || 'No example available')
              );
              list.append(card);
            });
          } catch (error) {
            byId('status').textContent = 'Inspection failed';
            byId('bridge-status').textContent = 'Unavailable';
            byId('methods').replaceChildren(
              element('p', '', error.message || String(error))
            );
            byId('theme-colors').replaceChildren(
              element('p', '', 'Theme information unavailable.')
            );
            byId('theme-json').textContent = 'Unable to retrieve theme.';
          } finally {
            button.disabled = false;
          }
        }

        byId('refresh').addEventListener('click', inspectKoware);
        inspectKoware();
    """
}