package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Translates a whole chapter in the background, so pages are already done by the time you scroll
 * to them (the per-page results land in [WebtoonPageOcr]'s cache and are picked up from there).
 *
 * Pages are processed one after another starting at the page being read, then wrapping around to
 * the pages before it. Page images that haven't been downloaded yet are requested from the page
 * loader as needed.
 */
object WebtoonChapterOcr {

    private const val PAGE_READY_TIMEOUT_MS = 45_000L
    private const val MAX_CONSECUTIVE_FAILURES = 3
    private const val PAUSE_AFTER_FAILURES_MS = 20_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var job: Job? = null
    private var runningChapterId: Long? = null

    @Volatile
    private var pausedUntil = 0L

    /**
     * Starts translating [pages] of chapter [chapterId], beginning at [startIndex]. Does nothing if
     * that chapter is already being processed (so it's safe to call from every page that binds).
     *
     * @param bytesFor returns the encoded image bytes of a page, as the reader shows it
     */
    @Synchronized
    fun start(
        chapterId: Long,
        pages: List<ReaderPage>,
        startIndex: Int,
        bytesFor: suspend (ReaderPage) -> ByteArray?,
    ) {
        if (System.currentTimeMillis() < pausedUntil) return
        if (runningChapterId == chapterId && job?.isActive == true) return

        job?.cancel()
        runningChapterId = chapterId
        job = scope.launch {
            val first = startIndex.coerceIn(0, pages.size)
            val order = (first until pages.size) + (0 until first)

            var consecutiveFailures = 0
            for (index in order) {
                ensureActive()
                val page = pages.getOrNull(index) ?: continue
                if (page is InsertPage) continue

                val key = "$chapterId-${page.index}"
                if (WebtoonPageOcr.cached(key) != null) continue
                if (!awaitReady(page)) continue

                when (val outcome = WebtoonPageOcr.process(key) { bytesFor(page) }) {
                    is WebtoonPageOcr.Outcome.Ready -> consecutiveFailures = 0
                    is WebtoonPageOcr.Outcome.Failed -> {
                        logcat(LogPriority.WARN) { "Chapter OCR: page ${page.index} failed: ${outcome.message}" }
                        consecutiveFailures++
                        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                            // Probably a broken engine / missing API key: don't hammer it page after page.
                            pausedUntil = System.currentTimeMillis() + PAUSE_AFTER_FAILURES_MS
                            break
                        }
                    }
                }
            }
        }
    }

    /** Stops any chapter translation in progress (e.g. when live translation is switched off). */
    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        runningChapterId = null
    }

    /** Makes sure the page image exists, asking the page loader for it if needed. */
    private suspend fun awaitReady(page: ReaderPage): Boolean {
        if (page.status == Page.State.Ready) return true
        val loader = page.chapter.pageLoader ?: return false

        return coroutineScope {
            // loadPage() suspends until cancelled, so run it on the side and stop it once we have the page.
            val loading = launch(Dispatchers.IO) { loader.loadPage(page) }
            try {
                val finalState = withTimeoutOrNull(PAGE_READY_TIMEOUT_MS) {
                    page.statusFlow.first { it == Page.State.Ready || it is Page.State.Error }
                }
                finalState == Page.State.Ready
            } finally {
                loading.cancel()
            }
        }
    }
}
