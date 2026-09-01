package com.david.llegeix.ui.reader

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
                            onLongPress = { x, y, w, h ->
                                viewModel.onWordPressed(index, x, y, w, h)
                            },
                            lookupHighlight = state.lookup
                                ?.takeIf { it.pageIndex == index }
                                ?.boundsPx,
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
            onExplainMore = viewModel::onExplainMore,
            onRetryOnAnyNetwork = viewModel::onRetryOnAnyNetwork,
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
    onLongPress: (xPx: Float, yPx: Float, widthPx: Int, heightPx: Int) -> Unit,
    lookupHighlight: RectF?,
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
                    .pointerInput(index) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            val next = (zoom * gestureZoom)
                                .coerceIn(ReaderViewModel.MIN_ZOOM, ReaderViewModel.MAX_ZOOM)
                            onZoomChanged(next)
                            if (next > 1.01f) {
                                // Keep the page from being dragged off screen.
                                // Horizontally it grows both ways from the
                                // centre; vertically it only grows downward,
                                // because the top edge is pinned.
                                val maxX = size.width * (next - 1f) / 2f
                                val minY = -size.height * (next - 1f)
                                offset = Offset(
                                    (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    (offset.y + pan.y).coerceIn(minY, 0f),
                                )
                            }
                        }
                    }
                    .pointerInput(index, rendered, drawnSize) {
                        detectTapGestures(
                            onDoubleTap = {
                                onZoomChanged(if (zoom > 1.01f) 1f else 2f)
                            },
                            onLongPress = { tap: Offset ->
                                if (drawnSize.width > 0 && drawnSize.height > 0) {
                                    val scaleX = rendered.width.toFloat() / drawnSize.width
                                    val scaleY = rendered.height.toFloat() / drawnSize.height
                                    onLongPress(
                                        tap.x * scaleX,
                                        tap.y * scaleY,
                                        rendered.width,
                                        rendered.height,
                                    )
                                }
                            },
                        )
                    }
                    .drawWithContent {
                        drawContent()
                        if (drawnSize.width <= 0) return@drawWithContent
                        val scaleX = size.width / rendered.width
                        val scaleY = size.height / rendered.height

                        lookupHighlight?.let { box ->
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

/**
 * Shows the pressed word and its translation.
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
    onExplainMore: () -> Unit,
    onRetryOnAnyNetwork: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.xl)
                .padding(bottom = Space.xxl),
        ) {
            Text(
                text = stringResource(R.string.lookup_direction),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = lookup.word,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = Space.xs),
            )

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
                modifier = Modifier.padding(top = Space.xxl),
            ) {
                if (lookup.canRetryOnAnyNetwork) {
                    OutlinedButton(onClick = onRetryOnAnyNetwork) {
                        Text(stringResource(R.string.lookup_use_mobile_data))
                    }
                } else {
                    OutlinedButton(onClick = onExplainMore) {
                        Text(stringResource(R.string.lookup_explain_more))
                    }
                }
                Button(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
            }
        }
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
