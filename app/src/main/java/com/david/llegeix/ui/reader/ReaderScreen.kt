package com.david.llegeix.ui.reader

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.pdf.DEFAULT_PAGE_ASPECT_RATIO
import com.david.llegeix.pdf.PdfMatch
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.HighlightColors
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.common.resolved
import kotlin.math.roundToInt

/**
 * Inverts a rendered page: light text on a dark ground, without touching the
 * app's own theme.
 *
 * A plain negative rather than anything cleverer. It keeps black text and white
 * paper readable, which is the case that matters, and a diagram inverted is
 * still a legible diagram.
 */
private val InvertFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

/** The gutter colour around an inverted page. */
private val InvertedSurround = Color(0xFF0E0E0E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    uriString: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetPage: Int? = null,
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
    val pagerState = rememberPagerState(pageCount = { state.pageCount })

    LaunchedEffect(pagerState.currentPage) {
        viewModel.onPageChanged(pagerState.currentPage)
    }

    // Search results drive the pager rather than the other way round.
    LaunchedEffect(Unit) {
        viewModel.pageJumps.collect { page ->
            if (page in 0 until pagerState.pageCount) pagerState.scrollToPage(page)
        }
    }

    // Jump to the remembered page once, after the document reports its length.
    // rememberSaveable so a rotation does not yank the reader back to the
    // resume point after the user has already paged away from it.
    var hasRestoredPosition by rememberSaveable(uriString) { mutableStateOf(false) }
    LaunchedEffect(state.pageCount, state.initialPage) {
        if (state.pageCount > 0 && !hasRestoredPosition) {
            hasRestoredPosition = true
            if (state.initialPage in 0 until state.pageCount) {
                pagerState.scrollToPage(state.initialPage)
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val lookupHint = state.lookupHint?.resolved()
    LaunchedEffect(lookupHint) {
        val hint = lookupHint ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(hint)
        viewModel.onLookupHintShown()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                )
            } else {
                ReaderBar(
                    title = state.displayTitle,
                    isBookmarked = state.isCurrentPageBookmarked,
                    highlightColor = state.highlightColor,
                    invertPages = state.invertPages,
                    canUseTools = state.pageCount > 0,
                    onBack = onBack,
                    onFind = viewModel::onOpenSearch,
                    onZoom = viewModel::onCycleZoom,
                    onInvert = viewModel::onToggleInvertPages,
                    onToggleBookmark = viewModel::onToggleBookmark,
                    onColorChosen = viewModel::onHighlightColorChosen,
                )
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
                    if (state.invertPages) {
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
                    title = stringResource(R.string.reader_open_failed_title),
                    body = state.error?.resolved()
                        ?: stringResource(R.string.reader_open_failed_body),
                )

                state.pageCount == 0 -> EmptyState(
                    title = stringResource(R.string.reader_no_pages_title),
                    body = stringResource(R.string.reader_no_pages_body),
                )

                else -> {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        // One page either side, so a swipe lands on a rendered
                        // page instead of a placeholder.
                        beyondViewportPageCount = 1,
                        // A drag while zoomed in has to pan the page, not turn
                        // it; otherwise the magnified view is unusable.
                        userScrollEnabled = !state.isZoomed,
                    ) { index ->
                        PdfPage(
                            index = index,
                            zoom = state.zoom,
                            invert = state.invertPages,
                            render = viewModel::renderPage,
                            // Passed in rather than read inside, so stepping to
                            // the next match re-runs the effect that fetches the
                            // rectangles instead of leaving the old ones drawn.
                            searchMatch = state.search.current?.takeIf {
                                it.pageIndex == index
                            },
                            highlights = { widthPx, heightPx ->
                                viewModel.matchHighlights(index, widthPx, heightPx)
                            },
                            onZoomChanged = viewModel::onZoomChanged,
                            onSelect = { x1, y1, x2, y2, w, h ->
                                viewModel.onSelection(index, x1, y1, x2, y2, w, h)
                            },
                            lookupHighlights = state.lookup
                                ?.takeIf { it.pageIndex == index }
                                ?.boundsPx
                                .orEmpty(),
                            highlightColor = state.highlightColor,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    PagePill(
                        page = state.currentPage + 1,
                        pageCount = state.pageCount,
                        zoom = state.zoom,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = Space.lg),
                    )
                }
            }
        }
    }

    state.lookup?.let { lookup ->
        WordLookupSheet(
            lookup = lookup,
            onDismiss = viewModel::onDismissLookup,
            onRetryOnAnyNetwork = viewModel::onRetryOnAnyNetwork,
            onToggleSaved = viewModel::onToggleWordBookmark,
        )
    }
}

/**
 * The reader's only chrome: back, the title, and the four things you actually
 * do while reading.
 *
 * The highlight colour lives on a long press of the bookmark button. It is a
 * once-a-document choice, and giving it a fifth permanent slot would have cost
 * the title what little room it has.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderBar(
    title: String,
    isBookmarked: Boolean,
    highlightColor: Int,
    invertPages: Boolean,
    canUseTools: Boolean,
    onBack: () -> Unit,
    onFind: () -> Unit,
    onZoom: () -> Unit,
    onInvert: () -> Unit,
    onToggleBookmark: () -> Unit,
    onColorChosen: (Int) -> Unit,
) {
    var colorMenuOpen by remember { mutableStateOf(false) }

    TopAppBar(

        expandedHeight = Space.topBar,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.reader_back),
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
                    contentDescription = stringResource(R.string.reader_find),
                )
            }
            IconButton(onClick = onZoom, enabled = canUseTools) {
                Icon(
                    painter = painterResource(R.drawable.ic_zoom_in),
                    contentDescription = stringResource(R.string.reader_zoom),
                )
            }
            IconButton(onClick = onInvert, enabled = canUseTools) {
                Icon(
                    painter = painterResource(R.drawable.ic_invert_colors),
                    contentDescription = stringResource(
                        if (invertPages) R.string.reader_invert_on else R.string.reader_invert_off,
                    ),
                    tint = if (invertPages) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
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
                        text = stringResource(R.string.reader_highlight_colour),
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
                    R.string.reader_remove_page_bookmark
                } else {
                    R.string.reader_add_page_bookmark
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

/** Replaces the reader bar while a search is running. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    search: SearchState,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column {
        TopAppBar(
            expandedHeight = Space.topBar,
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.reader_find_close),
                    )
                }
            },
            title = {
                OutlinedTextField(
                    value = search.query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(R.string.reader_find_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            },
            actions = {
                Text(
                    text = when {
                        search.isEmptyResult -> stringResource(R.string.reader_find_none)
                        search.matches.isEmpty() -> ""
                        else -> stringResource(
                            R.string.reader_find_position,
                            search.currentIndex + 1,
                            search.matches.size,
                        )
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                IconButton(onClick = onPrevious, enabled = search.matches.isNotEmpty()) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.reader_find_previous),
                    )
                }
                IconButton(onClick = onNext, enabled = search.matches.isNotEmpty()) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.reader_find_next),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )
        // Progress rather than a spinner: a long document takes a moment, and
        // matches keep arriving while it does.
        AnimatedVisibility(visible = search.isSearching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The floating page counter, in place of a whole bar. */
@Composable
private fun PagePill(
    page: Int,
    pageCount: Int,
    zoom: Float,
    modifier: Modifier = Modifier,
) {
    val position = stringResource(R.string.reader_page_position, page, pageCount)
    // One decimal, and no trailing ".0" on the whole steps.
    val rounded = (zoom * 10f).roundToInt() / 10f
    val zoomLabel = stringResource(
        R.string.reader_zoom_level,
        if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString(),
    )
    val label = if (zoom > 1.01f) "$position  ·  $zoomLabel" else position
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
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
 * One page of the document. Rendering is keyed on the measured width, so a
 * rotation re-renders at the new resolution rather than upscaling a stale
 * bitmap.
 */
@Composable
private fun PdfPage(
    index: Int,
    zoom: Float,
    invert: Boolean,
    render: suspend (index: Int, widthPx: Int) -> Bitmap?,
    searchMatch: PdfMatch?,
    highlights: suspend (widthPx: Int, heightPx: Int) -> List<RectF>,
    onZoomChanged: (Float) -> Unit,
    onSelect: (
        startXPx: Float, startYPx: Float,
        endXPx: Float, endYPx: Float,
        widthPx: Int, heightPx: Int,
    ) -> Unit,
    lookupHighlights: List<RectF>,
    highlightColor: Int,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        // Without this a magnified page paints straight over the pages either
        // side of it in the pager, so page 1 at 2x shows a slice of page 2.
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        var bitmap by remember(index, widthPx) { mutableStateOf<Bitmap?>(null) }
        var offset by remember(index) { mutableStateOf(Offset.Zero) }
        // The gesture handlers outlive the composition that started them, so
        // they have to read the zoom through a holder rather than capture it —
        // a captured value goes stale the moment the first pinch changes it.
        val currentZoom by rememberUpdatedState(zoom)
        val isZoomed = zoom > 1.01f

        LaunchedEffect(index, widthPx) {
            bitmap = render(index, widthPx)
        }

        // Panning only makes sense while magnified; snapping back on the way out
        // avoids leaving the page parked off-centre.
        LaunchedEffect(zoom) {
            if (zoom <= 1.01f) offset = Offset.Zero
        }

        val rendered = bitmap
        if (rendered != null) {
            // The composable is given the bitmap's exact aspect ratio, so a touch
            // offset maps to bitmap pixels by a single scale factor with no
            // letterboxing to compensate for.
            var drawnSize by remember { mutableStateOf(IntSize.Zero) }
            var searchRects by remember(index, rendered) { mutableStateOf(emptyList<RectF>()) }

            LaunchedEffect(index, rendered, drawnSize, searchMatch) {
                searchRects = if (searchMatch != null && drawnSize.width > 0) {
                    highlights(rendered.width, rendered.height)
                } else {
                    emptyList()
                }
            }

            Image(
                bitmap = rendered.asImageBitmap(),
                contentDescription = stringResource(
                    R.string.reader_page_content_description,
                    index + 1,
                ),
                contentScale = ContentScale.Fit,
                colorFilter = if (invert) InvertFilter else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.sm, vertical = Space.sm)
                    .aspectRatio(rendered.width.toFloat() / rendered.height.toFloat())
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        // Anchored at the top, not the centre. A page's text
                        // starts at the top, so centre-anchored zoom throws you
                        // into the middle of the margin and looks broken.
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        translationX = offset.x
                        translationY = offset.y
                    }
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
                            Modifier.pointerInput(index, zoom) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    // Horizontally the page grows both ways from
                                    // the centre; vertically only downward,
                                    // because the top edge is pinned.
                                    val maxX = size.width * (zoom - 1f) / 2f
                                    val minY = -size.height * (zoom - 1f)
                                    offset = Offset(
                                        (offset.x + drag.x).coerceIn(-maxX, maxX),
                                        (offset.y + drag.y).coerceIn(minY, 0f),
                                    )
                                }
                            }
                        } else {
                            Modifier
                        },
                    )
                    .pointerInput(index, rendered, drawnSize) {
                        detectTapGestures(
                            onDoubleTap = {
                                onZoomChanged(if (zoom > 1.01f) 1f else 2f)
                            },
                        )
                    }
                    // Press and hold picks a word; keep dragging and the
                    // selection grows to a phrase. One gesture, so there is
                    // nothing extra to learn to select more than one word.
                    .pointerInput(index, rendered, drawnSize) {
                        var anchor = Offset.Zero
                        detectDragGesturesAfterLongPress(
                            onDragStart = { start ->
                                anchor = start
                                emitSelection(start, start, rendered, drawnSize, onSelect)
                            },
                            onDrag = { change, _ ->
                                emitSelection(
                                    anchor,
                                    change.position,
                                    rendered,
                                    drawnSize,
                                    onSelect,
                                )
                            },
                        )
                    }
                    .drawWithContent {
                        drawContent()
                        if (drawnSize.width <= 0) return@drawWithContent
                        val scaleX = size.width / rendered.width
                        val scaleY = size.height / rendered.height

                        lookupHighlights.forEach { box ->
                            drawRect(
                                color = HighlightColors.compose(highlightColor).copy(alpha = 0.4f),
                                topLeft = Offset(box.left * scaleX, box.top * scaleY),
                                size = Size(box.width() * scaleX, box.height() * scaleY),
                            )
                        }
                        searchRects.forEach { box ->
                            drawRect(
                                color = HighlightColors.compose(HighlightColors.Blue)
                                    .copy(alpha = 0.45f),
                                topLeft = Offset(box.left * scaleX, box.top * scaleY),
                                size = Size(box.width() * scaleX, box.height() * scaleY),
                            )
                        }
                    },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.sm, vertical = Space.sm)
                    .aspectRatio(DEFAULT_PAGE_ASPECT_RATIO)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

/** Maps two points in the drawn page to bitmap pixels and reports the range. */
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
    onSelect(
        start.x * scaleX,
        start.y * scaleY,
        end.x * scaleX,
        end.y * scaleY,
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
    onDismiss: () -> Unit,
    onRetryOnAnyNetwork: () -> Unit,
    onToggleSaved: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Space.xl)
                .padding(bottom = Space.xxl),
        ) {
            // ---- The word itself, and the save toggle -----------------------
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.lookup_direction),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = lookup.text,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                    if (lookup.ipa.isNotBlank()) {
                        IpaLine(
                            ipa = lookup.ipa,
                            isApproximate = lookup.isIpaApproximate,
                            modifier = Modifier.padding(top = Space.xs),
                        )
                    }
                }
                IconButton(onClick = onToggleSaved) {
                    Icon(
                        imageVector = if (lookup.isSaved) {
                            Icons.Filled.Star
                        } else {
                            Icons.Outlined.Star
                        },
                        contentDescription = stringResource(
                            if (lookup.isSaved) {
                                R.string.lookup_unsave_word
                            } else {
                                R.string.lookup_save_word
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

            // ---- The answer -------------------------------------------------
            Box(modifier = Modifier.padding(top = Space.xl)) {
                when (lookup.status) {
                    LookupStatus.LOOKING_UP -> Text(
                        text = stringResource(R.string.lookup_translating),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    LookupStatus.DOWNLOADING_MODEL -> Column {
                        Text(
                            text = stringResource(R.string.lookup_downloading_title),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.lookup_downloading_body),
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
                            ?: stringResource(R.string.lookup_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // ---- Word by word, only when there is more than one -------------
            if (lookup.isPhrase && lookup.status == LookupStatus.READY) {
                DetailCard(
                    title = stringResource(R.string.lookup_word_by_word),
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
                    title = stringResource(R.string.lookup_in_context),
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
                    Text(
                        text = stringResource(
                            R.string.lookup_location,
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.md),
                modifier = Modifier.padding(top = Space.xl),
            ) {
                if (lookup.canRetryOnAnyNetwork) {
                    OutlinedButton(onClick = onRetryOnAnyNetwork) {
                        Text(stringResource(R.string.lookup_use_mobile_data))
                    }
                }
                Button(onClick = onDismiss) { Text(stringResource(R.string.lookup_done)) }
            }
        }
    }
}

/**
 * The pronunciation line.
 *
 * A trailing marker when the transcription had to guess: Catalan spelling does
 * not record whether a stressed e or o is close or open, so saying so is more
 * useful than quietly presenting a guess as fact.
 */
@Composable
private fun IpaLine(ipa: String, isApproximate: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "[$ipa]",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (isApproximate) {
            Text(
                text = stringResource(R.string.lookup_ipa_approximate),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.sm),
            )
        }
    }
}

/** A quiet block of supporting detail under the answer. */
@Composable
private fun DetailCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(Space.lg),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = Space.sm),
        )
        content()
    }
}

@Composable
private fun ColorSwatch(
    color: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(HighlightColors.compose(color))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(
                    R.string.reader_colour_selected,
                    stringResource(HighlightColors.nameOf(color)),
                ),
                tint = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
