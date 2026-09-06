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
import com.david.llegeix.data.db.entity.ExamPartEntity
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Where one global page number falls: which document, and which page of it.
 *
 * The reader sees a paper of forty pages; the app sees three PDFs of twelve,
 * eighteen and ten. This is the whole of the translation between those two, and
 * it is a value rather than a calculation scattered about, because getting it
 * wrong puts answers on the wrong page of the wrong document.
 */
data class PageAddress(val part: ExamPartEntity, val pageInPart: Int)

/**
 * The documents of a paper, laid end to end.
 *
 * Built once per change to the parts list rather than walked on every page
 * turn: the offsets are a running sum, and a pager asks for them several times
 * a frame while it is settling.
 */
class Pagination(val parts: List<ExamPartEntity>) {

    private val starts: List<Int> = buildList {
        var running = 0
        parts.forEach { add(running); running += it.pageCount }
    }

    val pageCount: Int = parts.sumOf { it.pageCount }

    /** Where a global page number lands, or null when it is off the end. */
    fun addressOf(page: Int): PageAddress? {
        if (page < 0) return null
        for (index in parts.indices.reversed()) {
            if (page >= starts[index]) {
                val within = page - starts[index]
                return if (within < parts[index].pageCount) {
                    PageAddress(parts[index], within)
                } else {
                    null
                }
            }
        }
        return null
    }

    /** The global page number a document starts at, for jumping to one. */
    fun startOf(partId: Long): Int {
        val index = parts.indexOfFirst { it.id == partId }
        return if (index < 0) 0 else starts[index]
    }

    companion object {
        val Empty = Pagination(emptyList())
    }
}

/** Everything the workspace needs that is not a page image or a mark. */
data class WorkspaceState(
    val exam: ExamEntity? = null,
    val attempt: ExamAttemptEntity? = null,
    /** The documents this paper is made of, in order. */
    val pagination: Pagination = Pagination.Empty,
    val isOpening: Boolean = true,
    val error: Boolean = false,
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

    /** Pages across every document of the paper. */
    val pageCount: Int get() = pagination.pageCount

    /** What the pager is showing: the paper, or the key. */
    val visiblePageCount: Int
        get() = if (showAnswerKey) answerKeyPageCount else pageCount

    /** Which document the current page falls in, and where in it. */
    val here: PageAddress? get() = pagination.addressOf(currentPage)

    /**
     * Which document the page belongs to, when the paper is made of several.
     *
     * Null for the ordinary single-file paper, where naming the document would
     * be repeating the exam's own title under every page.
     */
    val partOrdinal: Int?
        get() = here?.let { address ->
            if (pagination.parts.size <= 1) {
                null
            } else {
                pagination.parts.indexOfFirst { it.id == address.part.id } + 1
            }
        }

    val partTotal: Int get() = pagination.parts.size

    val partName: String? get() = here?.part?.sourceName

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

    /**
     * One open renderer per document, opened as its pages are first asked for.
     *
     * A paper of four documents is four PDFium handles, and opening all of them
     * up front would delay the first page for the sake of pages the reader may
     * never reach. Keyed by the part's id rather than its position, so
     * reordering or removing a document cannot hand back the wrong file.
     */
    private val papers = HashMap<Long, PdfPageRenderer>()
    private val openLock = Mutex()
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
        .map { current -> if (current.showAnswerKey) null else current.here }
        .distinctUntilChanged()
        .flatMapLatest { address ->
            // Null covers both "not loaded yet" and the answer sheet, which is
            // somebody else's document and gets nothing drawn on it.
            if (address == null) {
                flowOf(emptyList())
            } else {
                repository.observeMarks(attemptId, address.part.id, address.pageInPart)
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

        val parts = repository.parts(exam.id)
        if (parts.isEmpty()) {
            _state.update { it.copy(isOpening = false, error = true) }
            return@launch
        }
        val pagination = Pagination(parts)

        val resume = attempt.lastPage.coerceIn(0, (pagination.pageCount - 1).coerceAtLeast(0))
        _state.update {
            it.copy(
                exam = exam,
                attempt = attempt,
                pagination = pagination,
                isOpening = false,
                currentPage = resume,
                initialPage = resume,
                hasAnswerKey = exam.answerKeyFileName != null,
                answerKeyPageCount = exam.answerKeyPageCount ?: 0,
            )
        }
        repository.recordAttemptOpened(attemptId, resume)

        // Watched rather than read once: a document added or removed from the
        // paper while a sitting is open changes how long it is.
        launch {
            repository.observeParts(exam.id).collect { current ->
                if (current.isEmpty()) return@collect
                val updated = Pagination(current)
                _state.update { state ->
                    // A document removed while the sitting is open makes the
                    // paper shorter, and the reader may be standing past the new
                    // end of it. Left alone, every lookup from here answers null
                    // and the workspace goes quietly inert — a page that will not
                    // draw and a pen that will not write, with nothing said.
                    state.copy(
                        pagination = updated,
                        currentPage = state.currentPage
                            .coerceIn(0, (updated.pageCount - 1).coerceAtLeast(0)),
                    )
                }
            }
        }

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
        val current = _state.value
        if (current.showAnswerKey) {
            val renderer = ensureAnswerKey() ?: return null
            return render(renderer, "k", index, index, widthPx)
        }
        val address = current.pagination.addressOf(index) ?: return null
        val renderer = rendererFor(address.part) ?: return null
        // Keyed by the part, not by the global page: the same global number
        // means a different page the moment a document is added ahead of it.
        return render(renderer, "p${address.part.id}", address.pageInPart, index, widthPx)
    }

    private suspend fun render(
        renderer: PdfPageRenderer,
        prefix: String,
        pageInDocument: Int,
        globalPage: Int,
        widthPx: Int,
    ): Bitmap? {
        val key = "$prefix#$pageInDocument@$widthPx"
        pageCache.get(key)?.let { return it }
        // crop = false, always. See the class comment: a cropped bitmap is not
        // the page the marks were positioned against.
        return runCatchingCancellable { renderer.renderPage(pageInDocument, widthPx, crop = false) }
            .onSuccess { pageCache.put(key, it) }
            .getOrNull()
    }

    /**
     * The open renderer for one document, opening it if this is its first page.
     *
     * Under a lock because the pager asks for two or three pages at once while
     * it settles, and without one a document straddling a page boundary would
     * be opened twice and one of the two handles leaked.
     */
    private suspend fun rendererFor(part: ExamPartEntity): PdfPageRenderer? =
        openLock.withLock {
            papers[part.id]?.let { return it }
            val opened = runCatchingCancellable {
                PdfiumPageRenderer.open(application, repository.fileFor(part.fileName).toUri())
            }.getOrNull()
            if (opened != null) papers[part.id] = opened
            opened
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
        val here = current.here ?: return@launch
        if (points.size < 2 || !current.canWrite) return@launch
        val bounds = MarkGeometry.boundsOf(points)
        val isHighlight = current.tool == ExamTool.HIGHLIGHTER
        repository.addMark(
            ExamMarkEntity(
                attemptId = attemptId,
                partId = here.part.id,
                pageIndex = here.pageInPart,
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
        val here = current.here ?: return@launch
        val trimmed = text.trim()
        if (trimmed.isEmpty() || !current.canWrite) return@launch
        repository.addMark(
            ExamMarkEntity(
                attemptId = attemptId,
                partId = here.part.id,
                pageIndex = here.pageInPart,
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
        val here = current.here ?: return@launch
        if (!current.canWrite) return@launch
        if (existing != null) {
            repository.deleteMark(existing.id)
            return@launch
        }
        repository.addMark(
            ExamMarkEntity(
                attemptId = attemptId,
                partId = here.part.id,
                pageIndex = here.pageInPart,
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
        val here = _state.value.here ?: return@launch
        repository.clearPage(attemptId, here.part.id, here.pageInPart)
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
        if (current.exam == null) return@launch
        _state.update { it.copy(isExporting = true) }
        // Grouped by document and page, which is how a mark is addressed. The
        // exporter walks the documents in the same order the reader does, so
        // the file comes out as one paper however many PDFs went into it.
        val marks = repository.marks(attemptId).groupBy { it.partId to it.pageIndex }
        val written = exporter.export(
            documents = current.pagination.parts.map { part ->
                ExamExporter.Document(
                    file = repository.fileFor(part.fileName),
                    pageCount = part.pageCount,
                    marksFor = { page -> marks[part.id to page].orEmpty() },
                )
            },
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
        papers.values.forEach { it.close() }
        papers.clear()
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
