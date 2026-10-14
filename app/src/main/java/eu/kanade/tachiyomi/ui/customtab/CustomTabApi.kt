
package eu.kanade.tachiyomi.ui.customtab

import org.json.JSONObject

/**
 * Single source of truth for the Koware JavaScript API.
 * Used by the bridge, help(), and the API reference screen.
 */
object CustomTabApi {
    const val VERSION = 1
    const val CHANNEL = "_koware"

    data class Method(
        val name: String,
        val signature: String,
        val description: String,
        val parameters: String,
        val returns: String,
        val example: String,
        val category: String,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("name", name)
            .put("signature", signature)
            .put("description", description)
            .put("parameters", parameters)
            .put("returns", returns)
            .put("example", example)
            .put("category", category)
    }

    val methods: List<Method> = listOf(
        Method(
            name = "help",
            signature = "Koware.help()",
            description = "Returns the complete reference for registered API methods.",
            parameters = "None.",
            returns = "Array of method documentation objects.",
            example = "const methods = await Koware.help();\nconsole.log(methods);",
            category = "Core",
        ),
        Method(
            name = "getInfo",
            signature = "Koware.getInfo()",
            description = "Returns the app and API version.",
            parameters = "None.",
            returns = "{ appVersion: string, apiVersion: number }",
            example = "const info = await Koware.getInfo();\nconsole.log(info.appVersion);",
            category = "Core",
        ),
        Method(
            name = "getTheme",
            signature = "Koware.getTheme()",
            description = "Returns current app theme colors.",
            parameters = "None.",
            returns = "{ isDark, background, foreground, surface, muted, primary }",
            example = "const theme = await Koware.getTheme();\ndocument.body.style.color = theme.foreground;",
            category = "Appearance",
        ),
        Method(
            name = "getLibrary",
            signature = "Koware.getLibrary(options?)",
            description = "Returns saved library entries with metadata, category IDs, chapter counts, read counts, and bookmark counts.",
            parameters = "options?: { limit?: number }. Limit defaults to 100 and is clamped to 1–500.",
            returns = "Array of library entry objects.",
            example = "const library = await Koware.getLibrary({ limit: 100 });\nconsole.log(library);",
            category = "Library",
        ),
        Method(
            name = "getManga",
            signature = "Koware.getManga({ id })",
            description = "Returns stored metadata for one manga or novel.",
            parameters = "Required: { id: number } — the local manga ID.",
            returns = "Manga metadata object, or null if not found.",
            example = "const manga = await Koware.getManga({ id: 123 });\nif (manga) console.log(manga.title);",
            category = "Library",
        ),
        Method(
            name = "getChapters",
            signature = "Koware.getChapters({ mangaId, limit? })",
            description = "Returns chapters belonging to a local manga or novel.",
            parameters = "Required: mangaId. Optional: limit, default 200, clamped to 1–500.",
            returns = "Array of chapter objects.",
            example = "const chapters = await Koware.getChapters({ mangaId: 123, limit: 200 });\nconsole.log(chapters);",
            category = "Reading",
        ),
        Method(
            name = "getCategories",
            signature = "Koware.getCategories()",
            description = "Returns the categories configured in Koware.",
            parameters = "None.",
            returns = "Array of category objects.",
            example = "const categories = await Koware.getCategories();\nconsole.log(categories.map(c => c.name));",
            category = "Library",
        ),
        Method(
            name = "getHistory",
            signature = "Koware.getHistory(options?)",
            description = "Returns recent reading history.",
            parameters = "options?: { limit?: number }. Limit defaults to 10 and is clamped to 1–50.",
            returns = "Array containing mangaId, chapterId, title, chapterNumber, readAt, readDuration, lastPageRead, chapterRead, isNovel, and coverUrl.",
            example = "const history = await Koware.getHistory({ limit: 10 });\nconsole.log(history);",
            category = "Reading",
        ),
        Method(
            name = "getStats",
            signature = "Koware.getStats()",
            description = "Returns basic library and total reading-duration statistics.",
            parameters = "None.",
            returns = "{ libraryCount: number, totalReadDurationMs: number }",
            example = "const stats = await Koware.getStats();\nconsole.log(stats.libraryCount, stats.totalReadDurationMs);",
            category = "Statistics",
        ),
    )

    fun shim(): String {
        val names = methods.joinToString(",") { "\"${it.name}\"" }
        return SHIM_TEMPLATE
            .replace("%CHANNEL%", CHANNEL)
            .replace("%NAMES%", names)
    }

    private const val SHIM_TEMPLATE = """
(function () {
  var channel = window.%CHANNEL%;
  var pending = {};
  var nextId = 1;

  function call(method, args) {
    return new Promise(function (resolve, reject) {
      if (!channel) {
        reject(new Error('The Koware bridge is not available here.'));
        return;
      }

      var id = nextId++;
      pending[id] = { resolve: resolve, reject: reject };

      channel.postMessage(JSON.stringify({
        id: id,
        method: method,
        args: args
      }));
    });
  }

  if (channel) {
    channel.onmessage = function (event) {
      var msg;
      try {
        msg = JSON.parse(event.data);
      } catch (e) {
        return;
      }

      var entry = pending[msg.id];
      if (!entry) return;
      delete pending[msg.id];

      if (msg.ok) {
        entry.resolve(msg.result);
      } else {
        entry.reject(new Error(msg.error || 'Koware API call failed'));
      }
    };
  }

  var api = {};

  [%NAMES%].forEach(function (name) {
    api[name] = function () {
      return call(name, Array.prototype.slice.call(arguments));
    };
  });

  api.help = function () {
    return call('help', []).then(function (list) {
      console.log('Koware API\n' + list.map(function (method) {
        return method.signature + '\n    ' + method.description;
      }).join('\n'));
      return list;
    });
  };

  window.Koware = new Proxy(api, {
    get: function (target, key) {
      if (typeof key === 'symbol' || key === 'then' || key in target) {
        return target[key];
      }

      throw new Error(
        'Koware.' + key + ' does not exist. Use Koware.help() to list methods.'
      );
    }
  });
})();
"""
}
