package eu.kanade.domain.ui.model

/**
 * Overall UI style of the app.
 *
 * [LEGACY] keeps the original appearance unchanged. [MODERN] applies the redesigned
 * "Koware" look - card-based library grid, neutral manga-details header with a capped
 * cover tint - while every appearance setting (theme, amoled, cover theme, backdrop,
 * freeform covers, ...) keeps working exactly the same in both styles.
 */
enum class UiStyle(val label: String) {
    LEGACY("Legacy"),
    MODERN("Modern"),
}
