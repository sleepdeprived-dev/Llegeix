package com.david.llegeix.ui.reader

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.data.settings.PageTint
import com.david.llegeix.data.settings.ReadingMode
import com.david.llegeix.data.settings.TranslationTarget
import com.david.llegeix.pdf.DEFAULT_PAGE_ASPECT_RATIO
import com.david.llegeix.pdf.PdfMatch
import com.david.llegeix.resources.*
import com.david.llegeix.ui.common.AppBottomSheet
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.HighlightColors
import com.david.llegeix.ui.common.SearchField
import com.david.llegeix.ui.common.RecentSearches
import com.david.llegeix.ui.common.DetailCard
import com.david.llegeix.ui.common.DictionaryCard
import com.david.llegeix.ui.common.DictionaryStatus
import com.david.llegeix.ui.common.IpaLine
import com.david.llegeix.ui.common.PronounceButton
import com.david.llegeix.ui.common.TranslationTargetFlags
import com.david.llegeix.ui.common.VerbDetails
import com.david.llegeix.ui.common.AppSnackbarHost
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.common.resolved
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Warms a page and takes the glare off its white.
 *
 * Inverting is the right answer for reading in the dark and the wrong one for
 * reading in a lit room, where what actually tires the eye is a screen at full
 * white behind black type. This drops the blue channel hardest and the red
 * least, which is the same thing a paperback does by being made of paper.
 */
private val SepiaFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.96f, 0f, 0f, 0f, 12f,
            0f, 0.90f, 0f, 0f, 4f,
            0f, 0f, 0.78f, 0f, -6f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

/**
 * The filter a tint asks for, or none at all.
 *
 * Inverting is deliberately not one of them. A colour filter is a matrix over
 * every pixel without exception, which is why the inverted page used to turn
 * every photograph in a book into a negative of itself; it is done to the
 * bitmap instead, by
 * [com.david.llegeix.pdf.PageInvert], which can find the pictures and leave
 * them alone. Warming a photograph, on the other hand, is a perfectly
 * reasonable thing to do to a photograph, so sepia stays here.
 */
private fun filterFor(tint: PageTint): ColorFilter? = when (tint) {
    PageTint.NONE, PageTint.INVERT -> null
    PageTint.SEPIA -> SepiaFilter
    // The inversion has already happened to the pixels by the time this is
    // asked, so warming the result is the same operation as warming a page —
    // only now the white it takes the glare off is the type rather than the
    // paper.
    PageTint.WARM_DARK -> WarmDarkFilter
}

/**
 * The same warming as [SepiaFilter], over a page that has already been turned
 * light-on-dark.
 *
 * Gentler than sepia's, because it is being applied to type rather than to
 * paper: the values that matter here are the bright ones, and pulled as far as
 * a sepia page pulls them the text comes out orange rather than cream. The
 * offsets are small and positive on red and green so that the near-black
 * ground warms a little too instead of staying a flat neutral.
 */
private val WarmDarkFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, 6f,
            0f, 0.93f, 0f, 0f, 2f,
            0f, 0f, 0.80f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

/** The gutter colour around an inverted page. */
private val InvertedSurround = Color(0xFF0E0E0E)

/** Enough lift to give the page an edge, not enough to become a card. */
private val PageElevation = 3.dp

/** Enough to read the page counter as sitting over the page, not on it. */
private val PillElevation = 2.dp

/** The hairline that gives an inverted page an edge the shadow cannot. */
private val InvertedPageEdge = Color(0x1FFFFFFF)

/**
 * How long a page already on screen waits before re-rendering at a new width.
 *
 * Long enough to swallow a pinch, short enough that letting go and reading is
 * not a wait.
 */
private const val RENDER_SETTLE_MS = 110L

/** Corner on a highlight: enough to soften a block, too little to be a shape. */
private val HighlightCorner = 3.dp

/**
 * Thickness of the line under a word already saved.
 *
 * A hairline. It has to be findable when looked for and invisible when not,
 * because it appears on words the reader is reading past, not on words they
 * are looking for.
 */
private val SavedWordUnderline = 1.5.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    uriString: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetPage: Int? = null,
    /**
     * A search to open the document on, from the library's "search inside".
     *
     * A single space means "open find, empty": the library's own field may have
     * been blank, and an empty string cannot be told apart from the argument
     * being absent.
     */
    findQuery: String? = null,
) {
    // Keyed by document so navigating to a different PDF gets a fresh renderer
    // rather than reusing the previous one's open file descriptor.
    val viewModel: ReaderViewModel = viewModel(
        // The target page is part of the key so opening a different bookmark in
        // the same document re-creates the reader on the right page.
        key = "$uriString#${targetPage ?: -1}",
        factory = ReaderViewModel.factory(uriString, title, targetPage),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { state.pageCount })
    val scrollState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scrolling = state.readingMode == ReadingMode.SCROLL
    // Read on each event rather than captured: the collector below outlives
    // every change of mode, and a captured flag would keep driving whichever
    // container was in charge when the reader opened the document.
    val scrollingNow by rememberUpdatedState(scrolling)

    LaunchedEffect(pagerState.currentPage) {
        if (!scrolling) viewModel.onPageChanged(pagerState.currentPage)
    }

    // The topmost page still on screen, which is what "the page I am on" means
    // when there is no page turning going on. Read through a snapshot flow
    // rather than as a key: the index changes on every frame of a fling, and as
    // a key it would recompose the whole reader that often.
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.firstVisibleItemIndex }
            .distinctUntilChanged()
            // Read through the holder, not the captured value: this collector
            // outlives every change of reading mode.
            .collect { index -> if (scrollingNow) viewModel.onPageChanged(index) }
    }

    // Search results, the scrubber and the contents drive the pages rather than
    // the other way round.
    LaunchedEffect(Unit) {
        viewModel.pageJumps.collect { page ->
            if (page !in 0 until state.pageCount) return@collect
            if (scrollingNow) {
                scrollState.scrollToItem(page)
            } else if (page < pagerState.pageCount) {
                pagerState.scrollToPage(page)
            }
        }
    }

    // Changing mode should land on the page being read, not at the beginning of
    // whichever container had never been scrolled.
    LaunchedEffect(state.readingMode) {
        if (state.pageCount == 0) return@LaunchedEffect
        val here = state.currentPage.coerceIn(0, state.pageCount - 1)
        if (scrolling) scrollState.scrollToItem(here) else pagerState.scrollToPage(here)
    }

    // Jump to the remembered page once, after the document reports its length.
    // rememberSaveable so a rotation does not yank the reader back to the
    // resume point after the user has already paged away from it.
    var hasRestoredPosition by rememberSaveable(uriString) { mutableStateOf(false) }
    LaunchedEffect(state.pageCount, state.initialPage) {
        if (state.pageCount > 0 && !hasRestoredPosition) {
            hasRestoredPosition = true
            if (state.initialPage in 0 until state.pageCount) {
                if (scrolling) {
                    scrollState.scrollToItem(state.initialPage)
                } else {
                    pagerState.scrollToPage(state.initialPage)
                }
            }
        }
    }

    // Search is a mode with its own chrome, so the system back button has to
    // leave that mode before it leaves the document. Without this the reader
    // has no back handling at all and a single press closes the whole PDF —
    // losing the search and the page you were on in one go.
    //
    // The list of results is a step of its own inside that mode, and back
    // undoes one step at a time: the list first, the search after. Closing both
    // at once would throw away a sweep of the whole document for a press that
    // only meant "let me see the page".
    BackHandler(enabled = state.search.isOpen) {
        if (state.search.showResults && state.search.matches.isNotEmpty()) {
            viewModel.onToggleResults()
        } else {
            viewModel.onCloseSearch()
        }
    }

    // A tick each time the growing selection takes in another word, which is
    // what the platform's own text selection does and what makes dragging
    // across a phrase feel like it is gripping the words rather than sliding
    // over them. Not on the first one: the press that started the selection has
    // already been answered with a tick of its own, and two together read as a
    // stutter.
    val haptics = LocalHapticFeedback.current
    val selectedWords = state.selectionPreview?.wordCount
    var lastSelectedWords by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(selectedWords) {
        if (selectedWords != null && lastSelectedWords != null &&
            selectedWords != lastSelectedWords
        ) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
        lastSelectedWords = selectedWords
    }

    var showDisplay by remember { mutableStateOf(false) }
    var showOutline by remember { mutableStateOf(false) }

    /*
     * The shape of the first page that reported one, used for every page that
     * has not yet.
     *
     * This is what stops the strip of pages moving under the finger. A page in
     * scroll mode used to stand at a guess — A4 portrait — until its bitmap
     * arrived and gave it its real height, and a page that changes height is a
     * lazy list that re-lays out everything below it. Scrolling downwards goes
     * into pages that have not been drawn yet, so it was scrolling into a
     * column that kept resizing itself: the page under the finger jumped, the
     * topmost visible page changed with it, and the page number and the mark in
     * the contents flickered between two answers.
     *
     * Each page is measured properly, which is cheap and needs no bitmap. This
     * only covers the moment before that answer comes back, and covers it well
     * because the pages of a book are all the same size: the first page to
     * report is almost always the right answer for every page after it.
     * Forgotten when the crop is toggled, which is the one thing that changes
     * a page's shape.
     */
    var assumedAspect by remember(state.cropMargins) { mutableStateOf<Float?>(null) }

    // Opening find with the chrome hidden would put a text field on screen with
    // no visible way back out of it.
    LaunchedEffect(state.search.isOpen) {
        if (state.search.isOpen) viewModel.onShowChrome()
    }

    // Arrived from the library's "search inside", carrying whatever was typed
    // there. Once only: it is how the reader was opened, not a thing to be
    // reapplied every time the state changes.
    LaunchedEffect(findQuery) {
        val query = findQuery ?: return@LaunchedEffect
        viewModel.onOpenSearch()
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) viewModel.onSearchQueryChange(trimmed)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val lookupHint = state.lookupHint?.resolved()
    LaunchedEffect(lookupHint) {
        val hint = lookupHint ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(hint)
        viewModel.onLookupHintShown()
    }

    Scaffold(
        // The app shell's Scaffold has already inset this screen for the
        // status bar and the navigation bar; counting them a second time
        // put a dead band above the bottom bar and made every top bar
        // 24dp taller than it asks to be.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        // One bar, and only one. The bottom bar this screen used to carry took a
        // second slice out of the page for a toggle that fits in the top row.
        topBar = {
            if (state.search.isOpen) {
                SearchBar(
                    search = state.search,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onNext = viewModel::onNextMatch,
                    onPrevious = viewModel::onPreviousMatch,
                    onClose = viewModel::onCloseSearch,
                    onToggleResults = viewModel::onToggleResults,
                )
            } else {
                // Slid out of the way rather than switched off, so hiding the
                // chrome is visibly the same object leaving rather than the
                // page jumping upward by the height of a bar.
                AnimatedVisibility(
                    visible = state.isChromeVisible,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        ReaderBar(
                            title = state.displayTitle,
                            isBookmarked = state.isCurrentPageBookmarked,
                            highlightColor = state.highlightColor,
                            hasOutline = state.hasOutline,
                            canUseTools = state.pageCount > 0,
                            onBack = onBack,
                            onFind = viewModel::onOpenSearch,
                            onOutline = { showOutline = true },
                            onDisplay = { showDisplay = true },
                            onToggleBookmark = viewModel::onToggleBookmark,
                            onColorChosen = viewModel::onHighlightColorChosen,
                        )
                        PageScrubber(
                            page = state.currentPage,
                            pageCount = state.pageCount,
                            onGoToPage = viewModel::onGoToPage,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(
                    // A near-black surround while pages are inverted, whatever
                    // the app theme is. A pale gutter around a black page is a
                    // bright band right next to what you are reading.
                    if (state.isInverted) {
                        InvertedSurround
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.isOpening -> CircularProgressIndicator()

                state.error != null -> EmptyState(
                    title = stringResource(Res.string.reader_open_failed_title),
                    body = state.error?.resolved()
                        ?: stringResource(Res.string.reader_open_failed_body),
                )

                state.pageCount == 0 -> EmptyState(
                    title = stringResource(Res.string.reader_no_pages_title),
                    body = stringResource(Res.string.reader_no_pages_body),
                )

                else -> {
                    // One description of a page, drawn by whichever container
                    // is in charge. Written once because the two reading modes
                    // differ in how pages are arranged and in nothing else —
                    // selection, search highlights, zoom and the tap that hides
                    // the bar all have to behave identically, and two copies of
                    // this is two places for them to stop doing so.
                    val drawPage: @Composable (Int) -> Unit = { index ->
                        PdfPage(
                            index = index,
                            zoom = state.zoom,
                            cropMargins = state.cropMargins,
                            // In scroll mode the magnification is the width of
                            // the column rather than a transform on the page,
                            // so the page is drawn unscaled and simply rendered
                            // wider. Scaling inside a lazy list would paint each
                            // page outside the slot the list had measured for
                            // it. The zoom itself is still passed in full: the
                            // gestures have to know what it currently is, or a
                            // double tap in scroll mode magnifies for ever and
                            // never puts the page back.
                            applyScale = !scrolling,
                            tint = state.pageTint,
                            render = viewModel::renderPage,
                            measureAspectRatio = { page ->
                                viewModel.pageAspectRatio(page)?.also {
                                    if (assumedAspect == null) assumedAspect = it
                                }
                            },
                            assumedAspectRatio = assumedAspect ?: DEFAULT_PAGE_ASPECT_RATIO,
                            // Passed in rather than read inside, so stepping to
                            // the next match re-runs the effect that fetches the
                            // rectangles instead of leaving the old ones drawn.
                            searchMatch = state.search.current?.takeIf {
                                it.pageIndex == index
                            },
                            highlights = { widthPx, heightPx ->
                                viewModel.matchHighlights(index, widthPx, heightPx)
                            },
                            savedWords = { widthPx, heightPx ->
                                viewModel.savedWordHighlights(index, widthPx, heightPx)
                            },
                            savedWordsKey = if (state.markSavedWords) {
                                state.savedWords
                            } else {
                                emptySet<String>()
                            },
                            onZoomChanged = viewModel::onZoomChanged,
                            onTap = viewModel::onToggleChrome,
                            onSelectPreview = { x1, y1, x2, y2, w, h ->
                                viewModel.onSelectionPreview(index, x1, y1, x2, y2, w, h)
                            },
                            onSelectCommit = { x1, y1, x2, y2, w, h ->
                                // Not while the pager is still settling. A
                                // sheet that animates up as the page slides
                                // under it fights the fling, and both look
                                // broken.
                                if (!scrolling && pagerState.isScrollInProgress) {
                                    viewModel.onSelectionCancelled()
                                } else {
                                    viewModel.onSelectionCommitted(index, x1, y1, x2, y2, w, h)
                                }
                            },
                            onSelectCancel = viewModel::onSelectionCancelled,
                            onTurnPage = { step ->
                                val target = (index + step)
                                    .coerceIn(0, state.pageCount - 1)
                                if (target != index) {
                                    scope.launch { pagerState.animateScrollToPage(target) }
                                }
                            },
                            // While the finger is down the highlight comes from
                            // the preview; once the sheet is open it comes from
                            // the lookup it is showing.
                            lookupHighlights = state.selectionPreview
                                ?.takeIf { it.pageIndex == index }
                                ?.boundsPx
                                ?: state.lookup
                                    ?.takeIf { it.pageIndex == index }
                                    ?.boundsPx
                                    .orEmpty(),
                            highlightColor = state.highlightColor,
                            modifier = if (scrolling) {
                                Modifier.fillMaxWidth()
                            } else {
                                Modifier.fillMaxSize()
                            },
                        )
                    }

                    if (scrolling) {
                        ScrollingPages(
                            pageCount = state.pageCount,
                            zoom = state.zoom,
                            listState = scrollState,
                            drawPage = drawPage,
                        )
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            // One page either side, so a swipe lands on a
                            // rendered page instead of a placeholder.
                            beyondViewportPageCount = 1,
                            // A drag while zoomed in has to pan the page, not
                            // turn it; otherwise the magnified view is unusable.
                            userScrollEnabled = !state.isZoomed,
                        ) { index -> drawPage(index) }
                    }

                    // Goes with the bar. It is the same piece of furniture —
                    // the app telling you where you are — and leaving it behind
                    // when the bar slides away would mean a tap on the page
                    // cleared everything except one floating pill.
                    AnimatedVisibility(
                        visible = state.isChromeVisible,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        PagePill(
                            page = state.currentPage + 1,
                            pageCount = state.pageCount,
                            zoom = state.zoom,
                            modifier = Modifier
                                .navigationBarsPadding()
                                .padding(bottom = Space.lg),
                        )
                    }
                }
            }

            // Over the page rather than beside it, and only once there is
            // something to say: a panel that appears on the first keystroke and
            // empties itself on the second is a flicker where an answer should
            // be. Padded for the keyboard, since the field above it is still
            // focused and half the list would otherwise be underneath it.
            // Opening find and typing the same word again is most of what find
            // gets used for in a book being worked through, so the last few
            // words are what the panel holds until there is a query to answer.
            // The same surface as the results, because it is the same question
            // at an earlier stage — not a strip wedged into the bar above.
            if (state.search.isOpen &&
                state.search.query.isBlank() &&
                recentSearches.isNotEmpty()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                ) {
                    RecentSearches(
                        history = recentSearches,
                        onPick = viewModel::onSearchQueryChange,
                        onClear = viewModel::onForgetSearches,
                        onRemove = viewModel::onForgetSearch,
                        modifier = Modifier.padding(top = Space.md),
                    )
                }
            }

            if (state.search.isOpen &&
                state.search.showResults &&
                (state.search.matches.isNotEmpty() || state.search.isEmptyResult)
            ) {
                SearchResultsPanel(
                    search = state.search,
                    onSelect = viewModel::onSelectMatch,
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                )
            }
        }
    }

    if (showDisplay) {
        DisplaySheet(
            tint = state.pageTint,
            cropMargins = state.cropMargins,
            readingMode = state.readingMode,
            zoom = state.zoom,
            onTintChosen = viewModel::onPageTintChosen,
            onToggleCrop = viewModel::onToggleCropMargins,
            onReadingModeChosen = viewModel::onReadingModeChosen,
            markSavedWords = state.markSavedWords,
            onToggleMarkSavedWords = viewModel::onToggleMarkSavedWords,
            onZoomChosen = viewModel::onZoomChanged,
            onDismiss = { showDisplay = false },
        )
    }

    if (showOutline && state.hasOutline) {
        OutlineSheet(
            outline = state.outline,
            currentPage = state.currentPage,
            onGoToPage = viewModel::onGoToPage,
            onDismiss = { showOutline = false },
        )
    }

    state.lookup?.let { lookup ->
        WordLookupSheet(
            lookup = lookup,
            target = state.translationTarget,
            onDismiss = viewModel::onDismissLookup,
            onRetryOnAnyNetwork = viewModel::onRetryOnAnyNetwork,
            onToggleSaved = viewModel::onToggleWordBookmark,
            onToggleTarget = viewModel::onToggleTranslationTarget,
            onShowDictionary = viewModel::onShowDictionary,
        )
    }
}

/**
 * The reader's only chrome: back, the title, and the three things you actually
 * do while reading.
 *
 * It used to carry four buttons — find, zoom, invert, bookmark — which left a
 * book's title about half the width of the screen and still had nowhere to put
 * the settings that matter most on a phone. Zoom and invert were never actions:
 * they are how the page is drawn, they are changed rarely, and they belong
 * together in a sheet with margin cropping and the scroll mode. What is left on
 * the bar is find, the contents, and the bookmark, plus the way into that
 * sheet.
 *
 * The highlight colour lives on a long press of the bookmark button. It is a
 * once-a-document choice, and giving it a permanent slot would cost the title
 * the room this rearrangement just won back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderBar(
    title: String,
    isBookmarked: Boolean,
    highlightColor: Int,
    /** Whether the document has a contents worth offering a button for. */
    hasOutline: Boolean,
    canUseTools: Boolean,
    onBack: () -> Unit,
    onFind: () -> Unit,
    onOutline: () -> Unit,
    onDisplay: () -> Unit,
    onToggleBookmark: () -> Unit,
    onColorChosen: (Int) -> Unit,
) {
    var colorMenuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        windowInsets = WindowInsets(0, 0, 0, 0),

        expandedHeight = Space.topBar,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.reader_back),
                )
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        actions = {
            IconButton(onClick = onFind, enabled = canUseTools) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(Res.string.reader_find),
                )
            }
            // Only for a document that has one. A permanently greyed button is
            // a promise the app cannot keep for most scans.
            if (hasOutline) {
                IconButton(onClick = onOutline, enabled = canUseTools) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_toc),
                        contentDescription = stringResource(Res.string.reader_contents),
                    )
                }
            }
            IconButton(onClick = onDisplay, enabled = canUseTools) {
                Icon(
                    painter = painterResource(Res.drawable.ic_display),
                    contentDescription = stringResource(Res.string.reader_display),
                )
            }
            Box {
                BookmarkButton(
                    isBookmarked = isBookmarked,
                    highlightColor = highlightColor,
                    enabled = canUseTools,
                    onClick = onToggleBookmark,
                    onLongClick = { colorMenuOpen = true },
                )
                DropdownMenu(
                    expanded = colorMenuOpen,
                    onDismissRequest = { colorMenuOpen = false },
                ) {
                    Text(
                        text = stringResource(Res.string.reader_highlight_colour),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
                    )
                    HighlightColors.palette.chunked(3).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Space.md),
                            modifier = Modifier.padding(
                                horizontal = Space.lg,
                                vertical = Space.sm,
                            ),
                        ) {
                            row.forEach { candidate ->
                                ColorSwatch(
                                    color = candidate,
                                    selected = candidate == highlightColor,
                                    onClick = {
                                        onColorChosen(candidate)
                                        colorMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/** Tap to bookmark the page, hold to pick the colour it is marked in. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkButton(
    isBookmarked: Boolean,
    highlightColor: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(horizontal = Space.xs)
            .clip(CircleShape)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(Space.md),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isBookmarked) Icons.Filled.Star else Icons.Outlined.Star,
            contentDescription = stringResource(
                if (isBookmarked) {
                    Res.string.reader_remove_page_bookmark
                } else {
                    Res.string.reader_add_page_bookmark
                },
            ),
            tint = if (isBookmarked) {
                HighlightColors.compose(highlightColor)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * Replaces the reader bar while a search is running.
 *
 * Its own row rather than a TopAppBar, so its height is stated here and cannot
 * be quietly clipped: forced into the bar as a title, the field's outline was
 * sliced off at the top and spilled over the page at the bottom.
 *
 * The match count sits inside the field, where every browser puts it. Loose in
 * the bar it rendered as "1 / 8" a thumb's width from the page counter's
 * "1 / 8" — the same six characters, on screen at the same time, meaning two
 * different things. Inside the box it can only be about the search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    search: SearchState,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    onToggleResults: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val hasMatches = search.matches.isNotEmpty()

    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(Space.topBar)
                .padding(horizontal = Space.xs),
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(Res.string.reader_find_close),
                )
            }
            SearchField(
                query = search.query,
                placeholder = stringResource(Res.string.reader_find_hint),
                onQueryChange = onQueryChange,
                focusRequester = focusRequester,
                trailing = {
                    val counter = when {
                        search.query.isBlank() -> null
                        search.isEmptyResult -> stringResource(Res.string.reader_find_none)
                        hasMatches -> stringResource(
                            Res.string.reader_find_position,
                            search.currentIndex + 1,
                            search.matches.size,
                        )
                        // Still working, and nothing to report yet: the line
                        // under the bar is already saying so.
                        else -> null
                    }
                    if (counter != null) {
                        Text(
                            text = counter,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Space.xs),
            )
            IconButton(onClick = onPrevious, enabled = hasMatches) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(Res.string.reader_find_previous),
                )
            }
            IconButton(onClick = onNext, enabled = hasMatches) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(Res.string.reader_find_next),
                )
            }
        }
        // Progress rather than a spinner: a long document takes a moment, and
        // matches keep arriving while it does.
        AnimatedVisibility(visible = search.isSearching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        // What was found, and the way in and out of the list of it. Counting
        // the pages as well as the hits because they answer different
        // questions: forty hits on one page is a word this document is about,
        // and forty across thirty pages is a word to go looking through.
        if (hasMatches) {
            val pagesWithMatches = remember(search.matches) {
                search.matches.distinctBy { it.pageIndex }.size
            }
            SearchResultsBar(
                matchCount = search.matches.size,
                pageCount = pagesWithMatches,
                isOpen = search.showResults,
                onToggle = onToggleResults,
            )
        }
    }
}


/**
 * The floating page counter, in place of a whole bar.
 *
 * It says "Page 3 of 8" rather than "3 / 8". The find bar counts matches in
 * exactly the same shape, and two identical pairs of numbers on screen at once
 * meaning different things is a puzzle nobody asked for. Four extra characters
 * settle it for good.
 */
@Composable
private fun PagePill(
    page: Int,
    pageCount: Int,
    zoom: Float,
    modifier: Modifier = Modifier,
) {
    val position = stringResource(Res.string.reader_page_position, page, pageCount)
    // One decimal, and no trailing ".0" on the whole steps.
    val rounded = (zoom * 10f).roundToInt() / 10f
    val zoomLabel = stringResource(
        Res.string.reader_zoom_level,
        if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString(),
    )
    val label = if (zoom > 1.01f) "$position  ·  $zoomLabel" else position
    Box(
        modifier = modifier
            // Opaque, and lifted off the page. It used to be nine-tenths of the
            // surface colour, which was fine while there was always a gutter
            // under it — but a page with its margins trimmed reaches the bottom
            // of the screen, so the last line of type showed through the pill
            // reporting which page it was on.
            .shadow(elevation = PillElevation, shape = RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Space.lg, vertical = Space.sm),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Every page in one strip, for readers who would rather scroll than turn.
 *
 * Magnification here is the width of the column rather than a transform on each
 * page: a lazy list measures a slot for an item and then draws it, so a page
 * scaled up inside its slot paints over its neighbours and scrolls at the wrong
 * speed. Widening the column instead makes each page render larger for real —
 * sharper, not just bigger — and the sideways scroll is what reaches the part
 * of it that no longer fits.
 */
@Composable
private fun ScrollingPages(
    pageCount: Int,
    zoom: Float,
    listState: LazyListState,
    drawPage: @Composable (Int) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columnWidth = maxWidth * zoom
        val horizontal = rememberScrollState()
        // Re-centred when the magnification goes back to nothing, so leaving a
        // zoomed page does not leave the column parked off to one side with a
        // band of gutter down the edge of the screen.
        LaunchedEffect(zoom) {
            if (zoom <= 1.01f) horizontal.scrollTo(0)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizontal, enabled = zoom > 1.01f),
            // A real gap between pages, because in a strip the only thing
            // saying where one page ends and the next begins is the space.
            verticalArrangement = Arrangement.spacedBy(Space.md),
            contentPadding = PaddingValues(vertical = Space.md),
        ) {
            items(pageCount, key = { it }) { index ->
                Box(modifier = Modifier.width(columnWidth)) {
                    drawPage(index)
                }
            }
        }
    }
}

/**
 * One page of the document. Rendering is keyed on the measured width, so a
 * rotation re-renders at the new resolution rather than upscaling a stale
 * bitmap.
 */
@Composable
private fun PdfPage(
    index: Int,
    zoom: Float,
    /**
     * Whether the zoom is drawn as a transform on this page.
     *
     * False in scroll mode, where the column is widened instead. The page still
     * has to be told the zoom either way, because the gestures read it.
     */
    applyScale: Boolean,
    /**
     * Whether margins are being trimmed.
     *
     * Passed in only so that this page is re-rendered when it changes. The
     * renderer reads the setting itself; without it named here as well, the
     * effect that fetches the bitmap keys on the page index and the measured
     * width alone — neither of which moves when the switch is flicked — and the
     * reader is left looking at the uncropped page it already had.
     */
    cropMargins: Boolean,
    tint: PageTint,
    render: suspend (index: Int, widthPx: Int) -> Bitmap?,
    /**
     * The shape this page will come out, asked before it is drawn.
     *
     * A lazy list has to measure an item before it can place the ones below it,
     * so in scroll mode a page that does not know its own shape until its
     * bitmap arrives is a page that changes height in the middle of a scroll.
     * See [assumedAspectRatio] for what stands in until this answers.
     */
    measureAspectRatio: suspend (index: Int) -> Float?,
    /**
     * What this page is assumed to look like until it has been measured.
     *
     * The shape of the first page that answered, because the pages of a book
     * are all the same size as each other: one measurement is normally the
     * right answer for every page in the document, and a page that starts at
     * the right height never has to move.
     */
    assumedAspectRatio: Float,
    searchMatch: PdfMatch?,
    highlights: suspend (widthPx: Int, heightPx: Int) -> List<RectF>,
    /** Where words the reader has already saved sit on this page. */
    savedWords: suspend (widthPx: Int, heightPx: Int) -> List<RectF>,
    /**
     * The saved words themselves, used only as the key that re-runs the
     * lookup.
     *
     * The set rather than its size: saving one word and unsaving another leaves
     * the count where it was, and a page that then keeps its old underlines is
     * marking a word the reader has just removed.
     */
    savedWordsKey: Any,
    onZoomChanged: (Float) -> Unit,
    /** A single tap on the page, which hides the chrome and puts it back. */
    onTap: () -> Unit,
    onSelectPreview: (
        startXPx: Float, startYPx: Float,
        endXPx: Float, endYPx: Float,
        widthPx: Int, heightPx: Int,
    ) -> Unit,
    onSelectCommit: (
        startXPx: Float, startYPx: Float,
        endXPx: Float, endYPx: Float,
        widthPx: Int, heightPx: Int,
    ) -> Unit,
    onSelectCancel: () -> Unit,
    /** Called with -1 or +1 when a magnified page is pushed past its edge. */
    onTurnPage: (Int) -> Unit,
    lookupHighlights: List<RectF>,
    highlightColor: Int,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    // Read here rather than inside the draw scope, which is not a composable
    // and cannot reach the theme.
    val savedMark = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    BoxWithConstraints(
        // Without this a magnified page paints straight over the pages either
        // side of it in the pager, so page 1 at 2x shows a slice of page 2.
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        // Kept across a change of width rather than cleared, so a page being
        // re-rendered at a new size goes on showing the size it had instead of
        // blinking to a spinner and back.
        var bitmap by remember(index) { mutableStateOf<Bitmap?>(null) }
        var offset by remember(index) { mutableStateOf(Offset.Zero) }
        // How tall a slot this page asks for, which it can say before it has
        // anything to put in it. Keyed on the crop as well as the page,
        // because trimming the margins changes a page's shape and not only its
        // size.
        var aspectRatio by remember(index, cropMargins) { mutableFloatStateOf(assumedAspectRatio) }
        // The gesture handlers outlive the composition that started them, so
        // they have to read the zoom through a holder rather than capture it —
        // a captured value goes stale the moment the first pinch changes it.
        val currentZoom by rememberUpdatedState(zoom)
        val isZoomed = applyScale && zoom > 1.01f
        // What this page is actually drawn at, which is 1 in scroll mode however
        // magnified the column is.
        val drawnZoom = if (applyScale) zoom else 1f

        // Before the render rather than with it: this is the cheap half of the
        // question — the document's own dimensions, no rasterising — and its
        // whole point is to be answered while the bitmap is still being drawn.
        LaunchedEffect(index, cropMargins) {
            measureAspectRatio(index)?.let { aspectRatio = it }
        }

        LaunchedEffect(index, widthPx, cropMargins, tint) {
            // A page already on screen waits a moment before being redrawn at a
            // new width. In scroll mode the magnification *is* the width of the
            // column, so a pinch walks it through every value in between — and
            // without this pause each one of those was a full re-render of every
            // page on screen. The first render of a page never waits, because
            // there is nothing to look at until it lands.
            if (bitmap != null) delay(RENDER_SETTLE_MS)
            bitmap = render(index, widthPx) ?: bitmap
        }

        // Panning only makes sense while magnified; snapping back on the way out
        // avoids leaving the page parked off-centre.
        LaunchedEffect(drawnZoom) {
            if (drawnZoom <= 1.01f) offset = Offset.Zero
        }

        val rendered = bitmap
        if (rendered != null) {
            // Everything below holds to the reader's own press timing rather
            // than the platform's; see SelectionTiming.
            SelectionTiming {
                // The composable is given the bitmap's exact aspect ratio, so a touch
                // offset maps to bitmap pixels by a single scale factor with no
                // letterboxing to compensate for.
                var drawnSize by remember { mutableStateOf(IntSize.Zero) }
                var searchRects by remember(index, rendered) { mutableStateOf(emptyList<RectF>()) }
                var savedRects by remember(index, rendered) { mutableStateOf(emptyList<RectF>()) }

                LaunchedEffect(index, rendered, drawnSize, searchMatch) {
                    searchRects = if (searchMatch != null && drawnSize.width > 0) {
                        highlights(rendered.width, rendered.height)
                    } else {
                        emptyList()
                    }
                }

                // Off the main thread and after the page is already on screen:
                // walking a page's text is not free, and nothing about the page
                // waits for the answer.
                LaunchedEffect(index, rendered, savedWordsKey) {
                    savedRects = savedWords(rendered.width, rendered.height)
                }

                Image(
                    bitmap = rendered.asImageBitmap(),
                    contentDescription = stringResource(
                        Res.string.reader_page_content_description,
                        index + 1,
                    ),
                    contentScale = ContentScale.Fit,
                    colorFilter = filterFor(tint),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.sm, vertical = Space.sm)
                        .aspectRatio(rendered.width.toFloat() / rendered.height.toFloat())
                        .graphicsLayer {
                            scaleX = drawnZoom
                            scaleY = drawnZoom
                            // Anchored at the top, not the centre. A page's text
                            // starts at the top, so centre-anchored zoom throws you
                            // into the middle of the margin and looks broken.
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            translationX = offset.x
                            translationY = offset.y
                        }
                        // Inside the transform, so the shadow moves and grows with
                        // the page rather than staying behind where it started.
                        // A page with no edge on a pale ground is a white rectangle
                        // that happens to have words on it; this makes it paper.
                        .shadow(
                            elevation = PageElevation,
                            shape = RectangleShape,
                            clip = false,
                        )
                        // A shadow is invisible on a near-black ground, so the
                        // inverted page gets a hairline instead. Without one, black
                        // paper on a black desk has no edge at all, and a page
                        // pushed around while magnified has nothing to push.
                        .then(
                            if (tint.invertsPage) {
                                Modifier.border(1.dp, InvertedPageEdge)
                            } else {
                                Modifier
                            },
                        )
                        .onSizeChanged { drawnSize = it }
                        .pinchToZoom(
                            key = index,
                            currentZoom = { currentZoom },
                            minZoom = ReaderViewModel.MIN_ZOOM,
                            maxZoom = ReaderViewModel.MAX_ZOOM,
                            onZoomChanged = onZoomChanged,
                        )
                        // Dragging pans, but only while magnified — the pager owns
                        // horizontal drags the rest of the time.
                        .then(
                            if (isZoomed) {
                                // Keyed on the page alone. Including the zoom here
                                // restarted the detector every time the zoom
                                // changed, which cancelled the drag that was in
                                // flight the moment a pinch ended.
                                Modifier.pointerInput(index) {
                                    var edgePush = 0f
                                    detectDragGestures(
                                        onDragEnd = { edgePush = 0f },
                                        onDragCancel = { edgePush = 0f },
                                    ) { change, drag ->
                                        change.consume()
                                        val live = currentZoom
                                        // This modifier sits inside the graphics
                                        // layer, so the drag arrives already
                                        // divided by the zoom. Undo that, or
                                        // panning crawls at half speed at 2x and a
                                        // third at 3x.
                                        val moved = drag * live
                                        // Horizontally the page grows both ways from
                                        // the centre; vertically only downward,
                                        // because the top edge is pinned.
                                        val maxX = size.width * (live - 1f) / 2f
                                        val minY = -size.height * (live - 1f)
                                        val wantedX = offset.x + moved.x
                                        val settledX = wantedX.coerceIn(-maxX, maxX)
                                        offset = Offset(
                                            settledX,
                                            (offset.y + moved.y).coerceIn(minY, 0f),
                                        )

                                        // Once the page can go no further sideways,
                                        // keep pushing and it turns. Insisting is
                                        // the whole point: a small nudge past the
                                        // edge while reading should do nothing, so
                                        // only a sustained push in one direction
                                        // counts, and the count resets the moment
                                        // the finger goes the other way.
                                        val spare = wantedX - settledX
                                        if (spare != 0f && abs(moved.x) > abs(moved.y)) {
                                            if (spare > 0f != edgePush > 0f) edgePush = 0f
                                            edgePush += spare
                                            if (abs(edgePush) > PAGE_TURN_PUSH_PX) {
                                                edgePush = 0f
                                                // Pushing the page to the right
                                                // reveals what is to its left, so
                                                // that is a step backwards.
                                                onTurnPage(if (spare > 0f) -1 else 1)
                                            }
                                        }
                                    }
                                }
                            } else {
                                Modifier
                            },
                        )
                        .doubleTapToZoom(
                            key = index,
                            currentZoom = { currentZoom },
                            minZoom = ReaderViewModel.MIN_ZOOM,
                            magnified = DOUBLE_TAP_ZOOM,
                            onZoomChanged = onZoomChanged,
                            onTap = onTap,
                        )
                        // Press and hold picks a word; keep dragging and the
                        // selection grows to a phrase. One gesture, so there is
                        // nothing extra to learn to select more than one word.
                        //
                        // Nothing is looked up until the finger lifts. Opening the
                        // sheet on the first word, as this used to, put a panel
                        // over the very text the reader was still trying to drag
                        // across.
                        .pointerInput(index, rendered, drawnSize) {
                            var anchor = Offset.Zero
                            var last = Offset.Zero
                            detectDragGesturesAfterLongPress(
                                onDragStart = { start ->
                                    anchor = start
                                    last = start
                                    // Before anything is looked up, and before the
                                    // page has even been asked what is there. The
                                    // hold is over the moment it is over, and
                                    // saying so on that frame is most of what makes
                                    // it feel immediate — the highlight is a few
                                    // milliseconds behind and nobody notices,
                                    // because the gesture has already been
                                    // answered.
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    emitSelection(start, start, rendered, drawnSize, onSelectPreview)
                                },
                                onDrag = { change, _ ->
                                    last = change.position
                                    emitSelection(
                                        anchor,
                                        last,
                                        rendered,
                                        drawnSize,
                                        onSelectPreview,
                                    )
                                },
                                onDragEnd = {
                                    emitSelection(anchor, last, rendered, drawnSize, onSelectCommit)
                                },
                                onDragCancel = onSelectCancel,
                            )
                        }
                        .drawWithContent {
                            drawContent()
                            if (drawnSize.width <= 0) return@drawWithContent
                            val scaleX = size.width / rendered.width
                            val scaleY = size.height / rendered.height
                            // Rounded, and rounded in screen pixels rather than in
                            // the page's, so the corner keeps its size as the page
                            // is magnified. A run of hard-cornered blocks over a
                            // line of type reads as a redaction; the platform's own
                            // selection is rounded for the same reason.
                            val corner = CornerRadius(HighlightCorner.toPx())

                            fun mark(box: RectF, color: Color) = drawRoundRect(
                                color = color,
                                topLeft = Offset(box.left * scaleX, box.top * scaleY),
                                size = Size(box.width() * scaleX, box.height() * scaleY),
                                cornerRadius = corner,
                            )

                            // Underlined rather than blocked in. These are
                            // words already worked on, so they are a note in
                            // the margin of the page, not something being
                            // pointed at — a run of filled highlights across a
                            // page you are trying to read is the page shouting.
                            savedRects.forEach { box ->
                                val underline = SavedWordUnderline.toPx()
                                drawRoundRect(
                                    color = savedMark,
                                    topLeft = Offset(
                                        box.left * scaleX,
                                        box.bottom * scaleY - underline,
                                    ),
                                    size = Size(box.width() * scaleX, underline),
                                    cornerRadius = CornerRadius(underline / 2f),
                                )
                            }
                            lookupHighlights.forEach { box ->
                                mark(box, HighlightColors.compose(highlightColor).copy(alpha = 0.4f))
                            }
                            searchRects.forEach { box ->
                                mark(
                                    box,
                                    HighlightColors.compose(HighlightColors.Blue).copy(alpha = 0.45f),
                                )
                            }
                        },
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.sm, vertical = Space.sm)
                    .aspectRatio(aspectRatio)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

/**
 * Maps two points in the drawn page to bitmap pixels and reports the range.
 *
 * Both ends are held inside the page. Dragging off the edge of it is what
 * reaching the end of a line actually looks like — the finger overshoots into
 * the margin and then off the paper altogether — and a point on no page at all
 * finds no character, which collapsed the whole selection back to the word it
 * started on at the exact moment the reader got to the end of the phrase they
 * were after. Clamped, the drag simply stops at the edge and keeps everything
 * it crossed.
 */
private fun emitSelection(
    start: Offset,
    end: Offset,
    rendered: Bitmap,
    drawnSize: IntSize,
    onSelect: (Float, Float, Float, Float, Int, Int) -> Unit,
) {
    if (drawnSize.width <= 0 || drawnSize.height <= 0) return
    val scaleX = rendered.width.toFloat() / drawnSize.width
    val scaleY = rendered.height.toFloat() / drawnSize.height
    fun onPageX(value: Float) = (value * scaleX).coerceIn(0f, rendered.width - 1f)
    fun onPageY(value: Float) = (value * scaleY).coerceIn(0f, rendered.height - 1f)
    onSelect(
        onPageX(start.x),
        onPageY(start.y),
        onPageX(end.x),
        onPageY(end.y),
        rendered.width,
        rendered.height,
    )
}

/**
 * What a selection means, laid out so the answer is found without reading.
 *
 * The order is fixed and always the same: the Catalan, its pronunciation, the
 * English. Everything after that — the line it came from, and for a phrase the
 * word-by-word breakdown — is supporting detail, set quieter and further down.
 * Someone who only wants the translation should never have to look for it.
 *
 * Download is its own visible state because the ML Kit models are a
 * tens-of-megabytes fetch on first use; after that every lookup is on-device
 * and effectively instant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordLookupSheet(
    lookup: WordLookup,
    target: TranslationTarget,
    onDismiss: () -> Unit,
    onRetryOnAnyNetwork: () -> Unit,
    onToggleSaved: () -> Unit,
    onToggleTarget: () -> Unit,
    onShowDictionary: () -> Unit,
) {
    // Deliberately *not* skipping the partial height, unlike every other sheet
    // in the app. This one is an answer about a word on the page behind it, and
    // at full height it covered the sentence the word came from — which is most
    // of what makes a translation mean anything. It opens showing the word, its
    // pronunciation, the translation and the star, and everything after that is
    // there for anyone who drags it up.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    AppBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Space.xl)
                .padding(bottom = Space.xxl),
        ) {
            // ---- Which way it is being translated, and the two controls ------
            //
            // The quiet row: what the sheet is doing, the language it is doing
            // it into, and whether the word is kept. The word itself gets the
            // full width underneath, where a long phrase has room.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        Res.string.lookup_direction,
                        stringResource(target.directionRes),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TranslationTargetFlags(target = target, onPick = onToggleTarget)
                IconButton(onClick = onToggleSaved) {
                    Icon(
                        imageVector = if (lookup.isSaved) {
                            Icons.Filled.Star
                        } else {
                            Icons.Outlined.Star
                        },
                        contentDescription = stringResource(
                            if (lookup.isSaved) {
                                Res.string.lookup_unsave_word
                            } else {
                                Res.string.lookup_save_word
                            },
                        ),
                        tint = if (lookup.isSaved) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            // The speaker belongs beside the word rather than up in the row of
            // controls: the star keeps the word, the flags change the language,
            // and this one *is* the word. A pronunciation printed in IPA is a
            // notation somebody has to have learned; this is the same fact for
            // everybody else.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = Space.xs),
            ) {
                Text(
                    text = lookup.text,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f, fill = false),
                )
                PronounceButton(text = lookup.text)
            }
            if (lookup.ipa.isNotBlank()) {
                IpaLine(
                    ipa = lookup.ipa,
                    isApproximate = lookup.isIpaApproximate,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }

            // ---- The answer -------------------------------------------------
            //
            // A floor under the height on purpose. The translation lands a
            // moment after the sheet starts animating up, and without this the
            // sheet grew mid-animation and the whole thing juddered.
            Box(
                modifier = Modifier
                    .padding(top = Space.xl)
                    .heightIn(min = ANSWER_MIN_HEIGHT),
            ) {
                when (lookup.status) {
                    LookupStatus.LOOKING_UP -> Text(
                        text = stringResource(Res.string.lookup_translating),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    LookupStatus.DOWNLOADING_MODEL -> Column {
                        Text(
                            text = stringResource(Res.string.lookup_downloading_title),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(Res.string.lookup_downloading_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.xs),
                        )
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Space.md),
                        )
                    }

                    // The answer, given the weight of an answer: this one line
                    // is why the sheet opened at all.
                    LookupStatus.READY -> Text(
                        text = lookup.translation.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    LookupStatus.FAILED -> Text(
                        text = lookup.error?.resolved()
                            ?: stringResource(Res.string.lookup_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // ---- The expression the word turned out to belong to -------------
            // Directly under the answer, and given the same weight, because
            // this is a headword in the dictionary rather than a guess: "a boca
            // de canó" is what the reader is looking at, and *canó* on its own
            // comes back as "Canyon".
            lookup.here?.takeIf { it.isPhrase }?.let { here ->
                DetailCard(
                    title = stringResource(Res.string.lookup_here_phrase),
                    modifier = Modifier.padding(top = Space.lg),
                ) {
                    Text(
                        text = here.source,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = here.translation,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                }
            }

            // ---- What part of the verb this is, when it is one ---------------
            // Above the line it came from and below the answer, which is where
            // it belongs: the translation says what these letters mean, the
            // line says what was being said with them, and this says what the
            // word *is* — the one of the three a learner is least able to work
            // out on their own. The Dictionary tab shows exactly this, in the
            // same words: the same word should not be explained two ways
            // depending on how it was reached.
            lookup.verb?.let { verb ->
                DetailCard(
                    title = stringResource(Res.string.dictionary_verb_title),
                    modifier = Modifier.padding(top = Space.lg),
                ) {
                    VerbDetails(
                        verb = verb,
                        infinitiveMeaning = lookup.verbInfinitiveMeaning,
                        definition = lookup.verbDefinition,
                    )
                }
            }

            // ---- Word by word, only when there is more than one -------------
            if (lookup.isPhrase && lookup.status == LookupStatus.READY) {
                DetailCard(
                    title = stringResource(Res.string.lookup_word_by_word),
                    modifier = Modifier.padding(top = Space.xl),
                ) {
                    lookup.words.forEachIndexed { index, gloss ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                modifier = Modifier.padding(vertical = Space.sm),
                            )
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = gloss.word,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                if (gloss.ipa.isNotBlank()) {
                                    Text(
                                        text = "[${gloss.ipa}]",
                                        fontFamily = com.david.llegeix.ui.theme.IpaFont,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(
                                text = gloss.translation.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // ---- Where it came from -----------------------------------------
            if (lookup.context.isNotBlank() && lookup.context != lookup.text) {
                DetailCard(
                    title = stringResource(Res.string.lookup_in_context),
                    modifier = Modifier.padding(top = Space.lg),
                ) {
                    Text(
                        text = lookup.context,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (lookup.contextTranslation != null) {
                        Text(
                            text = lookup.contextTranslation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.sm),
                        )
                    }
                    // The reading taken from the line belongs beside the line it
                    // was taken from, not up beside the answer. It is only as
                    // good as the translation printed directly above it — when
                    // that line reads as nonsense, so does this — and putting
                    // the two together lets the reader see that for themselves
                    // instead of being asked to trust a second confident word.
                    lookup.here?.takeIf { !it.isPhrase }?.let { here ->
                        Text(
                            text = stringResource(Res.string.lookup_here),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = Space.md),
                        )
                        Text(
                            text = here.translation,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = Space.xs),
                        )
                        Text(
                            text = stringResource(Res.string.lookup_here_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.xs),
                        )
                    }
                    Text(
                        text = stringResource(
                            Res.string.lookup_location,
                            lookup.pageIndex + 1,
                            lookup.lineNumber,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.md),
                    )
                }
            }


            // Shown alongside a successful translation too, since the stub
            // message is delivered through the same field.
            if (lookup.status == LookupStatus.READY && lookup.error != null) {
                Text(
                    text = lookup.error.resolved(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.md),
                )
            }

            // ---- The dictionary, once it has been asked for ------------------
            if (lookup.dictionary.status != DictionaryStatus.CLOSED) {
                DictionaryCard(
                    entry = lookup.dictionary,
                    selected = lookup.text,
                    modifier = Modifier.padding(top = Space.lg),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.md),
                modifier = Modifier.padding(top = Space.xl),
            ) {
                // The filled button last, with the quieter ones before it.
                // It used to sit in the middle of the row, so the eye landed on
                // "Done" on its way to the thing it was actually looking for.
                if (lookup.canRetryOnAnyNetwork) {
                    OutlinedButton(onClick = onRetryOnAnyNetwork) {
                        Text(stringResource(Res.string.lookup_use_mobile_data))
                    }
                }
                if (lookup.dictionary.status == DictionaryStatus.CLOSED) {
                    OutlinedButton(onClick = onShowDictionary) {
                        Text(stringResource(Res.string.lookup_dictionary))
                    }
                }
                Button(onClick = onDismiss) { Text(stringResource(Res.string.lookup_done)) }
            }
        }
    }
}

/**
 * A floor under the height of the sheet's answer area.
 *
 * Enough for two lines of the headline style the translation is set in, so the
 * sheet is already the size it will end up at before the answer arrives.
 */
private val ANSWER_MIN_HEIGHT = 72.dp

/**
 * Where a double tap lands on an unmagnified page.
 *
 * Two, not one of the zoom button's steps: a double tap is a coarse gesture and
 * should give an obvious result, while the button is for choosing.
 */
private const val DOUBLE_TAP_ZOOM = 2f

/**
 * How far a magnified page must be pushed past its edge before it turns.
 *
 * Roughly a third of a phone's width. Short enough that turning the page is a
 * single deliberate swipe, long enough that nudging the text sideways while
 * reading never turns it by accident.
 */
private const val PAGE_TURN_PUSH_PX = 320f

@Composable
private fun ColorSwatch(
    color: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        // 40dp rather than 28. Nine colours in a dropdown at 28dp each was a
        // row of targets smaller than a fingertip, spaced closer than one, and
        // picking the wrong one silently recolours every bookmark made after.
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(HighlightColors.compose(color))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(
                    Res.string.reader_colour_selected,
                    stringResource(HighlightColors.nameOf(color)),
                ),
                tint = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
