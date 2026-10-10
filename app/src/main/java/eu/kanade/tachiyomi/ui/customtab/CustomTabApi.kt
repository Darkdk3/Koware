package eu.kanade.tachiyomi.ui.customtab

import org.json.JSONObject

/**
 * Single source of truth for the Koware JS API: the list below drives the
 * in-editor API reference, Koware.help(), and the functions the page can call.
 */
object CustomTabApi {

    const val VERSION = 1

    /** Name of the message channel the app exposes to the custom page. */
    const val CHANNEL = "_koware"

    data class Method(
        val name: String,
        val signature: String,
        val description: String,
        val returns: String,
        val example: String,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("name", name)
            .put("signature", signature)
            .put("description", description)
            .put("returns", returns)
            .put("example", example)
    }

    val methods: List<Method> = listOf(
        Method(
            name = "help",
            signature = "Koware.help()",
            description = "Prints every available function in the console and returns the list.",
            returns = "array of { name, signature, description, returns, example }",
            example = "Koware.help();",
        ),
        Method(
            name = "getInfo",
            signature = "Koware.getInfo()",
            description = "Basic information about the app.",
            returns = "{ appVersion: string, apiVersion: number }",
            example = """(async () => {
  const info = await Koware.getInfo();
  document.body.append('Koware ' + info.appVersion);
})();""",
        ),
        Method(
            name = "getTheme",
            signature = "Koware.getTheme()",
            description = "The app's current colors, as hex strings. The same colors are " +
                "also available in CSS as var(--bg), var(--fg), var(--surface), " +
                "var(--muted), and var(--primary).",
            returns = "{ isDark: boolean, background, foreground, surface, muted, primary }",
            example = """(async () => {
  const theme = await Koware.getTheme();
  document.body.style.borderTop = '4px solid ' + theme.primary;
})();""",
        ),
    )

    /** Script placed before the user's JS. It defines the global Koware object. */
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
      channel.postMessage(JSON.stringify({ id: id, method: method, args: args }));
    });
  }

  if (channel) {
    channel.onmessage = function (event) {
      var msg;
      try { msg = JSON.parse(event.data); } catch (e) { return; }
      var entry = pending[msg.id];
      if (!entry) return;
      delete pending[msg.id];
      if (msg.ok) entry.resolve(msg.result);
      else entry.reject(new Error(msg.error));
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
      console.log('Koware API\n' + list.map(function (m) {
        return m.signature + '\n    ' + m.description;
      }).join('\n'));
      return list;
    });
  };

  window.Koware = new Proxy(api, {
    get: function (target, key) {
      if (typeof key === 'symbol' || key === 'then' || key in target) {
        return target[key];
      }
      throw new Error('Koware.' + key + ' does not exist. Call Koware.help() to see what is available.');
    }
  });
})();
"""
}
