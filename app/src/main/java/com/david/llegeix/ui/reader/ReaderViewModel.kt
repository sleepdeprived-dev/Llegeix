package com.david.llegeix.ui.reader

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
import com.david.llegeix.data.db.entity.WordBookmarkEntity
import com.david.llegeix.data.settings.SearchHistoryRepository
import com.david.llegeix.data.settings.SearchScope
import com.david.llegeix.data.settings.SettingsRepository
import com.david.llegeix.data.settings.TranslationTarget
import com.david.llegeix.data.source.LibraryDataRepository
import android.graphics.RectF
import com.david.llegeix.lang.CatalanIpa
import com.david.llegeix.lang.CatalanWordBank
import com.david.llegeix.pdf.PdfMatch
import com.david.llegeix.pdf.PdfiumPageRenderer
import com.david.llegeix.pdf.PageOcr
import com.david.llegeix.pdf.PdfPageRenderer
import com.david.llegeix.pdf.PdfSelection
import com.david.llegeix.translate.WordTranslator
import com.david.llegeix.R
import com.david.llegeix.ui.common.DictionaryState
import com.david.llegeix.ui.common.DictionaryStatus
import com.david.llegeix.ui.common.HighlightColors
import com.david.llegeix.ui.common.UiText
import com.david.llegeix.util.pdfTitle
import com.david.llegeix.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How far a tap-and-hold lookup has got. */
enum class LookupStatus { LOOKING_UP, DOWNLOADING_MODEL, READY, FAILED }

/**
 * What is under the finger while a selection is still being dragged.
 *
 * Held apart from [WordLookup] so the page can highlight the growing selection
 * without the sheet opening over it: the sheet is what made picking a phrase
 * so awkward, since it appeared on the first word and covered everything the
 * reader was still trying to reach.
 */
data class SelectionPreview(
    val pageIndex: Int,
    val boundsPx: List<RectF>,
)

/** One word of a selected phrase, glossed on its own. */
data class WordGloss(
    val word: String,
    val ipa: String,
    val translation: String? = null,
)

/**
 * What the reader pressed and held, plus whatever is known about it so far.
 *
 * Covers one word and a dragged phrase alike: a phrase is the same thing with
 * more than one word in it, so [words] carries the per-word breakdown that makes
 * a phrase readable rather than just translated.
 *
 * [boundsPx] is in the coordinate space of the rendered page bitmap.
 */
data class WordLookup(
    val text: String,
    val pageIndex: Int,
    val boundsPx: List<RectF>,
    /** The line it came from, which is what makes the sense recoverable. */
    val context: String = "",
    val lineNumber: Int = 1,
    val ipa: String = "",
    /** True when the stressed vowel's aperture had to be guessed. */
    val isIpaApproximate: Boolean = false,
    val status: LookupStatus = LookupStatus.LOOKING_UP,
    val translation: String? = null,
    /** Per-word gloss, only worth showing for a phrase. */
    val words: List<WordGloss> = emptyList(),
    /** The line translated, so the phrase can be read in place. */
    val contextTranslation: String? = null,
    val isSaved: Boolean = false,
    val error: UiText? = null,
    /** True once a Wi-Fi-only download has failed, so retrying is worth offering. */
    val canRetryOnAnyNetwork: Boolean = false,
    /** Definitions, synonyms and antonyms, once the reader has asked. */
    val dictionary: DictionaryState = DictionaryState(),
) {
    val isPhrase: Boolean get() = words.size > 1
}

/** The find-in-document bar and whatever it has turned up so far. */
data class SearchState(
    val isOpen: Boolean = false,
    val query: String = "",
    val matches: List<PdfMatch> = emptyList(),
    val currentIndex: Int = 0,
    val isSearching: Boolean = false,
) {
    val current: PdfMatch? get() = matches.getOrNull(currentIndex)

    /** True once a completed search has come back with nothing. */
    val isEmptyResult: Boolean
        get() = !isSearching && query.isNotBlank() && matches.isEmpty()
}

data class ReaderUiState(
    /** The real filename, as stored in the database. */
    val title: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    /** Page to open on, restored from the reading history. */
    val initialPage: Int = 0,
    val isOpening: Boolean = true,
    val error: UiText? = null,
    val bookmarkedPages: Set<Int> = emptySet(),
    /** The document's chosen highlight colour, or the app default. */
    val highlightColor: Int = HighlightColors.Default,
    val lookup: WordLookup? = null,
    /** Highlighted while the finger is still down, before the sheet opens. */
    val selectionPreview: SelectionPreview? = null,
    /** Shown briefly when a long press lands on a page with no text layer. */
    val lookupHint: UiText? = null,
    val search: SearchState = SearchState(),
    /** Renders the page light-on-dark. Persisted, so it survives reopening. */
    val invertPages: Boolean = false,
    /** Page magnification, driven by pinch, double tap, or the zoom button. */
    val zoom: Float = 1f,
    /** Named in the sheet's "Catalan → …" line. */
    val translationTarget: TranslationTarget = TranslationTarget.Default,
) {
    /** The filename trimmed for the app bar. */
    val displayTitle: String get() = pdfTitle(title)

    val isCurrentPageBookmarked: Boolean
        get() = currentPage in bookmarkedPages

    val isZoomed: Boolean get() = zoom > 1.01f
}

class ReaderViewModel(
    private val application: Application,
    private val uriString: String,
    private val libraryData: LibraryDataRepository,
    private val settings: SettingsRepository,
    private val searchHistory: SearchHistoryRepository,
    title: String,
    /** Page to open on, overriding the remembered position. */
    private val targetPage: Int? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState(title = title))
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var renderer: PdfPageRenderer? = null

    /** Reads the words off pages that have none of their own. */
    private val pageOcr = PageOcr()

    /** Which pages carry a text layer, so the answer is worked out once each. */
    private val textLayers = HashMap<Int, Boolean>()

    /**
     * Rebuilt when the target language changes: an ML Kit translator is bound to
     * its language pair at construction, so switching target means a new one.
     * The old one is closed, since each holds a native model handle.
     */
    private var translator = WordTranslator(settings.current.translationTarget.code)

    private var lookupJob: Job? = null

    private var previewJob: Job? = null

    private var searchJob: Job? = null

    /**
     * Keeps a few rendered pages around so swiping back to the previous page is
     * instant. Bitmaps are never recycled here — the pager may still be drawing
     * one as it is evicted, so eviction is left to the garbage collector.
     */
    private val pageCache = LruCache<String, Bitmap>(CACHE_SIZE)

    init {
        openDocument()
        observeStoredState()
        viewModelScope.launch {
            settings.settings.collect { current ->
                _uiState.update {
                    it.copy(
                        invertPages = current.invertPages,
                        translationTarget = current.translationTarget,
                    )
                }
                if (translator.targetLanguage != current.translationTarget.code) {
                    translator.close()
                    translator = WordTranslator(current.translationTarget.code)
                    // A sheet open at the moment of the switch should answer in
                    // the new language rather than keep the old answer.
                    _uiState.value.lookup?.let { open ->
                        updateLookup {
                            it.copy(
                                status = LookupStatus.LOOKING_UP,
                                translation = null,
                                contextTranslation = null,
                                error = null,
                                words = it.words.map { gloss -> gloss.copy(translation = null) },
                            )
                        }
                        lookupJob?.cancel()
                        lookupJob = viewModelScope.launch { translate(open.text) }
                    }
                }
            }
        }
    }

    // ---- Reading controls -------------------------------------------------

    fun onToggleInvertPages() {
        settings.setInvertPages(!_uiState.value.invertPages)
    }

    /**
     * Step through the zoom levels.
     *
     * A button as well as pinch: pinching accurately is fiddly, and the whole
     * point of this screen is that it should not be fiddly.
     */
    fun onCycleZoom() {
        val next = ZOOM_STEPS.firstOrNull { it > _uiState.value.zoom + 0.01f } ?: ZOOM_STEPS.first()
        _uiState.update { it.copy(zoom = next) }
    }

    fun onZoomChanged(zoom: Float) {
        _uiState.update { it.copy(zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)) }
    }

    fun onResetZoom() {
        _uiState.update { it.copy(zoom = 1f) }
    }

    // ---- Find in document -------------------------------------------------

    /** Words looked for lately, shared with the saved-words screen. */
    val recentSearches: StateFlow<List<String>> = searchHistory.history(SearchScope.WORDS)

    fun onForgetSearches() = searchHistory.forget(SearchScope.WORDS)

    fun onOpenSearch() {
        _uiState.update { it.copy(search = it.search.copy(isOpen = true)) }
    }

    fun onCloseSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(search = SearchState()) }
    }

    fun onSearchQueryChange(query: String) {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                search = it.search.copy(
                    query = query,
                    matches = emptyList(),
                    currentIndex = 0,
                    isSearching = query.isNotBlank(),
                ),
            )
        }
        if (query.isBlank()) return

        searchJob = viewModelScope.launch {
            // Typing a word produces a search per keystroke otherwise, and each
            // one sweeps the whole document.
            delay(SEARCH_DEBOUNCE_MS)
            val active = renderer ?: return@launch
            var jumped = false
            runCatchingCancellable {
                active.findMatches(query) { batch ->
                    _uiState.update { state ->
                        state.copy(search = state.search.copy(matches = state.search.matches + batch))
                    }
                    // Land on the first hit as soon as there is one, rather than
                    // waiting for the whole document to be swept.
                    if (!jumped) {
                        jumped = true
                        _pageJumps.emit(batch.first().pageIndex)
                    }
                }
            }
            _uiState.update { it.copy(search = it.search.copy(isSearching = false)) }
            // Kept only now, and only if it found something. The sweep is the
            // answer to "was that worth searching for", so there is no need to
            // guess at it with a timer the way the filtering fields have to.
            if (_uiState.value.search.matches.isNotEmpty()) {
                searchHistory.record(SearchScope.WORDS, query)
            }
        }
    }

    fun onNextMatch() = stepMatch(1)

    fun onPreviousMatch() = stepMatch(-1)

    private fun stepMatch(delta: Int) {
        val search = _uiState.value.search
        if (search.matches.isEmpty()) return
        val next = (search.currentIndex + delta).mod(search.matches.size)
        _uiState.update { it.copy(search = it.search.copy(currentIndex = next)) }
        viewModelScope.launch { _pageJumps.emit(search.matches[next].pageIndex) }
    }

    /** Where the search wants the pager to go. */
    private val _pageJumps = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val pageJumps: SharedFlow<Int> = _pageJumps.asSharedFlow()

    /** Highlight rectangles for the active match on [pageIndex], if any. */
    suspend fun matchHighlights(pageIndex: Int, widthPx: Int, heightPx: Int): List<RectF> {
        val active = renderer ?: return emptyList()
        val match = _uiState.value.search.current ?: return emptyList()
        if (match.pageIndex != pageIndex) return emptyList()
        return runCatchingCancellable {
            active.matchBoundsPx(match, widthPx, heightPx)
        }.getOrDefault(emptyList())
    }

    private fun openDocument() = viewModelScope.launch {
        libraryData.ensureDocument(uriString, _uiState.value.title)
        // Arriving from a bookmarked page overrides the remembered position;
        // otherwise pick up where reading left off.
        val resumePage = targetPage ?: libraryData.lastReadPage(uriString)

        runCatchingCancellable { PdfiumPageRenderer.open(application, uriString.toUri()) }
            .onSuccess { opened ->
                renderer = opened
                // A document can shrink between visits; never resume past its end.
                val safeResume = resumePage.coerceIn(0, (opened.pageCount - 1).coerceAtLeast(0))
                _uiState.update {
                    it.copy(
                        isOpening = false,
                        pageCount = opened.pageCount,
                        initialPage = safeResume,
                        currentPage = safeResume,
                    )
                }
                libraryData.recordView(uriString, _uiState.value.title, safeResume)
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isOpening = false,
                        error = UiText.ofMessageOr(
                            error.message,
                            R.string.reader_open_failed_message,
                        ),
                    )
                }
            }
    }

    private fun observeStoredState() {
        viewModelScope.launch {
            libraryData.observeBookmarks(uriString).collect { bookmarks ->
                _uiState.update { it.copy(bookmarkedPages = bookmarks.map { b -> b.pageIndex }.toSet()) }
            }
        }
        viewModelScope.launch {
            libraryData.observeDocument(uriString).collect { document ->
                _uiState.update {
                    it.copy(highlightColor = document?.highlightColor ?: HighlightColors.Default)
                }
            }
        }
    }

    /** Null when the page could not be rendered; the UI shows a placeholder. */
    suspend fun renderPage(index: Int, widthPx: Int): Bitmap? {
        val active = renderer ?: return null
        val key = "$index@$widthPx"
        pageCache.get(key)?.let { return it }

        return runCatchingCancellable { active.renderPage(index, widthPx) }
            .onSuccess { pageCache.put(key, it) }
            .getOrNull()
    }

    fun onPageChanged(index: Int) {
        if (index == _uiState.value.currentPage) return
        _uiState.update { it.copy(currentPage = index) }
        // Cheap upsert of a single row, so writing on every turn is fine and
        // means the resume position survives the app being killed outright.
        viewModelScope.launch {
            libraryData.recordView(uriString, _uiState.value.title, index)
        }
    }

    /**
     * Track a selection while the finger is still down.
     *
     * Highlights what is currently covered and nothing more. No translation is
     * started and no sheet is opened, so the reader can keep dragging until the
     * selection is the phrase they meant.
     */
    fun onSelectionPreview(
        pageIndex: Int,
        startXPx: Float,
        startYPx: Float,
        endXPx: Float,
        endYPx: Float,
        renderedWidthPx: Int,
        renderedHeightPx: Int,
    ) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val selection = resolveSelection(
                pageIndex, startXPx, startYPx, endXPx, endYPx,
                renderedWidthPx, renderedHeightPx,
                mayRead = false,
            ) ?: return@launch
            _uiState.update {
                it.copy(selectionPreview = SelectionPreview(pageIndex, selection.boundsPx))
            }
        }
    }

    /** Drop the highlight when a gesture is cancelled rather than finished. */
    fun onSelectionCancelled() {
        previewJob?.cancel()
        _uiState.update { it.copy(selectionPreview = null) }
    }

    /**
     * Finish a tap-and-hold: find the words, then translate them.
     *
     * Called when the finger lifts, which is the first moment the selection is
     * known to be final. Coordinates arrive in bitmap pixels, which is the
     * space the page was drawn in, so the UI never has to reason about PDF user
     * space.
     */
    fun onSelectionCommitted(
        pageIndex: Int,
        startXPx: Float,
        startYPx: Float,
        endXPx: Float,
        endYPx: Float,
        renderedWidthPx: Int,
        renderedHeightPx: Int,
    ) {
        previewJob?.cancel()
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            val selection = resolveSelection(
                pageIndex, startXPx, startYPx, endXPx, endYPx,
                renderedWidthPx, renderedHeightPx,
                mayRead = true,
            )

            if (selection == null) {
                // Two different silences, and they deserve different answers:
                // a page with words in it that were not where the finger went,
                // and a page that is a picture with nothing readable in it.
                //
                // Which one it is turns on whether words were found at all,
                // from either source — not on where they came from. A scan
                // that was read perfectly well and then pressed in the margin
                // is the first case, not the second.
                val hasWords = hasTextLayer(pageIndex) ||
                    pageOcr.cached(pageIndex, renderedWidthPx)?.isEmpty == false
                val message = if (hasWords) {
                    R.string.lookup_no_word_there
                } else {
                    R.string.lookup_page_unreadable
                }
                _uiState.update {
                    it.copy(
                        lookup = null,
                        selectionPreview = null,
                        lookupHint = UiText.of(message),
                    )
                }
                return@launch
            }

            val pronunciation = CatalanIpa.transcribe(selection.text)
            val pieces = selection.text.split(WORD_SPLIT).filter { it.isNotBlank() }
            val saved = libraryData.findWordBookmark(selection.text, uriString, pageIndex) != null

            _uiState.update {
                it.copy(
                    lookupHint = null,
                    selectionPreview = null,
                    lookup = WordLookup(
                        text = selection.text,
                        pageIndex = pageIndex,
                        boundsPx = selection.boundsPx,
                        context = selection.lineText,
                        lineNumber = selection.lineNumber,
                        ipa = pronunciation.ipa,
                        isIpaApproximate = pronunciation.isApproximate,
                        words = pieces.map { piece ->
                            WordGloss(piece, CatalanIpa.transcribe(piece).ipa)
                        },
                        isSaved = saved,
                        status = LookupStatus.LOOKING_UP,
                    ),
                )
            }
            translate(selection.text)
        }
    }

    private suspend fun resolveSelection(
        pageIndex: Int,
        startXPx: Float,
        startYPx: Float,
        endXPx: Float,
        endYPx: Float,
        renderedWidthPx: Int,
        renderedHeightPx: Int,
        /**
         * Whether a page with no text of its own may be read now.
         *
         * False while the finger is still down. Recognition takes a moment and
         * the preview fires on every movement of the drag, so asking there
         * would start a job the next movement cancels, over and over, and the
         * page would never actually get read. The commit does the reading; the
         * preview uses it once it exists, which from the second press on a page
         * is immediately.
         */
        mayRead: Boolean,
    ): PdfSelection? {
        val active = renderer ?: return null
        val fromPage = runCatchingCancellable {
            active.selectionBetween(
                pageIndex, startXPx, startYPx, endXPx, endYPx,
                renderedWidthPx, renderedHeightPx,
            )
        }.getOrNull()
        if (fromPage != null) return fromPage
        // A page with text of its own that gave nothing back means the press
        // landed between words, and no amount of looking at the pixels will
        // change that.
        if (hasTextLayer(pageIndex)) return null

        val read = pageOcr.cached(pageIndex, renderedWidthPx)
            ?: if (mayRead) {
                val bitmap = renderPage(pageIndex, renderedWidthPx) ?: return null
                runCatchingCancellable { pageOcr.read(pageIndex, bitmap) }.getOrNull()
            } else {
                null
            }
            ?: return null

        return read.selectionBetween(
            startXPx, startYPx, endXPx, endYPx,
            tolerance = renderedWidthPx * OCR_TOUCH_TOLERANCE_FRACTION,
        )
    }

    /**
     * Whether a page has any text of its own, asked once per page.
     *
     * Cached because the preview asks on every movement of a drag, and each
     * answer costs opening the page and its text layer.
     */
    private suspend fun hasTextLayer(pageIndex: Int): Boolean {
        textLayers[pageIndex]?.let { return it }
        val active = renderer ?: return true
        val answer = runCatchingCancellable { active.hasTextLayer(pageIndex) }.getOrDefault(true)
        textLayers[pageIndex] = answer
        return answer
    }

    /**
     * Switch which language the sheet translates into, and redo the lookup.
     *
     * The setting is the same one Configuració writes, so a switch made here
     * while reading is the switch the app keeps.
     */
    fun onToggleTranslationTarget() {
        val current = _uiState.value.translationTarget
        val next = TranslationTarget.entries
            .getOrNull(current.ordinal + 1) ?: TranslationTarget.entries.first()
        settings.setTranslationTarget(next)
    }

    /**
     * Load what the references have for what is selected.
     *
     * A phrase is looked up word by word, keeping only the words the dictionary
     * actually knows, so a selected sentence does not produce a wall of lists
     * with empty gaps in it.
     */
    fun onShowDictionary() {
        val lookup = _uiState.value.lookup ?: return
        if (lookup.dictionary.status != DictionaryStatus.CLOSED) return
        updateLookup { it.copy(dictionary = DictionaryState(status = DictionaryStatus.LOADING)) }
        viewModelScope.launch {
            val words = if (lookup.isPhrase) {
                lookup.words.map { it.word }
            } else {
                listOf(lookup.text)
            }
            val found = withContext(Dispatchers.IO) {
                val dictionary = CatalanWordBank.get(application)
                words.filter { CatalanWordBank.isWorthLookingUp(it) }
                    .take(MAX_THESAURUS_WORDS)
                    .mapNotNull { dictionary.lookup(it) }
                    .filterNot { it.isEmpty }
            }
            updateLookup {
                it.copy(dictionary = DictionaryState(DictionaryStatus.READY, found))
            }
        }
    }

    /**
     * Save or unsave the selection.
     *
     * Everything that makes it learnable is copied in at save time — the
     * translation, the pronunciation, the line, and where it was found — so the
     * entry stands on its own even if the PDF is later gone.
     */
    fun onToggleWordBookmark() = viewModelScope.launch {
        val lookup = _uiState.value.lookup ?: return@launch
        val saved = libraryData.toggleWordBookmark(
            WordBookmarkEntity(
                word = lookup.text,
                translation = lookup.translation,
                ipa = lookup.ipa,
                context = lookup.context,
                documentUri = uriString,
                displayName = _uiState.value.title,
                pageIndex = lookup.pageIndex,
                lineNumber = lookup.lineNumber,
            ),
        )
        updateLookup { it.copy(isSaved = saved) }
    }

    /**
     * Retry a failed lookup without insisting on Wi-Fi.
     *
     * The model download defaults to unmetered, which is the polite choice for a
     * download this size, but it leaves anyone without Wi-Fi unable to use the
     * feature at all — so the failure state offers a way through.
     */
    fun onRetryOnAnyNetwork() {
        val word = _uiState.value.lookup?.text ?: return
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            updateLookup { it.copy(status = LookupStatus.LOOKING_UP, error = null) }
            translate(word, requireWifi = false)
        }
    }

    private suspend fun translate(word: String, requireWifi: Boolean = true) {
        if (!translator.isModelReady) {
            val downloaded = runCatchingCancellable {
                translator.ensureModel(requireWifi) {
                    updateLookup { it.copy(status = LookupStatus.DOWNLOADING_MODEL) }
                }
            }
            if (downloaded.isFailure) {
                updateLookup {
                    it.copy(
                        status = LookupStatus.FAILED,
                        canRetryOnAnyNetwork = requireWifi,
                        error = UiText.of(
                            if (requireWifi) {
                                R.string.lookup_model_wifi_failed
                            } else {
                                R.string.lookup_model_failed
                            },
                        ),
                    )
                }
                return
            }
        }

        runCatchingCancellable { translator.translate(word) }
            .onSuccess { translation ->
                updateLookup {
                    it.copy(status = LookupStatus.READY, translation = translation)
                }
                glossParts()
            }
            .onFailure { error ->
                updateLookup {
                    it.copy(
                        status = LookupStatus.FAILED,
                        error = UiText.ofMessageOr(error.message, R.string.lookup_failed),
                    )
                }
            }
    }

    /**
     * Fill in the per-word gloss and the translated line.
     *
     * Done after the phrase itself so the answer appears immediately and the
     * breakdown fills in behind it. This is a structural reading of the
     * selection, not an explanation: it says what each word means and what the
     * line around it says, which is what can honestly be done on-device.
     */
    private suspend fun glossParts() {
        val lookup = _uiState.value.lookup ?: return
        if (lookup.isPhrase) {
            val glossed = lookup.words.map { gloss ->
                val translated = runCatchingCancellable {
                    translator.translate(gloss.word)
                }.getOrNull()
                gloss.copy(translation = translated)
            }
            updateLookup { it.copy(words = glossed) }
        }
        val context = lookup.context
        if (context.isNotBlank() && context != lookup.text) {
            val translated = runCatchingCancellable { translator.translate(context) }.getOrNull()
            updateLookup { it.copy(contextTranslation = translated) }
        }
    }

    /** Apply an edit to the current lookup, if one is still open. */
    private fun updateLookup(edit: (WordLookup) -> WordLookup) {
        _uiState.update { state ->
            state.lookup?.let { state.copy(lookup = edit(it)) } ?: state
        }
    }

    fun onDismissLookup() {
        lookupJob?.cancel()
        _uiState.update { it.copy(lookup = null) }
    }

    fun onLookupHintShown() {
        _uiState.update { it.copy(lookupHint = null) }
    }


    fun onToggleBookmark() = viewModelScope.launch {
        val state = _uiState.value
        libraryData.toggleBookmark(
            uriString = uriString,
            displayName = state.title,
            pageIndex = state.currentPage,
            color = state.highlightColor,
        )
        // The bookmark flow re-emits, so no local state update is needed here.
    }

    fun onHighlightColorChosen(color: Int) = viewModelScope.launch {
        libraryData.setDocumentHighlightColor(uriString, color)
    }

    override fun onCleared() {
        renderer?.close()
        renderer = null
        translator.close()
        pageOcr.close()
        pageCache.evictAll()
    }

    companion object {
        /**
         * How far a press may miss a read word and still count, as a fraction
         * of the page's width.
         *
         * Looser than the text layer's, because a box drawn around a word by
         * recognition sits tighter to the ink than the one a PDF declares.
         */
        private const val OCR_TOUCH_TOLERANCE_FRACTION = 0.02f

        private const val CACHE_SIZE = 6

        /** Words of a selected phrase looked up in the thesaurus. */
        private const val MAX_THESAURUS_WORDS = 4

        /** Splits a selected phrase into words, on spaces and punctuation. */
        private val WORD_SPLIT = Regex("[^\\p{L}·'\u2019]+")
        private const val SEARCH_DEBOUNCE_MS = 300L
        private val ZOOM_STEPS = listOf(1f, 1.5f, 2f, 3f)
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 4f

        fun factory(
            uriString: String,
            title: String,
            targetPage: Int? = null,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LlegeixApp
                ReaderViewModel(
                    app,
                    uriString,
                    app.libraryDataRepository,
                    app.settingsRepository,
                    app.searchHistoryRepository,
                    title,
                    targetPage,
                )
            }
        }
    }
}
