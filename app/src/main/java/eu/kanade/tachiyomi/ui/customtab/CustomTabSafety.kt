package eu.kanade.tachiyomi.ui.customtab

import android.net.Uri

object CustomTabSafety {

    val DEFAULT_HOSTS: Set<String> = setOf(
        "youtube.com",
        "youtube-nocookie.com",
    )

    private val HOST_REGEX = Regex(
        "^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$",
    )

    /**
     * Turns "https://www.example.com/page" or "example.com" into "example.com".
     * Returns null when the input is not a usable site.
     */
    fun normalizeHost(input: String): String? {
        val raw = input.trim().lowercase()
        if (raw.isEmpty()) return null

        val withScheme = if ("://" in raw) raw else "https://$raw"
        val host = runCatching { Uri.parse(withScheme).host }
            .getOrNull()
            ?.removePrefix("www.")
            ?: return null

        return if (HOST_REGEX.matches(host)) host else null
    }

    /** A host is allowed if it matches an entry or is a subdomain of one. */
    fun isAllowed(host: String?, allowed: Set<String>): Boolean {
        if (host == null) return false
        val h = host.lowercase()
        return allowed.any { h == it || h.endsWith(".$it") }
    }
}
