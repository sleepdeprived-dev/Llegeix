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
import com.david.llegeix.data.source.LibraryDataRepository
import android.graphics.RectF
import com.david.llegeix.pdf.PdfiumPageRenderer
import com.david.llegeix.pdf.PdfPageRenderer
import com.david.llegeix.translate.WordTranslator
import com.david.llegeix.R
import com.david.llegeix.ui.common.HighlightColors
import com.david.llegeix.ui.common.UiText
import com.david.llegeix.util.pdfTitle
import com.david.llegeix.util.runCatchingCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How far a tap-and-hold lookup has got. */
enum class LookupStatus { LOOKING_UP, DOWNLOADING_MODEL, READY, FAILED }

/**
 * A word the user pressed and held, plus whatever is known about it so far.
 * [boundsPx] is in the coordinate space of the rendered page bitmap.
 */
data class WordLookup(
    val word: String,
    val pageIndex: Int,
    val boundsPx: RectF,
    val status: LookupStatus = LookupStatus.LOOKING_UP,
    val translation: String? = null,
    val error: UiText? = null,
    /** True once a Wi-Fi-only download has failed, so retrying is worth offering. */
    val canRetryOnAnyNetwork: Boolean = false,
)

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
    /** Shown briefly when a long press lands on a page with no text layer. */
    val lookupHint: UiText? = null,
) {
    /** The filename trimmed for the app bar. */
    val displayTitle: String get() = pdfTitle(title)

    val isCurrentPageBookmarked: Boolean
        get() = currentPage in bookmarkedPages
}

class ReaderViewModel(
    private val application: Application,
    private val uriString: String,
    private val libraryData: LibraryDataRepository,
    title: String,
    /** Page to open on, overriding the remembered position. */
    private val targetPage: Int? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState(title = title))
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var renderer: PdfPageRenderer? = null

    private val translator = WordTranslator()

    private var lookupJob: Job? = null

    /**
     * Keeps a few rendered pages around so swiping back to the previous page is
     * instant. Bitmaps are never recycled here — the pager may still be drawing
     * one as it is evicted, so eviction is left to the garbage collector.
     */
    private val pageCache = LruCache<String, Bitmap>(CACHE_SIZE)

    init {
        openDocument()
        observeStoredState()
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
     * Handle a tap-and-hold on a page: find the word, then translate it.
     *
     * Coordinates arrive in bitmap pixels, which is the space the page was drawn
     * in, so the UI never has to reason about PDF user space.
     */
    fun onWordPressed(
        pageIndex: Int,
        xPx: Float,
        yPx: Float,
        renderedWidthPx: Int,
        renderedHeightPx: Int,
    ) {
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            val active = renderer ?: return@launch
            val word = runCatchingCancellable {
                active.wordAt(pageIndex, xPx, yPx, renderedWidthPx, renderedHeightPx)
            }.getOrNull()

            if (word == null) {
                // Almost always a scanned page: pixels, no text layer.
                _uiState.update {
                    it.copy(
                        lookup = null,
                        lookupHint = UiText.of(R.string.lookup_no_text),
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    lookupHint = null,
                    lookup = WordLookup(
                        word = word.text,
                        pageIndex = pageIndex,
                        boundsPx = word.boundsPx,
                        status = LookupStatus.LOOKING_UP,
                    ),
                )
            }
            translate(word.text)
        }
    }

    /**
     * Retry a failed lookup without insisting on Wi-Fi.
     *
     * The model download defaults to unmetered, which is the polite choice for a
     * download this size, but it leaves anyone without Wi-Fi unable to use the
     * feature at all — so the failure state offers a way through.
     */
    fun onRetryOnAnyNetwork() {
        val word = _uiState.value.lookup?.word ?: return
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            updateLookup { it.copy(status = LookupStatus.LOOKING_UP, error = null) }
            translate(word, requireWifi = false)
        }
    }

    private suspend fun translate(word: String, requireWifi: Boolean = true) {
        if (!translator.isModelReady) {
            updateLookup { it.copy(status = LookupStatus.DOWNLOADING_MODEL) }
            val downloaded = runCatchingCancellable { translator.ensureModel(requireWifi) }
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

    /**
     * Feature 6's second half: a contextual explanation from an LLM. Deliberately
     * not wired to any API yet — this records the intent and keeps the button
     * honest about it.
     */
    fun onExplainMore() {
        updateLookup {
            it.copy(
                error = UiText.of(R.string.lookup_explain_more_stub),
            )
        }
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
        pageCache.evictAll()
    }

    companion object {
        private const val CACHE_SIZE = 6

        fun factory(
            uriString: String,
            title: String,
            targetPage: Int? = null,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LlegeixApp
                ReaderViewModel(app, uriString, app.libraryDataRepository, title, targetPage)
            }
        }
    }
}
