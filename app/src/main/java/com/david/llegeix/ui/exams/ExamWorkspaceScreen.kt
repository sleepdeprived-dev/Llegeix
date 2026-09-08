package com.david.llegeix.ui.exams

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.HighlightColors
import com.david.llegeix.ui.common.MenuIcon
import com.david.llegeix.ui.common.AppSnackbarHost
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.common.resolved
import kotlinx.coroutines.launch

/**
 * One sitting of an exam, open and being written on.
 *
 * ### The shape of the screen
 *
 * A sheet of paper on a desk, with a bar above it and a bar below, and every
 * control sorted into exactly one of the three by a single question — *what is
 * this about?*
 *
 * - **The bar above is about the paper.** Which part of it is on screen (the
 *   blank pages, the answer sheet), how it is lit, and where its recording is.
 *   These are the things a reader decides once and then leaves alone.
 * - **The desk in the middle is the page**, and nothing else: it sits on the
 *   dimmest surface the theme has, rounded, edged and lifted a little, so that
 *   it reads as a sheet rather than as the same darkness as everything round it.
 * - **The bar below is about working.** Where you are in the paper and how to
 *   take something back, then what your finger does. See [ExamToolbar] for why
 *   those are two strips and why neither ever changes height.
 *
 * The point of sorting them that way is not tidiness. It is that a reader who
 * has lost the thread can find any control by asking one question instead of
 * scanning eleven icons, and that nothing they are not using is competing with
 * the page for their attention. Whatever is only sometimes needed — the
 * recording, the ink palette — is one deliberate press away and invisible until
 * then.
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
    val focusManager = LocalFocusManager.current

    var menuOpen by remember { mutableStateOf(false) }
    var clearingPage by remember { mutableStateOf(false) }
    // The name the app gives the blank paper it makes, read here so both the
    // menu and the bar hand the same one down.
    val notesLabel = stringResource(R.string.exam_notes_name)

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

    // Going to the blank pages and coming back, animated rather than jumped:
    // the whole point of putting the essay paper inside the booklet is that
    // getting to it looks like turning pages, not like changing screen. Keyed on
    // the page count too, because a page just added is a page the pager does not
    // know about until the parts flow has come back round.
    val jumpTo by viewModel.jumpTo.collectAsStateWithLifecycle()
    LaunchedEffect(jumpTo, pageCount) {
        val target = jumpTo ?: return@LaunchedEffect
        if (target >= pageCount) return@LaunchedEffect
        pagerState.animateScrollToPage(target)
        viewModel.onJumpHandled()
        // Only once the pager has actually arrived. Asking for a cursor on a
        // page that is still scrolling into view puts the keyboard up over the
        // page the reader is leaving.
        viewModel.onArrivedOnNotes()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                expandedHeight = Space.topBar,
                // The same surface the bar at the bottom uses. The two are the
                // frame and the page is what is framed; leaving the top bar on
                // the plain background made it read as part of the paper's own
                // ground, with the title floating in it.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
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
                    // The other half of the booklet. One button, because it is
                    // one thought — *the other part of this paper* — and the
                    // reader is always on exactly one side of the join, so it
                    // is never ambiguous about which way it goes.
                    //
                    // Up here with the answer sheet rather than down in the
                    // bar, because the two are the same kind of thing: both
                    // say which part of this paper is on screen. The bar below
                    // is about pages within whatever that is.
                    if (!state.showAnswerKey) {
                        IconButton(onClick = { viewModel.onToggleNotes(notesLabel) }) {
                            Icon(
                                painter = painterResource(
                                    if (state.isOnNotes) {
                                        R.drawable.ic_exam
                                    } else {
                                        R.drawable.ic_text_field
                                    },
                                ),
                                contentDescription = stringResource(
                                    if (state.isOnNotes) {
                                        R.string.exam_back_to_paper
                                    } else {
                                        R.string.exam_go_to_notes
                                    },
                                ),
                                tint = if (state.isOnNotes) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    // How the page is lit. Not a tool — it changes nothing
                    // about what the finger does and it applies to the answer
                    // sheet as much as to the reader's own work — so it sits
                    // with the other things that say how this paper is being
                    // shown rather than in the row of pens.
                    IconButton(onClick = viewModel::onToggleDarkPage) {
                        Icon(
                            painter = painterResource(R.drawable.ic_contrast),
                            contentDescription = stringResource(
                                if (state.darkPage) {
                                    R.string.exam_light_page
                                } else {
                                    R.string.exam_dark_page
                                },
                            ),
                            tint = if (state.darkPage) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    // The way to the recording, and the way to put it away.
                    // In the bar rather than in the overflow menu: it is the
                    // control for a thing that is part of this paper, and the
                    // reader should be able to see that the paper has one
                    // without opening a menu to find out.
                    if (state.audio.isNotEmpty()) {
                        IconButton(onClick = viewModel::onToggleAudio) {
                            Icon(
                                painter = painterResource(R.drawable.ic_headphones),
                                contentDescription = stringResource(
                                    if (state.audioOpen) {
                                        R.string.exam_listen_hide
                                    } else {
                                        R.string.exam_listen_show
                                    },
                                ),
                                tint = if (state.audioOpen) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
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
                            // Also in the bar, as a plus beside the button that
                            // goes to the blank pages. Here too because the menu
                            // is where somebody looks for a thing they have not
                            // found yet, and the plus in the bar is only drawn
                            // once they are already on the blank pages.
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.exam_add_page)) },
                                leadingIcon = { MenuIcon(Icons.Default.Add) },
                                enabled = !state.showAnswerKey,
                                onClick = {
                                    menuOpen = false
                                    viewModel.onAddNotesPage(notesLabel)
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
        // imePadding, so the whole sandwich sits above the keyboard rather than
        // behind it. Without it the tools were under the keys and — much worse —
        // so was the bottom third of the page, which is where somebody writing
        // an essay spends most of their time: they were typing into a line they
        // could not see. The page itself scrolls inside what is left; see
        // [ExamPage].
        //
        // The navigation bar is declared consumed first because the screen is
        // already inside the padding the app's own scaffold applies for it.
        // Left unsaid, the keyboard's inset — which is measured from the bottom
        // of the window and therefore includes the navigation bar — would be
        // added on top of that padding a second time, and the bar would float a
        // finger's width above the keys.
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(WindowInsets.navigationBars)
                .imePadding(),
        ) {
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

            ExamAudioBar(
                tracks = state.audio,
                fileOf = viewModel::fileFor,
                expanded = state.audioOpen,
                onExpand = viewModel::onToggleAudio,
            )

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

            // Where the chrome above stops and the desk begins. In the light
            // theme the bar and the desk are four shades apart and would
            // otherwise run into one another.
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // A ground for the paper to sit on. The page used to be
                    // flush against the same near-black as everything else, so
                    // a darkened page and the bar under it were one continuous
                    // dark field with a sheet of writing somewhere in it. The
                    // dimmest surface in the scheme reads as the desk, and the
                    // page reads as a page.
                    .background(MaterialTheme.colorScheme.surfaceDim),
                // Held still whenever the finger is writing. See the class note.
                userScrollEnabled = !state.tool.takesTheFinger || state.showAnswerKey,
                beyondViewportPageCount = 1,
            ) { page ->
                val onThisPage = page == state.currentPage
                ExamPage(
                    index = page,
                    zoom = state.zoom,
                    tool = state.tool,
                    inkColor = state.inkColor,
                    highlightColor = state.highlightColor,
                    strokeWidth = state.strokeWidth,
                    marks = if (onThisPage) marks else emptyList(),
                    tickBoxes = if (onThisPage) tickBoxes else emptyList(),
                    // Both, because the darkened page is a different bitmap
                    // rather than the same one under a filter: flipping the
                    // switch has to send the page back to be drawn again.
                    renderKey = state.showAnswerKey to state.darkPage,
                    darkPage = state.darkPage,
                    canWrite = state.canWrite && onThisPage,
                    selectedText = if (onThisPage) {
                        marks.firstOrNull { it.id == state.selectedTextId }
                    } else {
                        null
                    },
                    editingText = state.editingText,
                    documentMode = onThisPage && state.isDocument,
                    render = viewModel::renderPage,
                    onStrokeFinished = viewModel::onStrokeFinished,
                    onCreateText = viewModel::onCreateText,
                    onSelectText = { viewModel.onSelectText(it?.id) },
                    onStartEditingText = viewModel::onEditSelectedText,
                    onDoneEditingText = viewModel::onFinishEditing,
                    onTextChanged = viewModel::onTextChanged,
                    onTextEdited = viewModel::onTextEdited,
                    onTextMoved = viewModel::onTextMoved,
                    onTextScaled = viewModel::onTextScaled,
                    onTextRotated = viewModel::onTextRotated,
                    onDeleteText = viewModel::onDeleteText,
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
                // Taking the cursor out of the field is what "done" means: the
                // editors close themselves when they lose focus, and the
                // keyboard goes down with it. Setting a flag instead would put
                // the tools back while the keys were still up.
                onDoneTyping = { focusManager.clearFocus() },
                onToolChosen = viewModel::onToolChosen,
                onInkColorChosen = viewModel::onInkColorChosen,
                onHighlightColorChosen = viewModel::onHighlightColorChosen,
                onStrokeWidthChosen = viewModel::onStrokeWidthChosen,
                onUndo = viewModel::onUndo,
                onRedo = viewModel::onRedo,
                onAddNotesPage = { viewModel.onAddNotesPage(notesLabel) },
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
 * The bottom of the screen, in two strips that never change height.
 *
 * ### Why two, and why fixed
 *
 * It was three, and the third came and went: the ink row appeared when a
 * writing tool was in hand and vanished when it was not, so choosing the eraser
 * made the page jump forty pixels taller and choosing the pen made it jump back.
 * On a screen built for people who lose their place easily, chrome that resizes
 * under a page is the worst kind of noise, because the thing that moves is the
 * thing being read.
 *
 * So there are two strips and they are always the same height, whatever is
 * selected:
 *
 * - **Where you are.** The page number, an arrow on each side of it, and the
 *   two history buttons behind a rule. Turning pages and taking a mark back are
 *   both "undo the last thing that happened", and neither has anything to do
 *   with what the finger is currently doing.
 * - **What your finger does.** The six tools, each with its name under it,
 *   dividing the width evenly so every one is always in the same place. Nothing
 *   scrolls: a control that has to be scrolled back to is a control that has
 *   moved, and these are pressed without looking.
 *
 * Ink lives on the tool rather than in a row of its own. The selected tool
 * carries a bar in the colour it writes in, and pressing it again opens the
 * palette. That is one more tap than a permanent row of swatches and it buys
 * the fixed height, a single palette instead of two that swap places, and a
 * colour that is legible at a glance rather than inferred from which of seven
 * circles has a ring round it.
 *
 * ### While the keyboard is up
 *
 * Both strips fold to one line: the page number, and Done. There is no tool
 * that can be used with a cursor in a field and no colour that can be chosen,
 * so everything else was standing between the keyboard and the words being
 * typed while doing nothing at all.
 */
@Composable
private fun ExamToolbar(
    state: WorkspaceState,
    page: Int,
    pageCount: Int,
    onDoneTyping: () -> Unit,
    onToolChosen: (ExamTool) -> Unit,
    onInkColorChosen: (Int) -> Unit,
    onHighlightColorChosen: (Int) -> Unit,
    onStrokeWidthChosen: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAddNotesPage: () -> Unit,
    onStep: (Int) -> Unit,
) {
    // Which tool has its palette open. Null almost always: it is opened by
    // pressing a tool that is already in hand, and closed by choosing or by
    // touching anywhere else.
    var paletteFor by remember { mutableStateOf<ExamTool?>(null) }
    LaunchedEffect(state.tool, state.editingText) { paletteFor = null }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        // A line where the paper stops and the tools start. The two are only a
        // few shades apart in the dark theme, and without it the bar read as
        // more page rather than as a different kind of thing.
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (state.editingText) {
            TypingStrip(
                page = page,
                pageCount = pageCount,
                onDoneTyping = onDoneTyping,
            )
            return@Column
        }

        PageStrip(
            state = state,
            page = page,
            pageCount = pageCount,
            onUndo = onUndo,
            onRedo = onRedo,
            onAddNotesPage = onAddNotesPage,
            onStep = onStep,
        )

        // Nothing is written on somebody else's answer sheet, so the tools are
        // not offered on it. This is the one place the bar does change height,
        // and it is right that it does: flipping to the key is a deliberate
        // change of what the screen is for, and a row of six greyed-out tools
        // would be six things to read and reject.
        if (state.showAnswerKey) return@Column

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.xs, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExamTool.entries.forEach { tool ->
                val selected = state.tool == tool
                val ink = tool.inkOf(state)
                Box(modifier = Modifier.weight(1f)) {
                    ToolSlot(
                        tool = tool,
                        selected = selected,
                        ink = ink,
                        onClick = {
                            // Pressing the tool already in hand is how its
                            // palette is asked for. Pressing any other one
                            // simply picks it up — a palette that opened on
                            // every change of tool would be a dialog between
                            // the reader and the page four times a minute.
                            if (selected && ink != null) {
                                paletteFor = tool
                            } else {
                                onToolChosen(tool)
                            }
                        },
                    )
                    if (ink != null) {
                        InkPalette(
                            expanded = paletteFor == tool,
                            tool = tool,
                            selectedColor = ink,
                            selectedWidth = state.strokeWidth,
                            onColorChosen = { argb ->
                                if (tool == ExamTool.HIGHLIGHTER) {
                                    onHighlightColorChosen(argb)
                                } else {
                                    onInkColorChosen(argb)
                                }
                                paletteFor = null
                            },
                            onWidthChosen = { width ->
                                onStrokeWidthChosen(width)
                                paletteFor = null
                            },
                            onDismiss = { paletteFor = null },
                        )
                    }
                }
            }
        }
    }
}

/** The colour a tool writes in, or null when it does not write in one. */
private fun ExamTool.inkOf(state: WorkspaceState): Int? = when (this) {
    ExamTool.PEN, ExamTool.TEXT, ExamTool.TICK -> state.inkColor
    ExamTool.HIGHLIGHTER -> state.highlightColor
    ExamTool.VIEW, ExamTool.ERASER -> null
}

/**
 * Where you are in the paper, and the two ways to take something back.
 *
 * The arrows are the only way to turn a page while a tool has the finger — see
 * the screen's own note on why the pager stops taking swipes — so they are
 * always drawn, and disabled rather than hidden at the ends so that their
 * position never moves.
 */
@Composable
private fun PageStrip(
    state: WorkspaceState,
    page: Int,
    pageCount: Int,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAddNotesPage: () -> Unit,
    onStep: (Int) -> Unit,
) {
    // Standing on the last of the blank pages, the arrow that would turn to the
    // next one makes it instead. It is the same thought in the same place —
    // "the page after this one" — and it puts the way to carry on writing under
    // the thumb that has just run out of room, rather than in a menu at the top
    // of the screen.
    val atTheEnd = page >= pageCount - 1
    val growsThePaper = state.isOnNotes && atTheEnd
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onStep(-1) }, enabled = page > 0) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.exam_previous_page),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = Space.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(
                    R.string.exam_page_of,
                    page + 1,
                    pageCount.coerceAtLeast(1),
                ),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
            // Which document, when the paper is made of several. Swiping off
            // the end of the reading paper and onto the listening one is
            // otherwise a silent change of subject.
            state.partOrdinal?.let { ordinal ->
                Text(
                    text = stringResource(
                        R.string.exam_part_of,
                        ordinal,
                        state.partTotal,
                        state.partName.orEmpty(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (growsThePaper) {
            IconButton(onClick = onAddNotesPage) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.exam_add_page_here),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            IconButton(onClick = { onStep(1) }, enabled = !atTheEnd) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.exam_next_page),
                )
            }
        }
        if (!state.showAnswerKey) {
            VerticalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .padding(horizontal = Space.xs)
                    .height(Space.xl),
            )
            IconButton(onClick = onUndo) {
                Icon(
                    painter = painterResource(R.drawable.ic_undo),
                    contentDescription = stringResource(R.string.exam_undo),
                )
            }
            // Disabled rather than hidden, so the pair never moves under the
            // thumb: undo and redo are pressed in quick succession, and a button
            // that appears between two presses is a button pressed by accident.
            IconButton(onClick = onRedo, enabled = state.canRedo) {
                Icon(
                    painter = painterResource(R.drawable.ic_redo),
                    contentDescription = stringResource(R.string.exam_redo),
                )
            }
        }
    }
}

/** The whole bar, while there is a cursor in a field: where you are, and out. */
@Composable
private fun TypingStrip(page: Int, pageCount: Int, onDoneTyping: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.lg, end = Space.sm, top = Space.xs, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                R.string.exam_page_of,
                page + 1,
                pageCount.coerceAtLeast(1),
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDoneTyping) {
            Text(stringResource(R.string.action_done))
        }
    }
}

/**
 * One tool: what it looks like, what it is called, and what colour it writes in.
 *
 * The name is under the icon and is not optional. Six glyphs in a row is six
 * small guesses to make before anything can be written, and the reader this app
 * is for is exactly the reader who should not have to make them. It costs
 * fourteen pixels of height, which is a fair price.
 *
 * The colour is a short bar under the icon rather than a tint on the icon
 * itself, because the ink is sometimes near-black and the pill it sits in is
 * dark: a pen drawn in its own ink would disappear on the very setting it most
 * needed to report. The bar keeps its space when there is no colour to show, so
 * that the six names stay on one line with each other.
 */
@Composable
private fun ToolSlot(
    tool: ExamTool,
    selected: Boolean,
    ink: Int?,
    onClick: () -> Unit,
) {
    val label = stringResource(tool.labelRes)
    val description = if (selected && ink != null) {
        stringResource(R.string.exam_choose_colour, label)
    } else {
        label
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(vertical = Space.sm)
            .semantics {
                contentDescription = description
                this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(tool.iconRes),
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(ToolGlyph),
        )
        Box(
            modifier = Modifier
                .padding(top = Space.xs)
                .size(width = InkBarWidth, height = InkBarHeight)
                .clip(CircleShape)
                .background(if (selected && ink != null) Color(ink) else Color.Transparent)
                // Ringed, because one of the four inks is very nearly black and
                // the pill it is shown in is very nearly black in the dark
                // theme. Without an edge, "you are writing in black" was drawn
                // as an empty space — the one reading it most needed to give.
                .then(
                    if (selected && ink != null) {
                        Modifier.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                .copy(alpha = InkBarRingAlpha),
                            shape = CircleShape,
                        )
                    } else {
                        Modifier
                    },
                ),
        )
        Text(
            text = label,
            // A point under labelSmall, because six names have to share the
            // width of a phone and the longest of them — "Highlight",
            // "Subratlla" — is nine characters. It is a caption under a glyph
            // that also carries the spoken label, not body copy, and the
            // alternative was to cut the words down until they stopped saying
            // what the tool is.
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = ToolLabelSize,
                lineHeight = ToolLabelLine,
            ),
            color = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Space.xs),
        )
    }
}

/**
 * The palette for the tool in hand, opened by pressing that tool again.
 *
 * Every swatch is named. A grid of coloured circles is quick for somebody who
 * can already see which is which and useless for somebody who cannot, and the
 * names cost one line of very small type. Four to a row, so the pen's four sit
 * on one line and the highlighter's six wrap to two rather than running off the
 * side of a menu.
 *
 * Choosing closes it. A palette that stayed open would cover the page it was
 * about to be used on.
 */
@Composable
private fun InkPalette(
    expanded: Boolean,
    tool: ExamTool,
    selectedColor: Int,
    selectedWidth: Float,
    onColorChosen: (Int) -> Unit,
    onWidthChosen: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val highlighting = tool == ExamTool.HIGHLIGHTER
    val colors = if (highlighting) HighlightColors.palette else ExamInk.palette
    val nameOf: (Int) -> Int = if (highlighting) HighlightColors::nameOf else ExamInk::nameOf
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Space.lg),
    ) {
        Column(modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm)) {
            PaletteHeading(stringResource(R.string.exam_colour_heading))
            colors.chunked(SWATCHES_PER_ROW).forEach { row ->
                Row {
                    row.forEach { argb ->
                        Swatch(
                            label = stringResource(nameOf(argb)),
                            selected = argb == selectedColor,
                            onClick = { onColorChosen(argb) },
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(SwatchSize)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape,
                                    ),
                            )
                        }
                    }
                }
            }

            // Only the pen has a nib. The highlighter covers a line of type by
            // definition, and a typed answer's size is the page's decision.
            if (tool == ExamTool.PEN) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = Space.sm),
                )
                PaletteHeading(stringResource(R.string.exam_thickness_heading))
                Row {
                    StrokeWidths.pen.forEach { width ->
                        Swatch(
                            label = stringResource(
                                when (width) {
                                    StrokeWidths.FINE -> R.string.exam_stroke_fine
                                    StrokeWidths.THICK -> R.string.exam_stroke_thick
                                    else -> R.string.exam_stroke_medium
                                },
                            ),
                            selected = width == selectedWidth,
                            onClick = { onWidthChosen(width) },
                        ) {
                            Box(
                                modifier = Modifier.size(SwatchSize),
                                contentAlignment = Alignment.Center,
                            ) {
                                // The nib at its own relative size, so the
                                // choice is made by looking rather than by
                                // reading.
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
        }
    }
}

@Composable
private fun PaletteHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Space.xs, bottom = Space.xs),
    )
}

/** One choice in the palette: the thing itself, its name, and a ring when it is on. */
@Composable
private fun Swatch(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    mark: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(SwatchSlotWidth)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = Space.xs)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(SwatchRing)
                .clip(CircleShape)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                ),
            contentAlignment = Alignment.Center,
        ) { mark() }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** How wide a tool's glyph is drawn. */
private val ToolGlyph = 24.dp

/** The caption under it. See [ToolSlot] for why it is smaller than labelSmall. */
private val ToolLabelSize = 10.sp
private val ToolLabelLine = 13.sp

/** The stripe of ink under the tool in hand, and the edge that keeps it visible. */
private val InkBarWidth = 22.dp
private val InkBarHeight = 5.dp
private const val InkBarRingAlpha = 0.45f

/** How many swatches sit on one line of the palette. */
private const val SWATCHES_PER_ROW = 4

/** The ring a chosen swatch sits in, and the column it and its name share. */
private val SwatchRing = 40.dp
private val SwatchSlotWidth = 60.dp

/**
 * How big the coloured disc itself is, inside its ring.
 *
 * Under the 48dp a button would get, and that is what the ring around it and
 * the column it sits in are for: the target a finger has is the whole column,
 * name included, and the disc is only the part of it that is coloured.
 */
private val SwatchSize = 30.dp

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
