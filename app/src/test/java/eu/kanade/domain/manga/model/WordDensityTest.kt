package eu.kanade.domain.manga.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WordDensityTest {

    @Test
    fun `countWords ignores markup and decodes entities`() {
        val html = """
            <html><head><title>Ignored title</title><style>p { color: red; }</style></head>
            <body><h1>Chapter&nbsp;1</h1><p>Tom &amp; Jerry said &lt;hi&gt;.</p>
            <script>var notWords = 1;</script><p>Last<br/>line</p></body></html>
        """.trimIndent()

        // Chapter, 1, Tom, &, Jerry, said, <hi>., Last, line
        assertEquals(9, WordDensity.countWords(html))
    }

    @Test
    fun `countWords handles plain text and blank content`() {
        assertEquals(4, WordDensity.countWords("one two\nthree\t four"))
        assertEquals(0, WordDensity.countWords("   \n "))
        assertEquals(0, WordDensity.countWords("<p> </p>"))
    }

    @Test
    fun `averageWords divides by counted chapters`() {
        assertEquals(450, WordDensity(totalWords = 900, countedChapters = 2, totalChapters = 5).averageWords)
        assertEquals(0, WordDensity(totalWords = 0, countedChapters = 0, totalChapters = 5).averageWords)
    }

    @Test
    fun `unreadable chapters are kept apart from chapters that are not downloaded`() {
        val density = WordDensity(totalWords = 1500, countedChapters = 3, totalChapters = 10, unreadableChapters = 2)

        assertEquals(500, density.averageWords)
        assertEquals(5, density.notDownloadedChapters)
        val allAvailable = WordDensity(totalWords = 900, countedChapters = 3, totalChapters = 5, unreadableChapters = 2)
        assertEquals(0, allAvailable.notDownloadedChapters)
    }

    @Test
    fun `densityTier follows 400 word first tier then 100 word steps`() {
        assertEquals(1, WordDensity.densityTier(0))
        assertEquals(1, WordDensity.densityTier(400))
        assertEquals(2, WordDensity.densityTier(401))
        assertEquals(2, WordDensity.densityTier(500))
        assertEquals(3, WordDensity.densityTier(501))
        assertEquals(9, WordDensity.densityTier(1200))
        assertEquals(WordDensity.MAX_TIER, WordDensity.densityTier(1201))
        assertEquals(WordDensity.MAX_TIER, WordDensity.densityTier(50_000))
    }

    @Test
    fun `tier bounds match densityTier`() {
        assertEquals(1, WordDensity.tierMinWords(1))
        assertEquals(400, WordDensity.tierMaxWords(1))
        assertEquals(401, WordDensity.tierMinWords(2))
        assertEquals(500, WordDensity.tierMaxWords(2))
        assertEquals(1201, WordDensity.tierMinWords(WordDensity.MAX_TIER))
        assertNull(WordDensity.tierMaxWords(WordDensity.MAX_TIER))

        for (tier in 1..WordDensity.MAX_TIER) {
            assertEquals(tier, WordDensity.densityTier(WordDensity.tierMinWords(tier)))
            WordDensity.tierMaxWords(tier)?.let { assertEquals(tier, WordDensity.densityTier(it)) }
        }
    }
}
