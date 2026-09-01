package com.david.llegeix.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.draw.drawWithContent
import com.david.llegeix.ui.common.HighlightColors as Highlights
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.david.llegeix.ui.common.HighlightColors
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.BoxWithConstraints
import com.david.llegeix.pdf.DEFAULT_PAGE_ASPECT_RATIO
import com.david.llegeix.R
import com.david.llegeix.ui.common.resolved
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.Space
import android.graphics.Bitmap
import android.graphics.RectF

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
        topBar = {
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
                    Column {
                        Text(
                            text = state.displayTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.pageCount > 0) {
                            Text(
                                text = stringResource(
                                    R.string.reader_page_of,
                                    state.currentPage + 1,
                                    state.pageCount,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.pageCount > 0) {
                ReaderControls(
                    isBookmarked = state.isCurrentPageBookmarked,
                    highlightColor = state.highlightColor,
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
                .background(MaterialTheme.colorScheme.surfaceVariant),
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

                else -> HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    // One page either side, so a swipe lands on a rendered page
                    // instead of a placeholder.
                    beyondViewportPageCount = 1,
                ) { index ->
                    PdfPage(
                        index = index,
                        render = viewModel::renderPage,
                        onLongPress = { x, y, w, h ->
                            viewModel.onWordPressed(index, x, y, w, h)
                        },
                        highlight = state.lookup
                            ?.takeIf { it.pageIndex == index }
                            ?.boundsPx,
                        highlightColor = state.highlightColor,
                        modifier = Modifier.fillMaxSize(),
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
 * One page of the document. Rendering is keyed on the measured width, so a
 * rotation re-renders at the new resolution rather than upscaling a stale
 * bitmap.
 */
@Composable
private fun PdfPage(
    index: Int,
    render: suspend (index: Int, widthPx: Int) -> Bitmap?,
    onLongPress: (xPx: Float, yPx: Float, widthPx: Int, heightPx: Int) -> Unit,
    highlight: RectF?,
    highlightColor: Int,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        var bitmap by remember(index, widthPx) { mutableStateOf<Bitmap?>(null) }

        LaunchedEffect(index, widthPx) {
            bitmap = render(index, widthPx)
        }

        val rendered = bitmap
        if (rendered != null) {
            // The composable is given the bitmap's exact aspect ratio, so a touch
            // offset maps to bitmap pixels by a single scale factor with no
            // letterboxing to compensate for.
            var drawnSize by remember { mutableStateOf(IntSize.Zero) }
            Image(
                bitmap = rendered.asImageBitmap(),
                contentDescription = stringResource(
                    R.string.reader_page_content_description,
                    index + 1,
                ),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .aspectRatio(rendered.width.toFloat() / rendered.height.toFloat())
                    .onSizeChanged { drawnSize = it }
                    .pointerInput(index, rendered, drawnSize) {
                        detectTapGestures(
                            onLongPress = { offset: Offset ->
                                if (drawnSize.width > 0 && drawnSize.height > 0) {
                                    val scaleX = rendered.width.toFloat() / drawnSize.width
                                    val scaleY = rendered.height.toFloat() / drawnSize.height
                                    onLongPress(
                                        offset.x * scaleX,
                                        offset.y * scaleY,
                                        rendered.width,
                                        rendered.height,
                                    )
                                }
                            },
                        )
                    }
                    .drawWithContent {
                        drawContent()
                        if (highlight != null && drawnSize.width > 0) {
                            val scaleX = size.width / rendered.width
                            val scaleY = size.height / rendered.height
                            drawRect(
                                color = Highlights.compose(highlightColor).copy(alpha = 0.4f),
                                topLeft = Offset(highlight.left * scaleX, highlight.top * scaleY),
                                size = androidx.compose.ui.geometry.Size(
                                    highlight.width() * scaleX,
                                    highlight.height() * scaleY,
                                ),
                            )
                        }
                    },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
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
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
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
                    modifier = Modifier.padding(top = 12.dp),
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

/**
 * Bookmark toggle and highlight-colour picker for the open document.
 *
 * The colour is stored per document; feature 6 will reuse it as the default for
 * a word highlight, and a bookmark can still override it individually.
 */
@Composable
private fun ReaderControls(
    isBookmarked: Boolean,
    highlightColor: Int,
    onToggleBookmark: () -> Unit,
    onColorChosen: (Int) -> Unit,
) {
    var colorMenuOpen by remember { mutableStateOf(false) }

    BottomAppBar {
        IconButton(onClick = onToggleBookmark) {
            Icon(
                imageVector = if (isBookmarked) {
                    Icons.Filled.Star
                } else {
                    Icons.Outlined.Star
                },
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

        Text(
            text = stringResource(
                if (isBookmarked) {
                    R.string.reader_page_bookmarked
                } else {
                    R.string.reader_bookmark_page
                },
            ),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 4.dp),
        )

        Box(modifier = Modifier.padding(start = 12.dp)) {
            ColorSwatch(
                color = highlightColor,
                selected = false,
                onClick = { colorMenuOpen = true },
            )
            DropdownMenu(
                expanded = colorMenuOpen,
                onDismissRequest = { colorMenuOpen = false },
            ) {
                DropdownMenuItem(
                    enabled = false,
                    text = { Text(stringResource(R.string.reader_highlight_colour)) },
                    onClick = {},
                )
                HighlightColors.palette.chunked(3).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
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
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    Color.Black.copy(alpha = 0.2f)
                },
                shape = CircleShape,
            )
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
