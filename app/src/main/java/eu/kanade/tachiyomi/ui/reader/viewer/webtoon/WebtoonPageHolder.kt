package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.content.res.Resources
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import eu.kanade.presentation.reader.OcrTranslationOverlay
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.databinding.ReaderErrorBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressIndicator
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import okio.Buffer
import okio.BufferedSource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR

/**
 * Holder of the webtoon reader for a single page of a chapter.
 *
 * @param frame the root view for this holder.
 * @param viewer the webtoon viewer.
 * @constructor creates a new webtoon holder.
 */
class WebtoonPageHolder(
    private val frame: ReaderPageImageView,
    viewer: WebtoonViewer,
) : WebtoonBaseHolder(frame, viewer) {

    /**
     * Loading progress bar to indicate the current progress.
     */
    private val progressIndicator = createProgressIndicator()

    /**
     * Progress bar container. Needed to keep a minimum height size of the holder, otherwise the
     * adapter would create more views to fill the screen, which is not wanted.
     */
    private lateinit var progressContainer: ViewGroup

    /**
     * Error layout to show when the image fails to load.
     */
    private var errorLayout: ReaderErrorBinding? = null

    /**
     * Getter to retrieve the height of the recycler view.
     */
    private val parentHeight
        get() = viewer.recycler.height

    /**
     * Page of a chapter.
     */
    private var page: ReaderPage? = null

    private val scope = MainScope()

    /**
     * Job for loading the page.
     */
    private var loadJob: Job? = null

    /**
     * Watches the "live translation" switch and runs OCR + translation for this page while it's on.
     */
    private var liveTranslationJob: Job? = null

    /**
     * Live-translation overlay for THIS page. It lives inside the page's own view, so it scrolls,
     * zooms and gets recycled together with the page. Result coordinates are in the page image's
     * pixel space, so the overlay simply stretches them over the page view.
     */
    private var overlayView: ComposeView? = null
    private var overlayUi by mutableStateOf<WebtoonOverlayUi?>(null)
    private var overlayProcessing by mutableStateOf(false)

    init {
        refreshLayoutParams()
        frame.onImageLoaded = { onImageDecoded() }
        frame.onImageLoadError = { error -> setError(error) }
        frame.onScaleChanged = { viewer.activity.hideMenu() }
    }

    /**
     * Binds the given [page] with this view holder, subscribing to its state.
     */
    fun bind(page: ReaderPage) {
        this.page = page
        loadJob?.cancel()
        loadJob = scope.launch { loadPageAndProcessStatus() }

        liveTranslationJob?.cancel()
        clearOverlay()
        liveTranslationJob = scope.launch {
            viewer.activity.viewModel.state
                .map { it.isLiveTranslationActive }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (active) {
                        runLiveTranslation()
                    } else {
                        WebtoonChapterOcr.stop()
                        clearOverlay()
                    }
                }
        }

        refreshLayoutParams()
    }

    private fun refreshLayoutParams() {
        frame.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            if (!viewer.isContinuous) {
                bottomMargin = 15.dpToPx
            }

            val margin = Resources.getSystem().displayMetrics.widthPixels * (viewer.config.sidePadding / 100f)
            marginEnd = margin.toInt()
            marginStart = margin.toInt()
        }
    }

    /**
     * Called when the view is recycled and added to the view pool.
     */
    override fun recycle() {
        loadJob?.cancel()
        loadJob = null
        liveTranslationJob?.cancel()
        liveTranslationJob = null
        clearOverlay()
        removeErrorLayout()
        frame.recycle()
        progressIndicator.setProgress(0)
        progressContainer.isVisible = true
    }

    /**
     * Loads the page and processes changes to the page's status.
     *
     * Returns immediately if there is no page or the page has no PageLoader.
     * Otherwise, this function does not return. It will continue to process status changes until
     * the Job is cancelled.
     */
    private suspend fun loadPageAndProcessStatus() {
        val page = page ?: return
        val loader = page.chapter.pageLoader ?: return

        supervisorScope {
            launchIO {
                loader.loadPage(page)
            }
            page.statusFlow.collectLatest { state ->
                when (state) {
                    Page.State.Queue -> setQueued()
                    Page.State.LoadPage -> setLoading()
                    Page.State.DownloadImage -> {
                        setDownloading()
                        page.progressFlow.collectLatest { value ->
                            progressIndicator.setProgress(value)
                        }
                    }
                    Page.State.Ready -> setImage()
                    is Page.State.Error -> setError(state.error)
                }
            }
        }
    }

    /**
     * Called when the page is queued.
     */
    private fun setQueued() {
        progressContainer.isVisible = true
        progressIndicator.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is loading.
     */
    private fun setLoading() {
        progressContainer.isVisible = true
        progressIndicator.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is downloading
     */
    private fun setDownloading() {
        progressContainer.isVisible = true
        progressIndicator.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is ready.
     */
    private suspend fun setImage() {
        progressIndicator.setProgress(0)
        val streamFn = page?.stream ?: return

        try {
            val (source, isAnimated) = withIOContext {
                val source = streamFn().use { process(Buffer().readFrom(it)) }
                val isAnimated = ImageUtil.isAnimatedAndSupported(source)
                Pair(source, isAnimated)
            }
            withUIContext {
                frame.setImage(
                    source,
                    isAnimated,
                    ReaderPageImageView.Config(
                        zoomDuration = viewer.config.doubleTapAnimDuration,
                        minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH,
                        cropBorders = viewer.config.imageCropBorders,
                    ),
                )
                removeErrorLayout()
                // The image view may have just been (re)created on top of the overlay.
                overlayView?.bringToFront()
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
            withUIContext {
                setError(e)
            }
        }
    }

    private fun process(imageSource: BufferedSource): BufferedSource = processForDisplay(viewer, imageSource)

    /**
     * Called when the page has an error.
     */
    private fun setError(error: Throwable?) {
        progressContainer.isVisible = false
        initErrorLayout(error)
    }

    /**
     * Called when the image is decoded and going to be displayed.
     */
    private fun onImageDecoded() {
        progressContainer.isVisible = false
        removeErrorLayout()
    }

    /**
     * Creates a new progress bar.
     */
    private fun createProgressIndicator(): ReaderProgressIndicator {
        progressContainer = FrameLayout(context)
        frame.addView(progressContainer, MATCH_PARENT, parentHeight)

        val progress = ReaderProgressIndicator(context).apply {
            updateLayoutParams<FrameLayout.LayoutParams> {
                updateMargins(top = parentHeight / 4)
            }
        }
        progressContainer.addView(progress)
        return progress
    }

    /**
     * Initializes a button to retry pages.
     */
    private fun initErrorLayout(error: Throwable?): ReaderErrorBinding {
        if (errorLayout == null) {
            errorLayout = ReaderErrorBinding.inflate(LayoutInflater.from(context), frame, true)
            errorLayout?.root?.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, (parentHeight * 0.8).toInt())
            errorLayout?.actionRetry?.setOnClickListener {
                page?.let { it.chapter.pageLoader?.retryPage(it) }
            }
        }

        val imageUrl = page?.imageUrl
        errorLayout?.actionOpenInWebView?.isVisible = imageUrl != null
        if (imageUrl != null) {
            if (imageUrl.startsWith("http", true)) {
                errorLayout?.actionOpenInWebView?.setOnClickListener {
                    val sourceId = viewer.activity.viewModel.manga?.source

                    val intent = WebViewActivity.newIntent(context, imageUrl, sourceId)
                    context.startActivity(intent)
                }
            }
        }

        errorLayout?.errorMessage?.text = with(context) { error?.formattedMessage }
            ?: context.stringResource(MR.strings.decode_image_error)

        return errorLayout!!
    }

    /**
     * Removes the decode error layout from the holder, if found.
     */
    private fun removeErrorLayout() {
        errorLayout?.let {
            frame.removeView(it.root)
            errorLayout = null
        }
    }

    // region Live translation overlay

    /**
     * OCRs and translates this page once its image is ready, then shows the result on top of it.
     * Runs inside collectLatest, so switching the feature off or recycling the holder cancels it.
     * Results are cached per page by [WebtoonPageOcr], so scrolling back is instant.
     */
    private suspend fun runLiveTranslation() {
        val page = page ?: return

        // The image has to exist before there is anything to read.
        page.statusFlow.first { it == Page.State.Ready }
        val streamFn = page.stream ?: return
        val pageKey = "${page.chapter.chapter.id}-${page.index}"

        // Translate the rest of the chapter in the background (does nothing if it's already running).
        startChapterTranslation(page)

        WebtoonPageOcr.cached(pageKey)?.let { cached ->
            showOverlay(cached)
            return
        }

        ensureOverlay()
        overlayProcessing = true
        val outcome = try {
            WebtoonPageOcr.process(pageKey) {
                // Same processing as the displayed image, so OCR coordinates match what is on screen.
                withIOContext { streamFn().use { process(Buffer().readFrom(it)).readByteArray() } }
            }
        } finally {
            overlayProcessing = false
        }

        when (outcome) {
            is WebtoonPageOcr.Outcome.Ready -> showOverlay(outcome.ui)
            is WebtoonPageOcr.Outcome.Failed -> {
                logcat(LogPriority.WARN) { "Live translation failed: ${outcome.message}" }
                if (WebtoonPageOcr.shouldReport(outcome.message)) {
                    context.toast("Live translation failed: ${outcome.message}")
                }
            }
        }
    }

    private fun startChapterTranslation(page: ReaderPage) {
        val chapterId = page.chapter.chapter.id ?: return
        val pages = page.chapter.pages ?: return
        val viewerRef = viewer
        WebtoonChapterOcr.start(chapterId, pages, page.index) { p ->
            val streamFn = p.stream ?: return@start null
            streamFn().use { processForDisplay(viewerRef, Buffer().readFrom(it)).readByteArray() }
        }
    }

    private fun showOverlay(ui: WebtoonOverlayUi) {
        ensureOverlay()
        overlayUi = ui
        overlayView?.bringToFront()
    }

    private fun clearOverlay() {
        overlayUi = null
        overlayProcessing = false
    }

    private fun ensureOverlay() {
        if (overlayView != null) return
        val view = ComposeView(context).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            isClickable = false
            isFocusable = false
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                val ui = overlayUi
                val processing = overlayProcessing
                if (ui != null) {
                    OcrTranslationOverlay(
                        ocrResults = ui.ocr,
                        translatedResults = ui.translated,
                        isProcessing = processing,
                        imageWidth = ui.width,
                        imageHeight = ui.height,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (processing) {
                    OcrTranslationOverlay(
                        ocrResults = emptyList(),
                        translatedResults = emptyList(),
                        isProcessing = true,
                        imageWidth = 0,
                        imageHeight = 0,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        frame.addView(view)
        overlayView = view
    }

    // endregion

    private companion object {
        /**
         * Applies the dual-page split/rotate settings. Shared so that OCR of pages that are not on screen
         * (whole-chapter translation) sees exactly the same image, and the same coordinates, as the reader.
         */
        fun processForDisplay(viewer: WebtoonViewer, imageSource: BufferedSource): BufferedSource {
            if (viewer.config.dualPageRotateToFit) {
                return rotateDualPage(viewer, imageSource)
            }

            if (viewer.config.dualPageSplit) {
                val isDoublePage = ImageUtil.isWideImage(imageSource)
                if (isDoublePage) {
                    val upperSide = if (viewer.config.dualPageInvert) ImageUtil.Side.LEFT else ImageUtil.Side.RIGHT
                    return ImageUtil.splitAndMerge(imageSource, upperSide)
                }
            }

            return imageSource
        }

        fun rotateDualPage(viewer: WebtoonViewer, imageSource: BufferedSource): BufferedSource {
            val isDoublePage = ImageUtil.isWideImage(imageSource)
            return if (isDoublePage) {
                val rotation = if (viewer.config.dualPageRotateToFitInvert) -90f else 90f
                ImageUtil.rotateImage(imageSource, rotation)
            } else {
                imageSource
            }
        }
    }
}
