package com.david.llegeix.ui.flashcards

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.data.db.dao.DeckDue
import com.david.llegeix.data.db.dao.DeckWithCount
import com.david.llegeix.data.flashcards.DeckNames
import com.david.llegeix.data.flashcards.MeaningLanguage
import com.david.llegeix.data.flashcards.StudyDirection
import com.david.llegeix.data.practice.Leitner
import com.david.llegeix.ui.common.AppSnackbarHost
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.MenuIcon
import com.david.llegeix.ui.common.ScreenTitle
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.common.resolved
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The Flashcards tab: the decks the reader has made, and a way into practice.
 *
 * A tab of its own because this is somewhere things are *made*. The other three
 * are the books, the language, and what was set aside while reading; this is
 * vocabulary written by hand, and it is a main reason to open the app rather
 * than a view of something that already lives elsewhere.
 *
 * Three things on it, in order of use: practice over everything, the decks
 * (each with its own way into practice), and a bare + for a new deck. Saving a
 * copy is a button in the corner: it matters, and it is also something done
 * once in a while, so it has no business being a panel read past every visit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsScreen(
    onOpenDeck: (deckId: Long) -> Unit,
    onStudy: (deckId: Long?, direction: StudyDirection, language: MeaningLanguage, extra: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FlashcardsViewModel = viewModel(factory = FlashcardsViewModel.Factory),
) {
    val decks by viewModel.decks.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val dueByDeck by viewModel.dueByDeck.collectAsStateWithLifecycle()
    val direction by viewModel.direction.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val backupBusy by viewModel.backupBusy.collectAsStateWithLifecycle()

    // The system's own file screens, so the copy goes wherever the reader
    // keeps things — Downloads, a memory card, a cloud drive they chose — and
    // the app is never given more than the one file.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE),
    ) { uri -> uri?.let(viewModel::onExport) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::onRestore) }
    val backupName = stringResource(
        R.string.flashcards_backup_filename,
        LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    )
    val onExport = { exportLauncher.launch(backupName) }
    val onRestore = { restoreLauncher.launch(BACKUP_OPEN_TYPES) }
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleResumeEffect(Unit) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }

    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<DeckWithCount?>(null) }
    var deleting by remember { mutableStateOf<DeckWithCount?>(null) }
    var picturing by remember { mutableStateOf<DeckWithCount?>(null) }
    var showingBackup by remember { mutableStateOf(false) }

    val messageText = message?.resolved()
    LaunchedEffect(messageText) {
        val text = messageText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onMessageShown()
    }

    Scaffold(
        // The app shell has already inset this screen for the system bars.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                expandedHeight = Space.topBar,
                title = {
                    ScreenTitle(
                        icon = R.drawable.ic_flashcards,
                        title = stringResource(R.string.nav_flashcards),
                    )
                },
                actions = {
                    IconButton(onClick = { showingBackup = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_backup),
                            contentDescription = stringResource(R.string.flashcards_backup_title),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.flashcards_new_deck))
            }
        },
    ) { innerPadding ->
        val list = decks
        when {
            // Still asking the database: draw nothing rather than a false
            // "no decks yet" for one frame.
            list == null -> Box(Modifier.padding(innerPadding))

            list.isEmpty() -> EmptyState(
                title = stringResource(R.string.flashcards_empty_title),
                body = stringResource(R.string.flashcards_empty_body),
                icon = painterResource(R.drawable.ic_flashcards),
                modifier = Modifier.padding(innerPadding),
                // A new phone, or the app put back after an uninstall, starts
                // here — which is exactly when a saved copy is wanted, so the
                // way to bring one back is on the screen, not behind a button.
                secondaryAction = {
                    TextButton(onClick = onRestore, enabled = !backupBusy) {
                        Text(stringResource(R.string.flashcards_backup_restore))
                    }
                },
            )

            else -> LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                // Room under the last deck for the + button.
                contentPadding = PaddingValues(top = Space.xs, bottom = BottomClearance),
            ) {
                // Only once there is something to practise: a panel over decks
                // with nothing in them is a question with no answer.
                if (list.any { it.cardCount > 0 }) {
                    item(key = "practice") {
                        val due = dueByDeck.values.sumOf { it.dueIn(direction) }
                        PracticePanel(
                            direction = direction,
                            language = language,
                            dueCount = due,
                            onChooseDirection = viewModel::onChooseDirection,
                            onChooseLanguage = viewModel::onChooseLanguage,
                            // Nothing due is not a closed door: it is a round of
                            // extra practice, off the schedule.
                            onStudy = { onStudy(null, direction, language, due == 0) },
                        )
                    }
                }
                items(list, key = { it.id }) { deck ->
                    val due = dueByDeck[deck.id]?.dueIn(direction) ?: 0
                    DeckCard(
                        deck = deck,
                        direction = direction,
                        dueCount = due,
                        canPractise = when (language) {
                            MeaningLanguage.ROMANIAN -> deck.cardCount > 0
                            MeaningLanguage.ENGLISH -> deck.englishCount > 0
                        },
                        onOpen = { onOpenDeck(deck.id) },
                        onPractise = { onStudy(deck.id, direction, language, due == 0) },
                        onTogglePinned = { viewModel.setPinned(deck, !deck.isPinned) },
                        onPicture = { picturing = deck },
                        onRename = { renaming = deck },
                        onDelete = { deleting = deck },
                    )
                }
            }
        }
    }

    if (showingBackup) {
        ModalBottomSheet(
            onDismissRequest = { showingBackup = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            BackupSheet(isBusy = backupBusy, onExport = onExport, onRestore = onRestore)
        }
    }

    picturing?.let { deck ->
        DeckPictureSheet(deck = deck, onDismiss = { picturing = null })
    }

    if (creating) {
        DeckNameDialog(
            title = stringResource(R.string.flashcards_new_deck),
            confirmLabel = stringResource(R.string.action_create),
            check = { viewModel.checkName(it) },
            onDismiss = { creating = false },
            onConfirm = { name ->
                viewModel.createDeck(name)
                creating = false
            },
        )
    }

    renaming?.let { deck ->
        DeckNameDialog(
            title = stringResource(R.string.flashcards_rename_deck_title),
            confirmLabel = stringResource(R.string.action_rename),
            initialName = deck.name,
            check = { viewModel.checkName(it, renaming = deck.id) },
            onDismiss = { renaming = null },
            onConfirm = { name ->
                viewModel.renameDeck(deck.id, name)
                renaming = null
            },
        )
    }

    deleting?.let { deck ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = {
                Text(stringResource(R.string.flashcards_delete_deck_title, deck.name))
            },
            // Says exactly what goes, in numbers: a deck is hand-made work, and
            // "delete this deck?" alone does not tell anybody it is sixty cards
            // and twelve photographs they chose one by one.
            text = {
                Text(
                    if (deck.cardCount == 0) {
                        stringResource(R.string.flashcards_delete_deck_body_empty)
                    } else {
                        listOfNotNull(
                            pluralStringResource(
                                R.plurals.flashcards_delete_deck_body,
                                deck.cardCount,
                                deck.cardCount,
                            ),
                            when {
                                deck.imageCount == 0 -> null
                                // "One of them" when there is only one reads as a slip.
                                deck.cardCount == 1 ->
                                    stringResource(R.string.flashcards_delete_deck_only_picture)
                                else -> pluralStringResource(
                                    R.plurals.flashcards_delete_deck_pictures,
                                    deck.imageCount,
                                    deck.imageCount,
                                )
                            },
                            stringResource(R.string.flashcards_cannot_undo),
                        ).joinToString(" ")
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDeck(deck)
                        deleting = null
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * Practice over every deck: which language, which way round, and go.
 *
 * Deliberately compact — a line of what is waiting, a line of controls — so the
 * decks start near the top of the screen instead of under a poster. The number
 * is still the largest thing in it, because it is the reason to look.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PracticePanel(
    direction: StudyDirection,
    language: MeaningLanguage,
    dueCount: Int,
    onChooseDirection: (StudyDirection) -> Unit,
    onChooseLanguage: (MeaningLanguage) -> Unit,
    onStudy: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen)
            .padding(bottom = Space.md)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = Space.lg, vertical = Space.md),
    ) {
        val onPanel = MaterialTheme.colorScheme.onPrimaryContainer
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.flashcards_study_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = onPanel.copy(alpha = 0.75f),
                )
                if (dueCount > 0) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$dueCount",
                            style = MaterialTheme.typography.headlineMedium,
                            color = onPanel,
                        )
                        Text(
                            text = pluralStringResource(R.plurals.flashcards_panel_due, dueCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = onPanel,
                            modifier = Modifier.padding(start = Space.sm, bottom = 5.dp),
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.flashcards_panel_all_done),
                        style = MaterialTheme.typography.titleMedium,
                        color = onPanel,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            LanguageSwitch(selected = language, onSelect = onChooseLanguage)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = Space.md),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                StudyDirection.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = entry == direction,
                        onClick = { onChooseDirection(entry) },
                        shape = SegmentedButtonDefaults.itemShape(index, StudyDirection.entries.size),
                        colors = SegmentedButtonDefaults.colors(
                            // The chosen way round in the accent, bright in
                            // either theme; the other left open on the panel.
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            activeBorderColor = MaterialTheme.colorScheme.primary,
                            inactiveContainerColor = Color.Transparent,
                            inactiveContentColor = onPanel,
                            inactiveBorderColor = onPanel.copy(alpha = 0.3f),
                        ),
                        // The flags say which is chosen as well as any tick would,
                        // and a tick would crowd two flags and an arrow.
                        icon = {},
                        label = { DirectionFlags(entry, language = language, flagWidth = 20.dp) },
                    )
                }
            }
            Spacer(Modifier.width(Space.md))
            FilledIconButton(
                onClick = onStudy,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource(
                        if (dueCount > 0) R.string.flashcards_panel_start else R.string.flashcards_practise_again,
                    ),
                )
            }
        }
    }
}

/**
 * One deck, as a card of its own.
 *
 * Its cover is the picture the reader chose for it, or its first card's, or its
 * initial. Under the name, how well the deck is known this way round, as a bar
 * rather than a number. On the right, the deck's own way into practice:
 * carrying how many are due when some are, and still there when none are,
 * because going through a deck again is never the wrong thing to want.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckCard(
    deck: DeckWithCount,
    direction: StudyDirection,
    /** Due in the direction and language currently chosen, so the numbers agree with the panel. */
    dueCount: Int,
    /**
     * Whether any of its cards can be asked in the language chosen. A deck with
     * no English meanings has nothing to offer an English session, and a play
     * button that led to an empty one would be a button that lies.
     */
    canPractise: Boolean,
    onOpen: () -> Unit,
    onPractise: () -> Unit,
    onTogglePinned: () -> Unit,
    onPicture: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen, vertical = 5.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })
            .padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeckCover(deck)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (deck.isPinned) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pin),
                        contentDescription = stringResource(R.string.folders_pinned),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(end = Space.xs)
                            .size(16.dp),
                    )
                }
                Text(
                    text = deck.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (deck.cardCount == 0) {
                    stringResource(R.string.flashcards_deck_no_cards)
                } else {
                    pluralStringResource(R.plurals.flashcards_card_count, deck.cardCount, deck.cardCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (deck.cardCount > 0) {
                LinearProgressIndicator(
                    progress = { deck.knownIn(direction) },
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .padding(top = Space.sm)
                        .fillMaxWidth(0.85f)
                        .height(5.dp)
                        .clip(CircleShape),
                )
            }
        }

        if (canPractise) {
            // A play mark and a number read at a glance; said out loud they
            // need the words.
            val label = if (dueCount > 0) {
                stringResource(R.string.flashcards_practise_deck, deck.name, dueCount)
            } else {
                stringResource(R.string.flashcards_practise_deck_again, deck.name)
            }
            FilledTonalButton(
                onClick = onPractise,
                contentPadding = PaddingValues(horizontal = if (dueCount > 0) Space.md else Space.sm),
                modifier = Modifier
                    .padding(start = Space.sm)
                    .clearAndSetSemantics {
                        contentDescription = label
                        role = Role.Button
                    },
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                if (dueCount > 0) {
                    Text(
                        text = "$dueCount",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = Space.xs),
                    )
                }
            }
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more),
                    contentDescription = stringResource(R.string.document_actions, deck.name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    leadingIcon = { MenuIcon(painterResource(R.drawable.ic_pin)) },
                    text = {
                        Text(
                            stringResource(if (deck.isPinned) R.string.folders_unpin else R.string.folders_pin),
                        )
                    },
                    onClick = { onTogglePinned(); menuOpen = false },
                )
                DropdownMenuItem(
                    leadingIcon = { MenuIcon(painterResource(R.drawable.ic_image)) },
                    text = { Text(stringResource(R.string.flashcards_deck_picture)) },
                    onClick = { onPicture(); menuOpen = false },
                )
                DropdownMenuItem(
                    leadingIcon = { MenuIcon(Icons.Default.Edit) },
                    text = { Text(stringResource(R.string.folders_rename)) },
                    onClick = { onRename(); menuOpen = false },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    leadingIcon = { MenuIcon(Icons.Default.Delete) },
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = { onDelete(); menuOpen = false },
                )
            }
        }
    }
}

/** The deck's picture: the chosen one, its first card's, or its initial on a tinted tile. */
@Composable
private fun DeckCover(deck: DeckWithCount) {
    val shape = RoundedCornerShape(16.dp)
    val cover = deck.coverImage
    if (cover != null) {
        CardImage(
            path = cover,
            maxEdge = 192,
            contentDescription = null,
            modifier = Modifier
                .size(CoverSize)
                .clip(shape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(CoverSize)
                .clip(shape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = deck.name.trim().take(1).uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/**
 * Choosing a deck's picture: the same grid as a card's, searched with the
 * deck's name, with a way back to no chosen picture when there is one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeckPictureSheet(deck: DeckWithCount, onDismiss: () -> Unit) {
    val viewModel: DeckPictureViewModel = viewModel(
        key = "deck-picture-${deck.id}",
        factory = DeckPictureViewModel.factory(deck.id),
    )
    val suggestions by viewModel.pictures.state.collectAsStateWithLifecycle()
    val source by viewModel.pictures.source.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val pickOwn = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onPickOwn)
    }
    LaunchedEffect(done) { if (done) onDismiss() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen)
                .padding(bottom = Space.xl)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.flashcards_deck_picture_title, deck.name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = Space.md),
            )
            message?.resolved()?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = Space.sm),
                )
            }
            PictureGrid(
                suggestions = suggestions,
                source = source,
                onSourceChange = viewModel::onSourceChange,
                onPick = viewModel::onPick,
                onPickOwn = {
                    pickOwn.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRetry = viewModel::onRetry,
            )
            if (deck.chosenCover != null) {
                OutlinedButton(
                    onClick = viewModel::onRemove,
                    modifier = Modifier.padding(top = Space.lg),
                ) { Text(stringResource(R.string.flashcards_deck_picture_remove)) }
            }
        }
    }
}

/**
 * Saving a copy of the cards, and bringing one back, in a sheet from the
 * corner button — with the line that says why it matters, which is a fact
 * nobody would guess from the rest of the screen.
 */
@Composable
private fun BackupSheet(isBusy: Boolean, onExport: () -> Unit, onRestore: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen)
            .padding(bottom = Space.xl)
            .navigationBarsPadding(),
    ) {
        Text(
            text = stringResource(R.string.flashcards_backup_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.flashcards_backup_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.sm),
        )
        if (isBusy) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.lg),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            modifier = Modifier.padding(top = Space.lg),
        ) {
            FilledTonalButton(onClick = onExport, enabled = !isBusy, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.flashcards_backup_save))
            }
            OutlinedButton(onClick = onRestore, enabled = !isBusy, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.flashcards_backup_restore))
            }
        }
    }
}

/**
 * How well a deck is known this way round, 0..1: its cards' Leitner boxes
 * against every card being in the last one. The same measure the saved words'
 * progress uses, taken over the deck.
 */
private fun DeckWithCount.knownIn(direction: StudyDirection): Float {
    if (cardCount == 0) return 0f
    val total = when (direction) {
        StudyDirection.CATALAN_TO_MEANING -> boxTotal
        StudyDirection.MEANING_TO_CATALAN -> reverseBoxTotal
    }
    return (total.toFloat() / (cardCount * Leitner.LAST_BOX)).coerceIn(0f, 1f)
}

/** How many of the deck's cards are due in [direction]. */
private fun DeckDue.dueIn(direction: StudyDirection): Int = when (direction) {
    StudyDirection.CATALAN_TO_MEANING -> forwardDue
    StudyDirection.MEANING_TO_CATALAN -> reverseDue
}

/**
 * Name a deck, new or existing.
 *
 * The name is checked as it is typed, and a clash is said under the field in
 * words, naming the deck it clashes with — a disabled button with no reason
 * given is a puzzle, and this app is built not to set those.
 */
@Composable
private fun DeckNameDialog(
    title: String,
    confirmLabel: String,
    check: (String) -> DeckNames.Check,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initialName: String = "",
) {
    var name by remember { mutableStateOf(initialName) }
    val result = check(name)
    val taken = result as? DeckNames.Check.Taken

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.flashcards_deck_name_label)) },
                placeholder = { Text(stringResource(R.string.flashcards_deck_name_hint)) },
                singleLine = true,
                isError = taken != null,
                supportingText = taken?.let {
                    { Text(stringResource(R.string.flashcards_deck_exists, it.existing)) }
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = result is DeckNames.Check.Ok,
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private val CoverSize = 60.dp

/** Clears the + button: its 56dp, and the 16dp it floats above the bar. */
private val BottomClearance = 88.dp

private const val BACKUP_MIME_TYPE = "application/zip"

/**
 * What the restore picker offers. Zip goes by several names depending on the
 * app that saved or downloaded it, and a copy that cannot be picked because a
 * file manager called it by another one is a copy that is lost.
 */
private val BACKUP_OPEN_TYPES = arrayOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/octet-stream",
)
