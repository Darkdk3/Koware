package eu.kanade.tachiyomi.ui.browse.extension

import eu.kanade.tachiyomi.extension.model.Extension
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NovelExtensionsUpdateAllTest {

    private fun installedJsPlugin(
        name: String = "Fake Novel",
        isInstalled: Boolean = true,
        hasUpdate: Boolean = true,
    ): Extension.JsPlugin = Extension.JsPlugin(
        name = name,
        pkgName = "app.tsundoku.jsplugin.${name.lowercase().replace(" ", "-")}",
        versionName = "1.0.1",
        versionCode = 101L,
        libVersion = 0.0,
        lang = "en",
        isNsfw = false,
        isNovel = true,
        sources = listOf(
            Extension.Available.Source(
                id = 1L,
                lang = "en",
                name = name,
                baseUrl = "https://example.com",
            ),
        ),
        iconUrl = "",
        repoUrl = "https://example.com/plugins.json",
        isInstalled = isInstalled,
        hasUpdate = hasUpdate,
    )

    private fun installedApk(hasUpdate: Boolean): Extension.Installed = Extension.Installed(
        name = "Some Novel",
        pkgName = "eu.kanade.tachiyomi.extension.en.somenovel",
        versionName = "1.0.1",
        versionCode = 2L,
        libVersion = 1.5,
        lang = "en",
        isNsfw = false,
        isNovel = true,
        pkgFactory = null,
        sources = emptyList(),
        icon = null,
        hasUpdate = hasUpdate,
        isObsolete = false,
        isShared = true,
        store = null,
    )

    private fun availableJsPlugin(): Extension.JsPlugin = installedJsPlugin(
        name = "Not installed",
        isInstalled = false,
        hasUpdate = false,
    )

    private fun availableApk(): Extension.Available = Extension.Available(
        name = "Available Novel",
        pkgName = "eu.kanade.tachiyomi.extension.en.availablenovel",
        versionName = "1.0.0",
        versionCode = 1L,
        libVersion = 1.5,
        lang = "en",
        isNsfw = false,
        isNovel = true,
        sources = emptyList(),
        apkUrl = "https://example.com/ext.apk",
        iconUrl = "",
        store = ExtensionStore(
            indexUrl = "https://example.com/index.json",
            name = "Test repo",
            badgeLabel = "TEST",
            signingKey = "",
            contact = ExtensionStore.Contact(website = "", discord = null),
            isLegacy = false,
            extensionListUrl = null,
        ),
    )

    private fun untrusted(): Extension.Untrusted = Extension.Untrusted(
        name = "Untrusted Novel",
        pkgName = "eu.kanade.tachiyomi.extension.en.untrusted",
        versionName = "1.0.0",
        versionCode = 1L,
        libVersion = 1.5,
        signatureHash = "abc",
        isNovel = true,
    )

    @Test
    fun `update all plan routes every pending novel update to its install path`() {
        val displayed = listOf(
            installedJsPlugin(name = "Fake Novel A"),
            installedJsPlugin(name = "Fake Novel B"),
            installedApk(hasUpdate = true),
            installedJsPlugin(name = "Up to date", hasUpdate = false),
            installedApk(hasUpdate = false),
            availableJsPlugin(),
        )

        val plan = displayed.toUpdateAllPlan()

        assertEquals(listOf("Fake Novel A", "Fake Novel B"), plan.jsPluginUpdates.map { it.name })
        assertEquals(listOf("Some Novel"), plan.apkUpdates.map { it.name })
    }

    @Test
    fun `update all plan with only js plugin updates pending is not empty`() {
        val plan = listOf(installedJsPlugin(name = "Fake Novel A"), installedJsPlugin(name = "Fake Novel B"))
            .toUpdateAllPlan()

        assertEquals(2, plan.jsPluginUpdates.size)
        assertTrue(plan.apkUpdates.isEmpty())
    }

    @Test
    fun `update all plan excludes available and untrusted extensions`() {
        val plan = listOf(availableApk(), untrusted()).toUpdateAllPlan()

        assertTrue(plan.apkUpdates.isEmpty())
        assertTrue(plan.jsPluginUpdates.isEmpty())
    }
}
