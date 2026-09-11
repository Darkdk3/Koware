package tachiyomi.domain.translation.model


interface TranslationEngine {
    val id: Long
    val name: String
    val requiresApiKey: Boolean
    val isRateLimited: Boolean
    val isOffline: Boolean
    val supportedLanguages: List<Pair<String, String>>

    /**
     * Whether this engine can handle arbitrary prompts (recommendations, summaries, etc.),
     * not just source→target translation. True only for general-purpose LLM engines —
     * dedicated translation-only APIs (Libre, DeepL, Google Translate, Systran) leave this false.
     */
    val supportsGeneralPrompts: Boolean get() = false

    suspend fun translate(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): TranslationResult

    suspend fun translateSingle(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): TranslationResult {
        return translate(listOf(text), sourceLanguage, targetLanguage)
    }

    /**
     * Send a free-form prompt and get a text response back. Only meaningful when
     * [supportsGeneralPrompts] is true.
     *
     * @param apiKeyOverride when non-null, used instead of the engine's own configured key -
     * lets AI-only features (recommendations, etc.) use a separate key from translation.
     */
    suspend fun complete(prompt: String, apiKeyOverride: String? = null): TranslationResult {
        return TranslationResult.Error(
            "This engine only supports translation, not general prompts.",
            TranslationResult.ErrorCode.LANGUAGE_NOT_SUPPORTED,
        )
    }

    fun isConfigured(): Boolean = true
}

sealed class TranslationResult {
    data class Success(
        val translatedTexts: List<String>,
        val detectedSourceLanguage: String? = null,
    ) : TranslationResult()

    data class Error(
        val message: String,
        val errorCode: ErrorCode = ErrorCode.UNKNOWN,
    ) : TranslationResult()

    enum class ErrorCode {
        UNKNOWN,
        NETWORK_ERROR,
        API_KEY_INVALID,
        API_KEY_MISSING,
        RATE_LIMITED,
        QUOTA_EXCEEDED,
        LANGUAGE_NOT_SUPPORTED,
        TEXT_TOO_LONG,
        SERVICE_UNAVAILABLE,
    }
}

object LanguageCodes {
    val COMMON_LANGUAGES = listOf(
        "auto" to "Auto-detect",
        "en" to "English",
        "zh" to "Chinese (Simplified)",
        "zh-TW" to "Chinese (Traditional)",
        "ja" to "Japanese",
        "ko" to "Korean",
        "es" to "Spanish",
        "fr" to "French",
        "de" to "German",
        "it" to "Italian",
        "pt" to "Portuguese",
        "ru" to "Russian",
        "ar" to "Arabic",
        "hi" to "Hindi",
        "th" to "Thai",
        "vi" to "Vietnamese",
        "id" to "Indonesian",
        "ms" to "Malay",
        "tl" to "Filipino",
        "tr" to "Turkish",
        "pl" to "Polish",
        "nl" to "Dutch",
        "sv" to "Swedish",
        "da" to "Danish",
        "fi" to "Finnish",
        "no" to "Norwegian",
        "uk" to "Ukrainian",
        "cs" to "Czech",
        "ro" to "Romanian",
        "hu" to "Hungarian",
        "el" to "Greek",
        "he" to "Hebrew",
        "fa" to "Persian",
        "bn" to "Bengali",
    )

    fun getDisplayName(code: String): String {
        return COMMON_LANGUAGES.find { it.first == code }?.second ?: code
    }
}
