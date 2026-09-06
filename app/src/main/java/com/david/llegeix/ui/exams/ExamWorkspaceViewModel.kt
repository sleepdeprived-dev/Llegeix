package com.david.llegeix.ui.exams

import android.app.Application
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.data.db.entity.ExamAttemptEntity
import com.david.llegeix.data.db.entity.ExamAudioEntity
import com.david.llegeix.data.db.entity.ExamEntity
import com.david.llegeix.data.db.entity.ExamMarkEntity
import com.david.llegeix.data.db.entity.MarkKind
import com.david.llegeix.data.exam.ExamExporter
import com.david.llegeix.data.exam.ExamRepository
import com.david.llegeix.data.exam.MarkGeometry
import android.net.Uri
import com.david.llegeix.R
import com.david.llegeix.ui.common.UiText
import com.david.llegeix.pdf.PdfPageRenderer
import com.david.llegeix.pdf.PdfiumPageRenderer
import com.david.llegeix.pdf.TickBox
import com.david.llegeix.pdf.TickBoxes
import com.david.llegeix.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the workspace needs that is not a page image or a mark. */
data class WorkspaceState(
    val exam: ExamEntity? = null,
    val attempt: ExamAttemptEntity? = null,
    val isOpening: Boolean = true,
    val error: Boolean = false,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    /** Where to open, read once when the sitting is loaded. */
    val initialPage: Int = 0,
    val tool: ExamTool = ExamTool.VIEW,
    val inkColor: Int = ExamInk.Default,
    val strokeWidth: Float = StrokeWidths.MEDIUM,
    val highlightColor: Int = com.david.llegeix.ui.common.HighlightColors.Yellow,
    /**
     * Whether the official answer sheet is showing instead of the paper.
     *
     * A swap rather than a split. Two documents side by side on a phone is two
     * columns of unreadable type, and the question being answered is "was I
     * right", which is asked one page at a time.
     */
    val showAnswerKey: Boolean = false,
    val hasAnswerKey: Boolean = false,
    val answerKeyPageCount: Int = 0,
    val audio: List<ExamAudioEntity> = emptyList(),
    val zoom: Float = 1f,
    /** True while a page is being written out, so the button can say so. */
    val isExporting: Boolean = false,
) {
    val isFinished: Boolean get() = attempt?.finishedAt != null

    /** What the pager is showing: the paper, or the key. */
    val visiblePageCount: Int
        get() = if (showAnswerKey) answerKeyPageCount else pageCount

    /** Writing is only ever onto the paper, never onto the answer sheet. */
    val canWrite: Boolean get() = !showAnswerKey
}

/**
 * One sitting, open.
 *
 * Two renderers rather than one, because the paper and its answer sheet are two
 * documents and the reader flips between them. The key's renderer is opened
 * lazily — most papers have no key, and the ones that do are not always looked
 * at — but once open it is kept, since flipping back and forth is the whole
 * point of having it.
 *
 * Pages are always rendered **whole**. The reader's margin-cropping preference
 * is deliberately not consulted here: cropping makes the bitmap a sub-rectangle
 * of the page, and every mark is stored as a fraction *of the page*, so a
 * cropped render would put every answer somewhere other than where it was
 * written. See [ExamMarkEntity].
 */
class ExamWorkspaceViewModel(
    private val application: Application,
    private val repository: ExamRepository,
    private val attemptId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkspaceState())
    val state: StateFlow<WorkspaceState> = _state.asStateFlow()

    private var paper: PdfPageRenderer? = null
    private var answerKey: PdfPageRenderer? = null

    /**
     * Rendered pages, budgeted in kilobytes rather than in pages.
     *
     * The same reasoning as the reader's cache: a page is not a fixed size, so
     * counting pages quietly becomes counting tens of megabytes on a large
     * sheet. Bitmaps are never recycled on eviction — the canvas may still be
     * drawing one — so eviction is left to the collector.
     */
    private val pageCache = object : LruCache<String, Bitmap>(cacheBudgetKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    /**
     * What is on the page the reader is looking at.
     *
     * Narrowed to the three things that actually decide the answer before
     * `flatMapLatest` sees it. Mapping the whole state would re-subscribe the
     * query on *every* change to it — and zoom is part of that state, so a
     * single pinch would re-query the database once per frame while the reader
     * was trying to look at the page.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val marks: StateFlow<List<ExamMarkEntity>> = _state
        .map { Triple(it.attempt != null, it.showAnswerKey, it.currentPage) }
        .distinctUntilChanged()
        .flatMapLatest { (loaded, showingKey, page) ->
            // Nothing is drawn over the answer sheet: it is somebody else's
            // document and the reader's work does not belong on it.
            if (!loaded || showingKey) {
                flowOf(emptyList())
            } else {
                repository.observeMarks(attemptId, page)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        open()
    }

    private fun open() = viewModelScope.launch {
        val attempt = repository.attempt(attemptId)
        val exam = attempt?.let { repository.exam(it.examId) }
        if (attempt == null || exam == null) {
            _state.update { it.copy(isOpening = false, error = true) }
            return@launch
        }

        val opened = runCatchingCancellable {
            PdfiumPageRenderer.open(application, repository.fileFor(exam.fileName).toUri())
        }.getOrNull()
        if (opened == null) {
            _state.update { it.copy(isOpening = false, error = true) }
            return@launch
        }
        paper = opened

        val resume = attempt.lastPage.coerceIn(0, (opened.pageCount - 1).coerceAtLeast(0))
        _state.update {
            it.copy(
                exam = exam,
                attempt = attempt,
                isOpening = false,
                pageCount = opened.pageCount,
                currentPage = resume,
                initialPage = resume,
                hasAnswerKey = exam.answerKeyFileName != null,
                answerKeyPageCount = exam.answerKeyPageCount ?: 0,
            )
        }
        repository.recordAttemptOpened(attemptId, resume)

        launch {
            repository.observeAudio(exam.id).collect { tracks ->
                _state.update { it.copy(audio = tracks) }
            }
        }
        // The attempt's own row is watched so "finished" reflects what the menu
        // did without the screen having to be left and come back to.
        launch {
            repository.observeAttempt(attemptId).collect { current ->
                if (current != null) _state.update { it.copy(attempt = current) }
            }
        }
    }

    /**
     * Boxes found on the page the reader is on, for the tick tool to snap to.
     *
     * Only ever a convenience. See [TickBoxes]: it will miss boxes and it will
     * invent them, so a tap near one of these snaps into it and a tap anywhere
     * else places a tick exactly where the finger went. Nothing depends on the
     * list being right, or on there being one at all.
     */
    private val _tickBoxes = MutableStateFlow<List<TickBox>>(emptyList())
    val tickBoxes: StateFlow<List<TickBox>> = _tickBoxes.asStateFlow()

    private var detectJob: Job? = null

    /**
     * Look for tick boxes, but only when the tick tool is actually in hand.
     *
     * Scanning a page costs a full-page pixel walk, and doing it on every page
     * turn regardless of tool would be paying for a feature on behalf of a
     * reader who is writing with a pen.
     */
    private fun refreshTickBoxes() {
        detectJob?.cancel()
        val current = _state.value
        if (current.tool != ExamTool.TICK || current.showAnswerKey) {
            _tickBoxes.value = emptyList()
            return
        }
        val page = current.currentPage
        detectJob = viewModelScope.launch {
            val boxes = withContext(Dispatchers.Default) {
                val bitmap = renderPage(page, DETECT_WIDTH_PX) ?: return@withContext emptyList()
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                TickBoxes.detect(pixels, bitmap.width, bitmap.height)
            }
            // The reader may have turned the page or changed tool while the
            // scan ran; a stale list would snap ticks to boxes on another page.
            if (_state.value.currentPage == page && _state.value.tool == ExamTool.TICK) {
                _tickBoxes.value = boxes
            }
        }
    }

    /** Where a stored copy is, for the audio bar to hand to its player. */
    fun fileFor(fileName: String) = repository.fileFor(fileName)

    // ---- Pages ------------------------------------------------------------

    /** Null when the page could not be drawn; the canvas shows a placeholder. */
    suspend fun renderPage(index: Int, widthPx: Int): Bitmap? {
        val showingKey = _state.value.showAnswerKey
        val renderer = if (showingKey) ensureAnswerKey() else paper
        if (renderer == null) return null
        val key = "${if (showingKey) "k" else "p"}$index@$widthPx"
        pageCache.get(key)?.let { return it }
        // crop = false, always. See the class comment: a cropped bitmap is not
        // the page the marks were positioned against.
        return runCatchingCancellable { renderer.renderPage(index, widthPx, crop = false) }
            .onSuccess { pageCache.put(key, it) }
            .getOrNull()
    }

    private suspend fun ensureAnswerKey(): PdfPageRenderer? {
        answerKey?.let { return it }
        val fileName = _state.value.exam?.answerKeyFileName ?: return null
        val opened = runCatchingCancellable {
            PdfiumPageRenderer.open(application, repository.fileFor(fileName).toUri())
        }.getOrNull()
        answerKey = opened
        return opened
    }

    fun onPageChanged(index: Int) {
        if (index == _state.value.currentPage) return
        _state.update { it.copy(currentPage = index) }
        refreshTickBoxes()
        // Only the paper's position is remembered. Where the reader had got to
        // in the answer sheet is not where they were working.
        if (!_state.value.showAnswerKey) {
            viewModelScope.launch { repository.recordAttemptOpened(attemptId, index) }
        }
    }

    fun onZoomChanged(zoom: Float) {
        _state.update { it.copy(zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)) }
    }

    fun onToggleAnswerKey() {
        _state.update {
            if (!it.hasAnswerKey) {
                it
            } else {
                val showing = !it.showAnswerKey
                it.copy(
                    showAnswerKey = showing,
                    // Landing on the matching page of the key is the useful
                    // guess, and it is only a guess: a key is often shorter
                    // than its paper, so it is clamped rather than trusted.
                    currentPage = it.currentPage.coerceIn(
                        0,
                        ((if (showing) it.answerKeyPageCount else it.pageCount) - 1)
                            .coerceAtLeast(0),
                    ),
                    // Nothing can be written on somebody else's answer sheet.
                    tool = if (showing) ExamTool.VIEW else it.tool,
                    zoom = 1f,
                )
            }
        }
    }

    // ---- Tools ------------------------------------------------------------

    fun onToolChosen(tool: ExamTool) {
        _state.update { it.copy(tool = tool) }
        refreshTickBoxes()
    }

    fun onInkColorChosen(argb: Int) {
        _state.update { it.copy(inkColor = argb) }
    }

    fun onHighlightColorChosen(argb: Int) {
        _state.update { it.copy(highlightColor = argb) }
    }

    fun onStrokeWidthChosen(width: Float) {
        _state.update { it.copy(strokeWidth = width) }
    }

    // ---- Writing ----------------------------------------------------------

    /**
     * Commit a finished stroke.
     *
     * Called as the finger lifts, not batched and not saved on exit. The reader
     * asked for work that cannot be lost, and the only version of that which
     * survives Android killing a backgrounded app mid-sentence is a row per
     * stroke, written as it is made.
     */
    fun onStrokeFinished(points: List<MarkGeometry.Point>) = viewModelScope.launch {
        val current = _state.value
        if (points.size < 2 || !current.canWrite) return@launch
        val bounds = MarkGeometry.boundsOf(points)
        val isHighlight = current.tool == ExamTool.HIGHLIGHTER
        repository.addMark(
            ExamMarkEntity(
                attemptId = attemptId,
                pageIndex = current.currentPage,
                kind = (if (isHighlight) MarkKind.HIGHLIGHT else MarkKind.INK).name,
                x = bounds.left,
                y = bounds.top,
                width = bounds.right - bounds.left,
                height = bounds.bottom - bounds.top,
                colorArgb = if (isHighlight) current.highlightColor else current.inkColor,
                size = if (isHighlight) StrokeWidths.HIGHLIGHTER else current.strokeWidth,
                points = MarkGeometry.encode(points),
            ),
        )
    }

    fun onTextPlaced(x: Float, y: Float, text: String) = viewModelScope.launch {
        val current = _state.value
        val trimmed = text.trim()
        if (trimmed.isEmpty() || !current.canWrite) return@launch
        repository.addMark(
            ExamMarkEntity(
                attemptId = attemptId,
                pageIndex = current.currentPage,
                kind = MarkKind.TEXT.name,
                x = x,
                y = y,
                // Filled in by the canvas once the text has been measured; the
                // box is only used for hit-testing, so an estimate is enough.
                width = trimmed.length * TEXT_SIZE_FRACTION * 0.55f,
                height = TEXT_SIZE_FRACTION * 1.3f,
                colorArgb = current.inkColor,
                size = TEXT_SIZE_FRACTION,
                text = trimmed,
            ),
        )
    }

    fun onTextEdited(mark: ExamMarkEntity, text: String) = viewModelScope.launch {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            repository.deleteMark(mark.id)
        } else {
            repository.updateMark(
                mark.copy(
                    text = trimmed,
                    width = trimmed.length * TEXT_SIZE_FRACTION * 0.55f,
                ),
            )
        }
    }

    /**
     * Tick where the reader pressed, or clear the tick already there.
     *
     * One gesture for both, because a tick box has two states and pressing it is
     * how a person changes which one it is in. A separate "untick" tool would be
     * a tool for undoing a tap.
     */
    fun onTickAt(x: Float, y: Float, existing: ExamMarkEntity?) = viewModelScope.launch {
        val current = _state.value
        if (!current.canWrite) return@launch
        if (existing != null) {
            repository.deleteMark(existing.id)
            return@launch
        }
        repository.addMark(
            ExamMarkEntity(
                attemptId = attemptId,
                pageIndex = current.currentPage,
                kind = MarkKind.TICK.name,
                x = x - TICK_SIZE_FRACTION / 2,
                y = y - TICK_SIZE_FRACTION / 2,
                width = TICK_SIZE_FRACTION,
                height = TICK_SIZE_FRACTION,
                colorArgb = current.inkColor,
                size = TICK_SIZE_FRACTION,
                checked = true,
            ),
        )
    }

    fun onErase(mark: ExamMarkEntity) = viewModelScope.launch {
        repository.deleteMark(mark.id)
    }

    fun onUndo() = viewModelScope.launch {
        repository.undoLastMark(attemptId)
    }

    fun onClearPage() = viewModelScope.launch {
        repository.clearPage(attemptId, _state.value.currentPage)
    }

    fun onToggleFinished() = viewModelScope.launch {
        repository.setAttemptFinished(attemptId, !_state.value.isFinished)
    }

    // ---- Export -----------------------------------------------------------

    private val exporter = ExamExporter(application)

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    /** The name to offer the file picker, so the saved file is recognisable. */
    fun suggestedExportName(): String {
        val current = _state.value
        return exporter.suggestedName(
            current.exam?.title.orEmpty(),
            current.attempt?.label.orEmpty(),
        )
    }

    /**
     * Write this sitting out to wherever the reader chose.
     *
     * The marks are read once, whole, rather than page by page as the export
     * walks the paper. An export is a snapshot of the work as it stands, and
     * reading each page as it is reached would let a stroke made halfway
     * through land in a file that already claims to be finished.
     */
    fun onExportTo(target: Uri) = viewModelScope.launch {
        val current = _state.value
        val exam = current.exam ?: return@launch
        _state.update { it.copy(isExporting = true) }
        val marksByPage = repository.marks(attemptId).groupBy { it.pageIndex }
        val written = exporter.export(
            paperFile = repository.fileFor(exam.fileName),
            pageCount = current.pageCount,
            marksByPage = marksByPage,
            target = target,
        )
        _state.update { it.copy(isExporting = false) }
        _message.value = UiText.of(
            if (written) R.string.exam_export_done else R.string.exam_export_failed,
        )
    }

    fun onMessageShown() {
        _message.value = null
    }

    override fun onCleared() {
        paper?.close()
        paper = null
        answerKey?.close()
        answerKey = null
        pageCache.evictAll()
    }

    companion object {
        /**
         * How wide a page is rendered for the tick-box scan.
         *
         * Smaller than the page is drawn at. The scan is looking for shapes a
         * few millimetres across, which survives being sampled down, and a
         * quarter of the pixels is a quarter of the work on a job that runs
         * every time the reader turns a page with the tick tool in hand.
         */
        private const val DETECT_WIDTH_PX = 700

        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 5f

        /**
         * A budget for rendered pages, as a share of the heap.
         *
         * Smaller than the reader's share: the workspace holds one document at
         * a time and rarely more than a page or two on screen, and the room
         * left over is what keeps a long stroke from stuttering.
         */
        private fun cacheBudgetKb(): Int {
            val heapKb = Runtime.getRuntime().maxMemory() / 1024
            return (heapKb / 10).coerceIn(16L * 1024, Int.MAX_VALUE.toLong()).toInt()
        }

        fun factory(attemptId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LlegeixApp
                ExamWorkspaceViewModel(app, app.examRepository, attemptId)
            }
        }
    }
}
