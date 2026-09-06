package com.david.llegeix.ui.exams

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.HighlightColors
import com.david.llegeix.ui.common.MenuIcon
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.common.resolved
import kotlinx.coroutines.launch

/**
 * One sitting of an exam, open and being written on.
 *
 * The layout is a sandwich and the order is deliberate: the recordings on top,
 * where a listening exercise can be started without leaving the question; the
 * page in the middle taking every pixel that is left; the tools at the bottom,
 * under the thumb.
 *
 * The one interaction worth explaining is why the pager stops taking swipes.
 * A finger on a page can mean "turn this" or "write here", and it cannot mean
 * both — with both live, the first downstroke of a letter is read as a swipe
 * and the page leaves under the pen. So the reader picks a tool first, and in
 * any tool but View the page is held still and the arrows in the bar turn it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamWorkspaceScreen(
    attemptId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExamWorkspaceViewModel = viewModel(
        factory = ExamWorkspaceViewModel.factory(attemptId),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val marks by viewModel.marks.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val tickBoxes by viewModel.tickBoxes.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var menuOpen by remember { mutableStateOf(false) }
    var clearingPage by remember { mutableStateOf(false) }
    var typingAt by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    // CreateDocument, so the reader says where their copy goes. The app never
    // decides that for them: this is their work leaving the app, and it should
    // land somewhere they chose and can find again.
    val exportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(EXPORT_MIME),
    ) { uri -> if (uri != null) viewModel.onExportTo(uri) }

    val messageText = message?.resolved()
    LaunchedEffect(messageText) {
        val text = messageText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onMessageShown()
    }

    if (state.error) {
        EmptyState(
            title = stringResource(R.string.exam_open_failed_title),
            body = stringResource(R.string.exam_open_failed_body),
            icon = painterResource(R.drawable.ic_exam),
            modifier = modifier,
            primaryAction = {
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            },
        )
        return
    }

    // Nothing is composed until the sitting is open, and that is load-bearing
    // rather than cosmetic: rememberPagerState reads initialPage once, on first
    // composition, and the paper is opened asynchronously. Composing the pager
    // first meant it always captured page zero, and "come back to where you
    // were" — the whole reason an attempt remembers its page — never worked.
    if (state.isOpening) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val pageCount = state.visiblePageCount
    val pagerState = rememberPagerState(
        initialPage = state.initialPage,
        pageCount = { pageCount.coerceAtLeast(1) },
    )

    // The pager is the authority on which page is showing; the ViewModel
    // follows it, so a swipe and the arrows cannot disagree.
    LaunchedEffect(pagerState.currentPage) {
        viewModel.onPageChanged(pagerState.currentPage)
    }
    // Flipping to the answer sheet lands on the matching page, clamped.
    LaunchedEffect(state.showAnswerKey) {
        if (pagerState.currentPage != state.currentPage) {
            pagerState.scrollToPage(state.currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                expandedHeight = Space.topBar,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = state.exam?.title.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        state.attempt?.let { attempt ->
                            Text(
                                text = stringResource(
                                    R.string.exams_attempt_label,
                                    attempt.label,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (state.hasAnswerKey) {
                        IconButton(onClick = viewModel::onToggleAnswerKey) {
                            Icon(
                                painter = painterResource(R.drawable.ic_key),
                                contentDescription = stringResource(
                                    if (state.showAnswerKey) {
                                        R.string.exam_show_work
                                    } else {
                                        R.string.exam_show_key
                                    },
                                ),
                                tint = if (state.showAnswerKey) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more),
                                contentDescription = stringResource(
                                    R.string.exams_more,
                                    state.exam?.title.orEmpty(),
                                ),
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (state.isFinished) {
                                                R.string.exam_unfinish
                                            } else {
                                                R.string.exam_finish
                                            },
                                        ),
                                    )
                                },
                                leadingIcon = { MenuIcon(Icons.Default.Check) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.onToggleFinished()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.exam_export)) },
                                leadingIcon = {
                                    MenuIcon(painterResource(R.drawable.ic_export))
                                },
                                enabled = !state.isExporting,
                                onClick = {
                                    menuOpen = false
                                    exportPicker.launch(viewModel.suggestedExportName())
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.exam_clear_page)) },
                                leadingIcon = { MenuIcon(Icons.Default.Delete) },
                                onClick = {
                                    menuOpen = false
                                    clearingPage = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (state.isExporting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = Space.screen, vertical = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.exam_exporting),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            ExamAudioBar(tracks = state.audio, fileOf = viewModel::fileFor)

            if (state.showAnswerKey) {
                // Said out loud rather than left to be inferred from a tinted
                // icon. Somebody who tries to write on the answer sheet and
                // finds nothing happening deserves to know why.
                Text(
                    text = stringResource(R.string.exam_key_banner),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = Space.screen, vertical = Space.xs),
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                // Held still whenever the finger is writing. See the class note.
                userScrollEnabled = !state.tool.takesTheFinger || state.showAnswerKey,
                beyondViewportPageCount = 1,
            ) { page ->
                ExamPage(
                    index = page,
                    zoom = state.zoom,
                    tool = state.tool,
                    inkColor = state.inkColor,
                    highlightColor = state.highlightColor,
                    strokeWidth = state.strokeWidth,
                    marks = if (page == state.currentPage) marks else emptyList(),
                    tickBoxes = if (page == state.currentPage) tickBoxes else emptyList(),
                    renderKey = state.showAnswerKey,
                    canWrite = state.canWrite && page == state.currentPage,
                    render = viewModel::renderPage,
                    onStrokeFinished = viewModel::onStrokeFinished,
                    onTapWithText = { x, y -> typingAt = x to y },
                    onTick = viewModel::onTickAt,
                    onErase = viewModel::onErase,
                    onZoomChanged = viewModel::onZoomChanged,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            ExamToolbar(
                state = state,
                page = pagerState.currentPage,
                pageCount = pageCount,
                onToolChosen = viewModel::onToolChosen,
                onInkColorChosen = viewModel::onInkColorChosen,
                onHighlightColorChosen = viewModel::onHighlightColorChosen,
                onStrokeWidthChosen = viewModel::onStrokeWidthChosen,
                onUndo = viewModel::onUndo,
                onStep = { delta ->
                    scope.launch {
                        pagerState.animateScrollToPage(
                            (pagerState.currentPage + delta)
                                .coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
                        )
                    }
                },
            )
        }
    }

    typingAt?.let { (x, y) ->
        TypeAnswerDialog(
            onDismiss = { typingAt = null },
            onConfirm = { text ->
                viewModel.onTextPlaced(x, y, text)
                typingAt = null
            },
        )
    }

    if (clearingPage) {
        AlertDialog(
            onDismissRequest = { clearingPage = false },
            title = { Text(stringResource(R.string.exam_clear_page_title)) },
            text = { Text(stringResource(R.string.exam_clear_page_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onClearPage()
                        clearingPage = false
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { clearingPage = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * The tools, under the thumb.
 *
 * Everything that changes what the finger does is on one row, and what that
 * tool needs — ink, thickness — appears on a second row underneath only when
 * it applies. A bar that showed pen colours while the eraser was selected
 * would be offering settings for a tool that is not running.
 */
@Composable
private fun ExamToolbar(
    state: WorkspaceState,
    page: Int,
    pageCount: Int,
    onToolChosen: (ExamTool) -> Unit,
    onInkColorChosen: (Int) -> Unit,
    onHighlightColorChosen: (Int) -> Unit,
    onStrokeWidthChosen: (Float) -> Unit,
    onUndo: () -> Unit,
    onStep: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        // The page counter, with arrows that are the only way to turn a page
        // while a tool has the finger. Always present so their position never
        // moves, and disabled rather than hidden at the ends.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onStep(-1) }, enabled = page > 0) {
                Icon(
                    Icons.Default.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.exam_previous_page),
                )
            }
            Text(
                text = stringResource(R.string.exam_page_of, page + 1, pageCount.coerceAtLeast(1)),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { onStep(1) }, enabled = page < pageCount - 1) {
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.exam_next_page),
                )
            }
        }

        if (!state.showAnswerKey) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Space.sm, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExamTool.entries.forEach { tool ->
                    val selected = state.tool == tool
                    IconButton(
                        onClick = { onToolChosen(tool) },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    Color.Transparent
                                },
                            ),
                    ) {
                        Icon(
                            painter = painterResource(tool.iconRes),
                            contentDescription = stringResource(tool.labelRes),
                            tint = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f))
                IconButton(onClick = onUndo) {
                    Icon(
                        painter = painterResource(R.drawable.ic_undo),
                        contentDescription = stringResource(R.string.exam_undo),
                    )
                }
            }

            when (state.tool) {
                ExamTool.PEN, ExamTool.TEXT, ExamTool.TICK -> InkRow(
                    colors = ExamInk.palette,
                    nameOf = ExamInk::nameOf,
                    selectedColor = state.inkColor,
                    onColorChosen = onInkColorChosen,
                    widths = if (state.tool == ExamTool.PEN) StrokeWidths.pen else emptyList(),
                    selectedWidth = state.strokeWidth,
                    onWidthChosen = onStrokeWidthChosen,
                )

                ExamTool.HIGHLIGHTER -> InkRow(
                    colors = HighlightColors.palette,
                    nameOf = HighlightColors::nameOf,
                    selectedColor = state.highlightColor,
                    onColorChosen = onHighlightColorChosen,
                    widths = emptyList(),
                    selectedWidth = state.strokeWidth,
                    onWidthChosen = onStrokeWidthChosen,
                )

                ExamTool.VIEW, ExamTool.ERASER -> Unit
            }
        }
    }
}

@Composable
private fun InkRow(
    colors: List<Int>,
    /** How to name a colour aloud, since the swatches carry no text. */
    nameOf: (Int) -> Int,
    selectedColor: Int,
    onColorChosen: (Int) -> Unit,
    widths: List<Float>,
    selectedWidth: Float,
    onWidthChosen: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Space.md, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        colors.forEach { argb ->
            val selected = argb == selectedColor
            val colorName = stringResource(nameOf(argb))
            Box(
                modifier = Modifier
                    .semantics {
                        contentDescription = colorName
                        this.selected = selected
                    }
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    )
                    .clickable { onColorChosen(argb) },
            )
        }

        if (widths.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(width = 1.dp, height = 24.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            widths.forEach { width ->
                val selected = width == selectedWidth
                val widthName = stringResource(
                    when (width) {
                        StrokeWidths.FINE -> R.string.exam_stroke_fine
                        StrokeWidths.THICK -> R.string.exam_stroke_thick
                        else -> R.string.exam_stroke_medium
                    },
                )
                Box(
                    modifier = Modifier
                        .semantics {
                            contentDescription = widthName
                            this.selected = selected
                        }
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable { onWidthChosen(width) },
                    contentAlignment = Alignment.Center,
                ) {
                    // The nib drawn at its own relative size, so the choice is
                    // made by looking rather than by reading a label.
                    Box(
                        modifier = Modifier
                            .size((width * NIB_PREVIEW_SCALE).dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface),
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeAnswerDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exam_text_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.exam_text_hint)) },
                singleLine = false,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.exam_text_place)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * How much to magnify a nib fraction to make a visible dot.
 *
 * The widths are fractions of a page a thousand-odd pixels wide, so they are
 * tiny numbers; this turns them into something between 2dp and 8dp, which reads
 * as a scale of thicknesses at a glance.
 */
private const val NIB_PREVIEW_SCALE = 1000f

/**
 * The type offered to the file picker for an export.
 *
 * A PDF, because that is what comes out: the paper as it was, with the reader's
 * own marks over it, in the one format every phone, teacher and printer can
 * open.
 */
private const val EXPORT_MIME = "application/pdf"
