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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 * The layout is a sandwich and the order is deliberate: the recording on top,
 * where a listening exercise can be started without leaving the question; the
 * page in the middle taking every pixel that is left; the tools at the bottom,
 * under the thumb.
 *
 * Both ends of that sandwich get out of the way, because on a screen for people
 * who find clutter hard going the page is the thing and everything else is
 * scaffolding. The player is closed until the headphones button in the bar asks
 * for it, so twenty pages of reading are not spent looking at the controls for
 * the one listening task; and while somebody is typing, the tools and the ink
 * fold away, because none of them can be used with a cursor in a field and all
 * of them were taking room from the keyboard.
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

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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
                onToggleDarkPage = viewModel::onToggleDarkPage,
                onToggleNotes = { viewModel.onToggleNotes(notesLabel) },
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
 * The tools, under the thumb.
 *
 * Everything that changes what the finger does is on one row, and what that
 * tool needs — ink, thickness — appears on a second row underneath only when
 * it applies. A bar that showed pen colours while the eraser was selected
 * would be offering settings for a tool that is not running.
 *
 * ### While the keyboard is up
 *
 * All of it folds away to the page counter and a Done button. Not to save room
 * for its own sake, though it does save it: with a cursor in a field there is
 * no tool that can be used and no colour that can be chosen, so three rows of
 * controls were standing between the keyboard and the words being typed while
 * doing nothing at all. What is left is the one thing somebody typing needs —
 * the way out.
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
    onToggleDarkPage: () -> Unit,
    onToggleNotes: () -> Unit,
    onAddNotesPage: () -> Unit,
    onStep: (Int) -> Unit,
) {
    val typing = state.editingText
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        // A line where the page stops and the tools start. The two are the same
        // few shades of near-black in the dark theme, and without it the bar
        // read as more page rather than as a different thing.
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // The page counter, with arrows that are the only way to turn a page
        // while a tool has the finger. Always present so their position never
        // moves, and disabled rather than hidden at the ends.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.sm, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onStep(-1) }, enabled = page > 0) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.exam_previous_page),
                )
            }
            // Which document, when the paper is made of several. Swiping off
            // the end of the reading paper and onto the listening one is
            // otherwise a silent change of subject.
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(
                        R.string.exam_page_of,
                        page + 1,
                        pageCount.coerceAtLeast(1),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
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
            when {
                // The way out of the keyboard, in the place the other page
                // controls sit, so nothing moves when it appears.
                typing -> TextButton(onClick = onDoneTyping) {
                    Text(stringResource(R.string.action_done))
                }

                // The way to the blank pages, and back. One button, because it
                // is one thought — the other part of this booklet — and the
                // reader is always on exactly one side of the join, so it is
                // never ambiguous about which way it goes. On the blank pages a
                // plus appears beside it, which is the only place another blank
                // page could be wanted.
                !state.showAnswerKey -> {
                    if (state.isOnNotes) {
                        IconButton(onClick = onAddNotesPage) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.exam_add_page),
                            )
                        }
                    }
                    IconButton(onClick = onToggleNotes) {
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
            }
            IconButton(onClick = { onStep(1) }, enabled = page < pageCount - 1) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.exam_next_page),
                )
            }
        }

        if (typing) return@Column

        // The row below the page counter, in two halves: the tools scroll, the
        // three buttons on the right do not, and a rule between them says so.
        //
        // It was one scrolling row, and that was a bug rather than a style —
        // six tools and three buttons come to more than a phone is wide, so
        // undo, redo and the dark-page switch went off the end and had to be
        // scrolled back to. The ones that are always in the same place are the
        // ones pressed without looking, which is exactly what undo is for.
        //
        // The rule earns its place too. Both halves can hold a lit-up button —
        // the tool in hand on the left, the dark-page switch on the right — and
        // with the two sitting flush against each other the pair read as one
        // wide, meaningless block rather than as two separate things that
        // happen to both be on.
        //
        // The dark-page switch is here whether or not the reader is looking at
        // their own work, because a page too bright to read is too bright to
        // read on the answer sheet as well; the tools and the two history
        // buttons are only there when there is something to write on.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.sm, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                if (!state.showAnswerKey) {
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
                }
            }
            VerticalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .padding(horizontal = Space.sm)
                    .height(Space.xl),
            )
            IconButton(
                onClick = onToggleDarkPage,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (state.darkPage) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            Color.Transparent
                        },
                    ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_contrast),
                    contentDescription = stringResource(
                        if (state.darkPage) R.string.exam_light_page else R.string.exam_dark_page,
                    ),
                    tint = if (state.darkPage) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (!state.showAnswerKey) {
                IconButton(onClick = onUndo) {
                    Icon(
                        painter = painterResource(R.drawable.ic_undo),
                        contentDescription = stringResource(R.string.exam_undo),
                    )
                }
                // Disabled rather than hidden, so the pair never moves under
                // the thumb: undo and redo are pressed in quick succession, and
                // a button that appears between two presses is a button pressed
                // by accident.
                IconButton(onClick = onRedo, enabled = state.canRedo) {
                    Icon(
                        painter = painterResource(R.drawable.ic_redo),
                        contentDescription = stringResource(R.string.exam_redo),
                    )
                }
            }
        }

        if (!state.showAnswerKey) {
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
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
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
                    .size(SwatchSize)
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
                        .size(SwatchSize)
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

/**
 * How big a colour or nib swatch is.
 *
 * Under the 48dp a button would get, and that is what the row's own padding and
 * the gaps between them are for: the target a finger has is the swatch plus the
 * air around it, and a row of discs jammed edge to edge is both harder to hit
 * and harder to look at.
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
