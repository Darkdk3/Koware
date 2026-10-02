package eu.kanade.domain.manga.model

import org.jsoup.Jsoup

/**
 * Word statistics for the downloaded chapters of a novel.
 *
 * @property totalWords words across every counted chapter.
 * @property countedChapters chapters whose content could be read and counted.
 * @property totalChapters every chapter of the entry, downloaded or not.
 * @property unreadableChapters chapters that were available but whose content could not be read.
 */
data class WordDensity(
    val totalWords: Long,
    val countedChapters: Int,
    val totalChapters: Int,
    val unreadableChapters: Int = 0,
) {
    /** Chapters left out because they are not downloaded. */
    val notDownloadedChapters: Int
        get() = (totalChapters - countedChapters - unreadableChapters).coerceAtLeast(0)

    /** Average words per counted chapter, the novel's word density. */
    val averageWords: Long
        get() = if (countedChapters == 0) 0 else totalWords / countedChapters

    val tier: Int
        get() = densityTier(averageWords)

    companion object {
        /** Averages up to this many words per chapter are tier 1. */
        const val FIRST_TIER_MAX_WORDS = 400L

        /** Each following tier spans this many words. */
        const val TIER_STEP_WORDS = 100L

        const val MAX_TIER = 10

        /**
         * Maps an average chapter length onto a small 1..[MAX_TIER] indicator:
         * 1-400 words is 1, 401-500 is 2, 501-600 is 3, and so on, capped at [MAX_TIER].
         */
        fun densityTier(averageWords: Long): Int {
            if (averageWords <= FIRST_TIER_MAX_WORDS) return 1
            val tier = 2 + (averageWords - FIRST_TIER_MAX_WORDS - 1) / TIER_STEP_WORDS
            return tier.coerceAtMost(MAX_TIER.toLong()).toInt()
        }

        /** Lowest average word count that lands in [tier]. */
        fun tierMinWords(tier: Int): Long =
            if (tier <= 1) 1 else FIRST_TIER_MAX_WORDS + 1 + (tier - 2) * TIER_STEP_WORDS

        /** Highest average word count that lands in [tier], or null for the open-ended top tier. */
        fun tierMaxWords(tier: Int): Long? =
            if (tier >= MAX_TIER) null else FIRST_TIER_MAX_WORDS + (tier - 1) * TIER_STEP_WORDS

        /**
         * Counts whitespace-separated words in chapter HTML or plain text. Only the body is
         * counted: markup, scripts and styles are dropped and entities such as `&nbsp;` are decoded.
         */
        fun countWords(content: String): Int {
            if (content.isBlank()) return 0
            val text = Jsoup.parse(content).body().text()
            var words = 0
            var inWord = false
            for (char in text) {
                // isSpaceChar also covers no-break spaces, which Jsoup keeps when decoding &nbsp;
                val isSeparator = char.isWhitespace() || Character.isSpaceChar(char)
                if (!isSeparator && !inWord) words++
                inWord = !isSeparator
            }
            return words
        }
    }
}
