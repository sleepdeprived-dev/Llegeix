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
import com.david.llegeix.pdf.PageInvert
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
    /**
     * Draws the page as light-on-dark, without touching the app's theme.
     *
     * The same inversion the reader offers, for the same reason and with one
     * difference: only the *page* is inverted, never the marks over it. An
     * inverted ink colour would mean the blue the reader is writing in and the
     * blue on the page were different blues, and the export — which is drawn
     * from the same marks on white paper — would then disagree with the screen.
     */
    val darkPage: Boolean = false,
    /** True while a page is being written out, so the button can say so. */
    val isExporting: Boolean = false,
    /** Whether there is an undone mark waiting to be put back. */
    val canRedo: Boolean = false,
    /**
     * The text box the reader has hold of, if any.
     *
     * A text box is an object rather than a dialog's output now: it is picked
     * up, moved, turned, resized and typed into on the page itself, so
     * something has to remember which one is in hand.
     */
    val selectedTextId: Long? = null,
    /** True when the selected box should open with the keyboard already up. */
    val editingText: Boolean = false,
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

    /** True when the page showing is one of the blank ones added for an essay. */
    val isOnNotes: Boolean get() = !showAnswerKey && here?.part?.isNotes == true

    /** Where the blank pages start, or null when the paper has none yet. */
    val notesStart: Int?
        get() = pagination.parts.firstOrNull { it.isNotes }
            ?.let { pagination.startOf(it.id) }

    /**
     * Whether the page is being written on as a document rather than marked up.
     *
     * The one place the text tool changes what it means. On a printed page a
     * typed answer goes in a box, because it is going into a gap somebody else
     * left and has to be put exactly there. A blank page has no gaps: the page
     * *is* the answer, so the whole writing area is one field and typing runs
     * across it and down, the way it does in a word processor.
     *
     * Held as a question about the state rather than as a mode the reader
     * switches: there is no combination of tool and page where the other
     * behaviour would be the right one.
     */
    val isDocument: Boolean get() = isOnNotes && tool == ExamTool.TEXT
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

    /**
     * A page, drawn — and darkened, if that is what the reader has asked for.
     *
     * The darkening happens here rather than as a colour filter at draw time,
     * and that is the whole of the fix for pictures coming out as negatives. A
     * `ColorFilter` is a matrix applied to every pixel without exception: it
     * cannot be told that this rectangle is a photograph. Working on the bitmap
     * can, so [PageInvert] looks at the page, finds the pictures, and inverts
     * around them.
     *
     * The two versions are cached separately, the dark one derived from the
     * light one, so flipping the switch on a page already drawn costs one pass
     * over its pixels and flipping back costs nothing at all.
     */
    private suspend fun render(
        renderer: PdfPageRenderer,
        prefix: String,
        pageInDocument: Int,
        globalPage: Int,
        widthPx: Int,
    ): Bitmap? {
        val dark = _state.value.darkPage
        val plainKey = "$prefix#$pageInDocument@$widthPx"
        val key = if (dark) "$plainKey!dark" else plainKey
        pageCache.get(key)?.let { return it }

        val plain = pageCache.get(plainKey)
            // crop = false, always. See the class comment: a cropped bitmap is
            // not the page the marks were positioned against.
            ?: runCatchingCancellable {
                renderer.renderPage(pageInDocument, widthPx, crop = false)
            }.getOrNull()?.also { pageCache.put(plainKey, it) }
            ?: return null

        if (!dark) return plain
        // Off the main thread: it is a full pass over a page-sized array, which
        // is milliseconds rather than microseconds.
        val inverted = withContext(Dispatchers.Default) {
            runCatchingCancellable { PageInvert.invertKeepingPictures(plain) }.getOrNull()
        } ?: return plain
        pageCache.put(key, inverted)
        return inverted
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
        // Whatever was in hand belonged to the page that has just left.
        _state.update { current ->
            val moved = current.copy(
                currentPage = index,
                selectedTextId = null,
                editingText = false,
            )
            // Turning onto blank paper puts the text tool in hand, and turning
            // off it puts the view tool back. Blank paper is for writing on —
            // arriving there with the pen selected and having to find the
            // toolbar first is exactly the friction the blank pages exist to
            // remove — and carrying the text tool back onto a printed page
            // would mean the next tap on a question planted a text box in it.
            when {
                moved.isOnNotes && !current.isOnNotes -> moved.copy(tool = ExamTool.TEXT)
                !moved.isOnNotes && current.isOnNotes && moved.tool == ExamTool.TEXT ->
                    moved.copy(tool = ExamTool.VIEW)
                else -> moved
            }
        }
        // Swiping onto blank paper gets the same page-sized field as arriving
        // there by the button; only the keyboard is different, because a swipe
        // is not necessarily somebody about to write.
        if (_state.value.isOnNotes) ensureDocument()
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

    fun onToggleDarkPage() {
        _state.update { it.copy(darkPage = !it.darkPage) }
    }

    // ---- Text boxes --------------------------------------------------------

    /**
     * Take hold of a text box, or let go of the one in hand.
     *
     * Selecting is separate from editing on purpose: a box you have selected
     * can be moved, turned and resized with the keyboard down, which is most of
     * what is done to a text box after it is written. Editing is one more tap.
     */
    fun onSelectText(id: Long?, editing: Boolean = false) {
        _state.update { it.copy(selectedTextId = id, editingText = editing && id != null) }
    }

    /**
     * Put the cursor in whatever is being written on.
     *
     * On a printed page that is the box in hand; on blank paper it is the page,
     * which has no selection because there is only ever one thing to type into.
     */
    fun onEditSelectedText() {
        _state.update {
            if (it.selectedTextId == null && !it.isDocument) it else it.copy(editingText = true)
        }
    }

    fun onFinishEditing() {
        _state.update { it.copy(editingText = false) }
    }

    /**
     * Put a new text box where the reader pressed, and open it for typing.
     *
     * On a printed page the box starts narrow, at the tap, because it is going
     * into a gap somebody else left. On one of the blank pages at the back it
     * starts as the whole writing area, because that page *is* the answer —
     * which is the difference between filling in a form and writing an essay,
     * and the reason the same tool can do both without a mode switch.
     */
    fun onCreateText(x: Float, y: Float) = viewModelScope.launch {
        val current = _state.value
        val here = current.here ?: return@launch
        if (!current.canWrite || current.isDocument) return@launch
        forgetRedo()

        // The tap is the box's left edge and its first line, unless that would
        // leave it too narrow to type in — see TextBoxGeometry, which is where
        // the arithmetic lives and where it is tested.
        val placed = TextBoxGeometry.placeAt(x)
        val id = repository.addMark(
            ExamMarkEntity(
                attemptId = attemptId,
                partId = here.part.id,
                pageIndex = here.pageInPart,
                kind = MarkKind.TEXT.name,
                x = placed.left,
                y = y.coerceIn(0f, 0.98f),
                width = placed.width,
                height = TEXT_SIZE_FRACTION * 1.4f,
                colorArgb = current.inkColor,
                size = TEXT_SIZE_FRACTION,
                text = "",
            ),
        )
        _state.update { it.copy(selectedTextId = id, editingText = true) }
    }

    /**
     * Save what has been typed so far, without ending the edit.
     *
     * Called from the editor a moment after typing stops, and it is the fix for
     * the worst bug this release had: the text was written only when the editor
     * left the composition, and the most common way for that to happen is the
     * reader leaving the workspace — at which point [onCleared] has cancelled
     * the scope the write was launched in, so it never ran and the answer was
     * gone. Marks made with the pen have always been saved as the finger lifts;
     * this makes typing keep the same promise.
     */
    fun onTextChanged(mark: ExamMarkEntity, text: String, heightFraction: Float) =
        viewModelScope.launch {
            if (mark.text == text) return@launch
            repository.updateMark(mark.copy(text = text, height = heightFraction))
        }

    /**
     * Commit what was typed, and the height it turned out to need.
     *
     * The height comes from the canvas rather than being guessed here, because
     * the canvas has the one piece of information that decides it — the size
     * the page is actually drawn at — and the same measurement draws the frame
     * round the box. An empty box is deleted rather than kept: a text box with
     * nothing in it is invisible on the page and impossible to get rid of.
     */
    fun onTextEdited(mark: ExamMarkEntity, text: String, heightFraction: Float) =
        viewModelScope.launch {
            if (text.isBlank()) {
                repository.deleteMark(mark.id)
                // Only if this is still the box in hand. Tapping bare paper
                // while an empty box is open commits the old one and selects
                // the new one in the same frame, and clearing the selection
                // unconditionally here would throw away the new box's
                // selection a moment after it was made — leaving a box on the
                // page with no frame round it and no keyboard.
                _state.update {
                    if (it.selectedTextId == mark.id) {
                        it.copy(selectedTextId = null, editingText = false)
                    } else {
                        it
                    }
                }
                return@launch
            }
            repository.updateMark(mark.copy(text = text, height = heightFraction))
        }

    /** Moved by dragging it. Kept on the page, so it cannot be lost off an edge. */
    fun onTextMoved(mark: ExamMarkEntity, x: Float, y: Float) = viewModelScope.launch {
        repository.updateMark(
            mark.copy(
                x = x.coerceIn(-mark.width / 2f, 1f - mark.width / 2f),
                y = y.coerceIn(-mark.height / 2f, 1f - mark.height / 2f),
            ),
        )
    }

    /**
     * Scaled by dragging the corner.
     *
     * The type size and the box width move together, which is what "make it
     * bigger" means about a piece of writing. Changing the width alone would be
     * re-wrapping, and there is a much better control for that: the words.
     */
    fun onTextScaled(mark: ExamMarkEntity, scale: Float) = viewModelScope.launch {
        val factor = scale.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
        repository.updateMark(
            mark.copy(
                size = (mark.size * factor).coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE),
                width = (mark.width * factor).coerceIn(MIN_TEXT_WIDTH, 1f),
            ),
        )
    }

    /** Turned by dragging the handle above it. */
    fun onTextRotated(mark: ExamMarkEntity, degrees: Float) = viewModelScope.launch {
        repository.updateMark(mark.copy(rotation = degrees))
    }

    fun onDeleteText(mark: ExamMarkEntity) = viewModelScope.launch {
        repository.deleteMark(mark.id)
        _state.update { it.copy(selectedTextId = null, editingText = false) }
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
        _state.update { it.copy(tool = tool, selectedTextId = null, editingText = false) }
        refreshTickBoxes()
        // Picking the text tool on blank paper is the reader saying they want
        // to write on it, so the page they are going to write on is made ready
        // before they touch it rather than on their first tap.
        if (tool == ExamTool.TEXT) ensureDocument()
    }

    /**
     * Make sure a page of blank paper has something to type into.
     *
     * One text mark per blank page, spanning the writing area. It is created
     * before the keyboard rather than on the first keystroke because the editor
     * needs a row to save into, and a field that quietly discards what was typed
     * into it until a row appears is the bug this whole release is about.
     *
     * Nothing happens on a printed page: there, a text box is placed where the
     * finger goes, and one conjured up in advance would be a box the reader has
     * to find and delete.
     */
    fun ensureDocument() = viewModelScope.launch {
        val current = _state.value
        if (!current.isOnNotes || !current.canWrite) return@launch
        val here = current.here ?: return@launch
        val existing = repository.marks(attemptId).firstOrNull {
            it.partId == here.part.id &&
                it.pageIndex == here.pageInPart &&
                it.kind == MarkKind.TEXT.name
        }
        if (existing != null) {
            // Widened if it is not already the writing area. A page written on
            // in 4.0.0 got whatever width the box tool gave it, which on blank
            // paper could be a narrow column — and the essay in it would go on
            // being drawn in that column for ever. The words are untouched;
            // only the shape they flow in changes, and it changes towards the
            // page rather than away from it.
            if (existing.width < 1f - 2 * NOTES_MARGIN - 0.01f) {
                repository.updateMark(
                    existing.copy(
                        x = NOTES_MARGIN,
                        y = minOf(existing.y, NOTES_MARGIN),
                        width = 1f - 2 * NOTES_MARGIN,
                        size = maxOf(existing.size, NOTES_TEXT_SIZE),
                    ),
                )
            }
            return@launch
        }
        repository.addMark(
            ExamMarkEntity(
                attemptId = attemptId,
                partId = here.part.id,
                pageIndex = here.pageInPart,
                kind = MarkKind.TEXT.name,
                x = NOTES_MARGIN,
                y = NOTES_MARGIN,
                width = 1f - 2 * NOTES_MARGIN,
                height = 1f - 2 * NOTES_MARGIN,
                colorArgb = current.inkColor,
                size = NOTES_TEXT_SIZE,
                text = "",
            ),
        )
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
        forgetRedo()
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
        forgetRedo()
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
        forgetRedo()
    }

    /**
     * The marks taken back by undo, newest last, waiting to be put back.
     *
     * In memory and not in the database, and that is the right place for it: a
     * redo stack is about what happened in this sitting of this screen, not
     * about the reader's work. Coming back to a paper in March with a redo
     * queue from January would be offering to reinstate a stroke nobody
     * remembers making.
     *
     * Anything else that changes the page — a new stroke, an erase, clearing
     * the page — empties it, which is what every editor does and is the only
     * behaviour that cannot surprise: a redo after further drawing would drop a
     * stroke into a page that has moved on without it.
     */
    private val undone = ArrayDeque<ExamMarkEntity>()

    private fun forgetRedo() {
        if (undone.isEmpty()) return
        undone.clear()
        _state.update { it.copy(canRedo = false) }
    }

    fun onUndo() = viewModelScope.launch {
        val removed = repository.undoLastMark(attemptId) ?: return@launch
        undone.addLast(removed)
        _state.update { it.copy(canRedo = true) }
    }

    /**
     * Put back the last thing undo took away.
     *
     * Written as a new row rather than restored with its old id: the id is the
     * database's and the row it named is gone. Everything that decides where
     * the mark is drawn — the document, the page, the fractions, the colour —
     * is carried over exactly, so what comes back is the stroke that went, on
     * the page it was on, whichever page the reader is looking at now.
     */
    fun onRedo() = viewModelScope.launch {
        val mark = undone.removeLastOrNull() ?: return@launch
        repository.addMark(mark.copy(id = 0))
        _state.update { it.copy(canRedo = undone.isNotEmpty()) }
    }

    fun onClearPage() = viewModelScope.launch {
        val here = _state.value.here ?: return@launch
        repository.clearPage(attemptId, here.part.id, here.pageInPart)
        forgetRedo()
    }

    // ---- Blank paper -------------------------------------------------------

    /**
     * Where the workspace should jump to next, or null when it should stay put.
     *
     * A page number rather than a call into the pager, because the pager
     * belongs to the composable and the ViewModel has no business holding it.
     * The screen consumes this and reports back through [onJumpHandled].
     */
    private val _jumpTo = MutableStateFlow<Int?>(null)
    val jumpTo: StateFlow<Int?> = _jumpTo.asStateFlow()

    fun onJumpHandled() {
        _jumpTo.value = null
    }

    /** Where the reader was on the printed paper before going to the blank pages. */
    private var pageOnPaper = 0

    /**
     * Add a blank page to write an essay on, and go to it.
     *
     * Adding and arriving are one action on purpose. The reader asked for this
     * not to be "100000 separated menus and buttons": a button that silently
     * appended a page somewhere behind them, leaving them to find it, would be
     * exactly the thing they were complaining about. The paper simply gets
     * longer and the workspace turns to the new sheet, the way a booklet does.
     */
    fun onAddNotesPage(name: String) = viewModelScope.launch {
        val examId = _state.value.exam?.id ?: return@launch
        if (!_state.value.isOnNotes) pageOnPaper = _state.value.currentPage
        val added = repository.addNotesPage(examId, name)
        _message.value = UiText.of(
            if (added == null) R.string.exam_page_add_failed else R.string.exam_page_added,
        )
        if (added == null) return@launch
        // The blank paper is one PDF that is rewritten a page longer each time,
        // so the renderer opened on it is holding the *shorter* file and would
        // answer "no such page" for the one just added. Letting it go here — and
        // not merely evicting the bitmaps — is what makes the new sheet appear.
        // The cached pages are left alone: page three of a blank document is the
        // same blank page whether the file has three pages or four.
        openLock.withLock { papers.remove(added.partId)?.close() }
        _jumpTo.value = added.page
    }

    /**
     * Land on a page of blank paper ready to type.
     *
     * Called by the screen once the pager has actually arrived, because until
     * then there is no page to put a cursor on. A reader who pressed "add a
     * blank page" has said what they want to do with it in the act of asking
     * for it, so the keyboard comes up without a further tap — which is the
     * whole of what "let me type straight away" means.
     */
    fun onArrivedOnNotes() = viewModelScope.launch {
        if (!_state.value.isOnNotes) return@launch
        ensureDocument()
        _state.update { it.copy(editingText = true) }
    }

    /**
     * Go to the blank pages, or back to where the paper was left.
     *
     * One button rather than two, because it is one thought — *the other part
     * of this booklet* — and because the reader is always on exactly one side
     * of the join, so the button is never ambiguous about which way it goes.
     * Coming back lands on the page the paper was left at rather than at page
     * one: an essay is written *about* a question, and going back to look at it
     * is the whole reason this is inside the exam rather than beside it.
     */
    fun onToggleNotes(name: String) {
        val current = _state.value
        if (current.isOnNotes) {
            _jumpTo.value = pageOnPaper.coerceIn(0, (current.pageCount - 1).coerceAtLeast(0))
            return
        }
        pageOnPaper = current.currentPage
        val start = current.notesStart
        if (start == null) {
            // No blank paper yet, so the button that goes to it makes it. The
            // alternative is a button that goes nowhere until some other button
            // has been found first.
            onAddNotesPage(name)
        } else {
            _jumpTo.value = start
        }
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

        /** Narrow enough for one word in a gap, wide enough to still be grabbable. */
        private const val MIN_TEXT_WIDTH = 0.06f

        /**
         * The margin the blank pages leave, as a share of the page.
         *
         * The same on all four sides and roughly what a word processor uses. A
         * blank page written edge to edge does not read as a page.
         */
        private const val NOTES_MARGIN = 0.07f

        /**
         * How big the type is on a blank page, as a share of the page's width.
         *
         * Larger than an answer squeezed into a printed gap, because nothing is
         * squeezing it: this is somebody writing an essay on their own paper,
         * and it should read like a document rather than like an annotation.
         */
        private const val NOTES_TEXT_SIZE = 0.026f

        private const val MIN_TEXT_SIZE = 0.008f
        private const val MAX_TEXT_SIZE = 0.12f
        private const val MIN_TEXT_SCALE = 0.2f
        private const val MAX_TEXT_SCALE = 5f

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
