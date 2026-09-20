package com.david.llegeix.ui.flashcards

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.data.db.dao.DeckWithCount
import com.david.llegeix.data.flashcards.DeckNames
import com.david.llegeix.data.flashcards.MeaningLanguage
import com.david.llegeix.data.flashcards.PictureResults
import com.david.llegeix.data.flashcards.StudyDirection
import com.david.llegeix.data.flashcards.StudyScope
import com.david.llegeix.data.practice.Leitner
import com.david.llegeix.ui.common.AppBottomSheet
import com.david.llegeix.ui.common.AppSnackbarHost
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.MenuIcon
import com.david.llegeix.ui.common.Pill
import com.david.llegeix.ui.common.PillGroup
import com.david.llegeix.ui.common.ScreenTitle
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.common.resolved
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The Flashcards tab: the decks the reader has made, the shelves they sit on,
 * and a way into practice.
 *
 * A tab of its own because this is somewhere things are *made*. The other three
 * are the books, the language, and what was set aside while reading; this is
 * vocabulary written by hand, and it is a main reason to open the app rather
 * than a view of something that already lives elsewhere.
 *
 * ### The top of the tab
 *
 * It has been a filled panel headed "14 cards to review" over a row of
 * controls, and then a quieter strip with the same controls on it. Both were
 * the same mistake in different weights: the first third of a tab about decks
 * was given over to settings about how to practise them.
 *
 * Those settings are one button in the app bar now — [StudyPairMenu], four
 * rows, next to the one that saves a copy — which is where a phone keeps the
 * choices that belong to a whole screen. What is left below the bar is the one
 * thing anybody came to this tab to do that is not about a particular deck:
 * go through all of it. The decks begin immediately underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsScreen(
    onOpenDeck: (deckId: Long) -> Unit,
    onStudy: (scope: StudyScope, direction: StudyDirection, language: MeaningLanguage) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FlashcardsViewModel = viewModel(factory = FlashcardsViewModel.Factory),
) {
    val list by viewModel.list.collectAsStateWithLifecycle()
    val openShelves by viewModel.openShelves.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
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

    var creating by remember { mutableStateOf(false) }
    var newDeckCount by remember { mutableIntStateOf(0) }
    var renaming by remember { mutableStateOf<DeckWithCount?>(null) }
    var deleting by remember { mutableStateOf<DeckWithCount?>(null) }
    var picturing by remember { mutableStateOf<DeckWithCount?>(null) }
    var picturingShelf by remember { mutableStateOf<DeckShelf?>(null) }
    var moving by remember { mutableStateOf<DeckWithCount?>(null) }
    var renamingShelf by remember { mutableStateOf<DeckShelf?>(null) }
    var deletingShelf by remember { mutableStateOf<DeckShelf?>(null) }
    var showingBackup by remember { mutableStateOf(false) }

    val messageText = message?.resolved()
    LaunchedEffect(messageText) {
        val text = messageText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onMessageShown()
    }

    /** Whether a set of decks has anything to ask in the language chosen. */
    fun canPractise(decks: List<DeckWithCount>): Boolean = decks.any { deck ->
        when (language) {
            MeaningLanguage.ROMANIAN -> deck.cardCount > 0
            MeaningLanguage.ENGLISH -> deck.englishCount > 0
        }
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
                    StudyPairMenu(
                        direction = direction,
                        language = language,
                        onChoose = viewModel::onChoosePair,
                    )
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
            FloatingActionButton(onClick = { newDeckCount++; creating = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.flashcards_new_deck))
            }
        },
    ) { innerPadding ->
        val loaded = list
        when {
            // Still asking the database: draw nothing rather than a false
            // "no decks yet" for one frame.
            loaded == null -> Box(Modifier.padding(innerPadding))

            loaded.isEmpty -> EmptyState(
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
                // Only once there is something to practise: asking which way
                // round to go through decks that have nothing in them is a
                // question with no answer.
                if (loaded.hasCards && canPractise(loaded.allDecks)) {
                    item(key = "practice") {
                        PractiseEverything(
                            onStudy = { onStudy(StudyScope.Everything, direction, language) },
                        )
                    }
                }

                // The shelves first, then what is on no shelf. A reader who has
                // never made a collection sees exactly the list they had before
                // collections existed.
                loaded.shelves.forEach { shelf ->
                    item(key = "shelf-${shelf.id}") {
                        ShelfRow(
                            shelf = shelf,
                            isOpen = shelf.id in openShelves,
                            canPractise = canPractise(shelf.decks),
                            onToggle = { viewModel.onToggleShelf(shelf.id) },
                            onPractise = {
                                onStudy(StudyScope.Collection(shelf.id), direction, language)
                            },
                            onPicture = { picturingShelf = shelf },
                            onTogglePinned = {
                                viewModel.setCollectionPinned(shelf.id, !shelf.collection.isPinned)
                            },
                            onRename = { renamingShelf = shelf },
                            onDelete = { deletingShelf = shelf },
                        )
                    }
                    if (shelf.id in openShelves) {
                        if (shelf.decks.isEmpty()) {
                            item(key = "shelf-${shelf.id}-empty") {
                                Text(
                                    text = stringResource(R.string.flashcards_collection_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(start = Space.screen + ShelfIndent)
                                        .padding(end = Space.screen, top = Space.xs, bottom = Space.md),
                                )
                            }
                        }
                        items(shelf.decks, key = { "deck-${it.id}" }) { deck ->
                            DeckRow(
                                deck = deck,
                                direction = direction,
                                canPractise = canPractise(listOf(deck)),
                                indent = ShelfIndent,
                                onOpen = { onOpenDeck(deck.id) },
                                onPractise = {
                                    onStudy(StudyScope.Deck(deck.id), direction, language)
                                },
                                onTogglePinned = { viewModel.setPinned(deck, !deck.isPinned) },
                                onPicture = { picturing = deck },
                                onMove = { moving = deck },
                                onRename = { renaming = deck },
                                onDelete = { deleting = deck },
                            )
                        }
                    }
                }

                items(loaded.loose, key = { "deck-${it.id}" }) { deck ->
                    DeckRow(
                        deck = deck,
                        direction = direction,
                        canPractise = canPractise(listOf(deck)),
                        onOpen = { onOpenDeck(deck.id) },
                        onPractise = {
                            onStudy(StudyScope.Deck(deck.id), direction, language)
                        },
                        onTogglePinned = { viewModel.setPinned(deck, !deck.isPinned) },
                        onPicture = { picturing = deck },
                        onMove = { moving = deck },
                        onRename = { renaming = deck },
                        onDelete = { deleting = deck },
                    )
                }
            }
        }
    }

    if (showingBackup) {
        AppBottomSheet(
            onDismissRequest = { showingBackup = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            BackupSheet(isBusy = backupBusy, onExport = onExport, onRestore = onRestore)
        }
    }

    picturing?.let { deck ->
        DeckPictureSheet(deck = deck, onDismiss = { picturing = null })
    }

    picturingShelf?.let { shelf ->
        CollectionPictureSheet(shelf = shelf, onDismiss = { picturingShelf = null })
    }

    moving?.let { deck ->
        MoveToShelfSheet(
            deck = deck,
            shelves = list?.shelves.orEmpty(),
            onDismiss = { moving = null },
            onMove = { collectionId ->
                viewModel.moveDeck(deck, collectionId)
                moving = null
            },
        )
    }

    if (creating) {
        // A fresh sheet each time it is opened, so the last attempt's name and
        // picture are not waiting in it.
        NewDeckSheet(key = newDeckCount, onDismiss = { creating = false })
    }

    renaming?.let { deck ->
        NameDialog(
            title = stringResource(R.string.flashcards_rename_deck_title),
            label = stringResource(R.string.flashcards_deck_name_label),
            hint = stringResource(R.string.flashcards_deck_name_hint),
            takenRes = R.string.flashcards_deck_exists,
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

    renamingShelf?.let { shelf ->
        NameDialog(
            title = stringResource(R.string.flashcards_rename_collection_title),
            label = stringResource(R.string.flashcards_collection_name_label),
            hint = stringResource(R.string.flashcards_collection_name_hint),
            takenRes = R.string.flashcards_collection_exists,
            confirmLabel = stringResource(R.string.action_rename),
            initialName = shelf.collection.name,
            check = { viewModel.checkCollectionName(it, renaming = shelf.id) },
            onDismiss = { renamingShelf = null },
            onConfirm = { name ->
                viewModel.renameCollection(shelf.id, name)
                renamingShelf = null
            },
        )
    }

    deletingShelf?.let { shelf ->
        AlertDialog(
            onDismissRequest = { deletingShelf = null },
            title = { Text(stringResource(R.string.flashcards_delete_collection_title, shelf.collection.name)) },
            // The one thing anybody wants to know before deleting a shelf is
            // what happens to what was on it. Nothing happens to it, and saying
            // so is the difference between a tidy-up and a moment of panic.
            text = {
                Text(
                    if (shelf.decks.isEmpty()) {
                        stringResource(R.string.flashcards_delete_collection_body_empty)
                    } else {
                        pluralStringResource(
                            R.plurals.flashcards_delete_collection_body,
                            shelf.decks.size,
                            shelf.decks.size,
                        )
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCollection(shelf)
                        deletingShelf = null
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingShelf = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
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
 * Go through the lot.
 *
 * The one thing on this tab that is not about a particular deck, so the one
 * thing that earns a place above them all. Not a round play button, unlike
 * every other way into practice in the app: those sit on a row whose name is
 * already beside them, and this one has nothing next to it to say what it
 * would play.
 *
 * Drawn only when something could actually be asked — a tab full of decks with
 * no English meanings in them has nothing to offer an English session, and a
 * button that leads to an empty one is a button that lies.
 */
@Composable
private fun PractiseEverything(onStudy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen)
            .padding(top = Space.sm, bottom = Space.md),
    ) {
        FilledTonalButton(
            onClick = onStudy,
            contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.flashcards_practise_all),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = Space.sm),
            )
        }
    }
}

/**
 * A collection, as the row that opens it.
 *
 * Pressing anywhere on it folds it open or shut, which is the thing the row is
 * mostly for; the chevron turns to say which way it went. Its own play button
 * starts a session over every card on the shelf at once — the reason
 * collections exist at all, since practising *food* is a different session from
 * practising *vegetables* three times in a row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfRow(
    shelf: DeckShelf,
    isOpen: Boolean,
    canPractise: Boolean,
    onToggle: () -> Unit,
    onPractise: () -> Unit,
    onTogglePinned: () -> Unit,
    onPicture: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (isOpen) 0f else -90f, label = "shelf chevron")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen, vertical = 5.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = onToggle, onLongClick = { menuOpen = true })
            .padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(
            image = shelf.coverImage,
            credit = shelf.coverCredit,
            initial = shelf.collection.name,
            size = CoverSize,
            // A shelf is drawn as a stack: the same square, a step squarer than
            // a deck's, so the two kinds of row are told apart without a label.
            corner = 12.dp,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (isOpen) R.string.flashcards_hide_decks else R.string.flashcards_show_decks,
                        shelf.collection.name,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(turn),
                )
                if (shelf.collection.isPinned) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pin),
                        contentDescription = stringResource(R.string.folders_pinned),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = Space.xs)
                            .size(16.dp),
                    )
                }
                Text(
                    text = shelf.collection.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Space.xs),
                )
            }
            Text(
                text = listOf(
                    pluralStringResource(
                        R.plurals.flashcards_deck_count,
                        shelf.decks.size,
                        shelf.decks.size,
                    ),
                    pluralStringResource(
                        R.plurals.flashcards_card_count,
                        shelf.cardCount,
                        shelf.cardCount,
                    ),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 26.dp, top = 2.dp),
            )
        }

        if (canPractise) {
            PlayButton(
                onClick = onPractise,
                contentDescription = stringResource(
                    R.string.flashcards_practise_name,
                    shelf.collection.name,
                ),
                modifier = Modifier.padding(start = Space.sm),
            )
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more),
                    contentDescription = stringResource(
                        R.string.document_actions,
                        shelf.collection.name,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    leadingIcon = { MenuIcon(painterResource(R.drawable.ic_pin)) },
                    text = {
                        Text(
                            stringResource(
                                if (shelf.collection.isPinned) {
                                    R.string.folders_unpin
                                } else {
                                    R.string.folders_pin
                                },
                            ),
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

/**
 * One deck, as a card of its own.
 *
 * Its cover is the picture the reader chose for it, or its first card's, or its
 * initial. Under the name, how well the deck is known this way round, as a bar
 * rather than a number. On the right, the deck's own round way into practice —
 * the same circle as everywhere else, whatever is or is not due, because going
 * through a deck again is never the wrong thing to want.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckRow(
    deck: DeckWithCount,
    direction: StudyDirection,
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
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    /** How far in from the margin, so a deck on a shelf reads as being on it. */
    indent: Dp = 0.dp,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.screen + indent, end = Space.screen)
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })
            .padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(
            image = deck.coverImage,
            credit = deck.coverCredit,
            initial = deck.name,
            size = CoverSize,
            corner = 16.dp,
        )

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
            // A round mark reads at a glance; said out loud it needs the words.
            PlayButton(
                onClick = onPractise,
                contentDescription = stringResource(
                    R.string.flashcards_practise_name,
                    deck.name,
                ),
                modifier = Modifier.padding(start = Space.sm),
            )
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
                    leadingIcon = { MenuIcon(painterResource(R.drawable.ic_collection)) },
                    text = { Text(stringResource(R.string.flashcards_move_to_collection)) },
                    onClick = { onMove(); menuOpen = false },
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

/**
 * The square that stands for a deck or a shelf: its picture, or its initial on
 * a tinted tile.
 *
 * One composable for both, because a shelf borrows the picture of the first
 * deck on it and two near-identical squares drawn by two different pieces of
 * code is how they stop being near-identical.
 */
@Composable
private fun Cover(
    image: String?,
    credit: String?,
    initial: String,
    size: Dp,
    corner: Dp,
) {
    val shape = RoundedCornerShape(corner)
    if (image != null) {
        CardImage(
            path = image,
            maxEdge = 192,
            contentDescription = null,
            pictogram = PictureResults.isPictogram(credit),
            modifier = Modifier
                .size(size)
                .clip(shape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial.trim().take(1).uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/**
 * Which shelf a deck goes on, chosen from the shelves that exist.
 *
 * A list of the reader's own collections and one row for none of them, rather
 * than a picker that can also invent one: making a collection is what the +
 * does, and offering it here as well would be the same decision reachable two
 * ways, each with its own idea of what is being made.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveToShelfSheet(
    deck: DeckWithCount,
    shelves: List<DeckShelf>,
    onDismiss: () -> Unit,
    onMove: (collectionId: Long?) -> Unit,
) {
    AppBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Space.xl)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.flashcards_move_title, deck.name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.sm),
            )
            if (shelves.isEmpty()) {
                Text(
                    text = stringResource(R.string.flashcards_no_collections),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = Space.screen)
                        .padding(top = Space.sm, bottom = Space.lg),
                )
            }
            ShelfChoice(
                name = stringResource(R.string.flashcards_move_none),
                chosen = deck.collectionId == null,
                onClick = { onMove(null) },
            )
            shelves.forEach { shelf ->
                ShelfChoice(
                    name = shelf.collection.name,
                    chosen = deck.collectionId == shelf.id,
                    onClick = { onMove(shelf.id) },
                )
            }
        }
    }
}

@Composable
private fun ShelfChoice(name: String, chosen: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.screen, vertical = Space.md),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_collection),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Space.lg),
        )
        if (chosen) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Something new here: a deck, or a shelf to put decks on.
 *
 * One sheet for both, reached by the one + button. What is being made is the
 * first thing in it, as two pills, so the choice is in front of the reader
 * rather than behind a second floating button they would have to notice — and
 * the name field below is the same field either way.
 *
 * A deck is offered a picture while its name is being typed, because the name
 * is the best search there is for one: typing *Menjar* is already asking for
 * pictures of food. A collection is not: it wears the picture of the first deck
 * on it, which is a picture the reader has already chosen once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewDeckSheet(key: Int, onDismiss: () -> Unit) {
    val viewModel: NewDeckViewModel = viewModel(key = "new-deck-$key", factory = NewDeckViewModel.Factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val suggestions by viewModel.pictures.state.collectAsStateWithLifecycle()
    val source by viewModel.pictures.source.collectAsStateWithLifecycle()
    val created by viewModel.created.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val pickOwn = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onPickOwn)
    }
    val close = {
        viewModel.onCancel()
        onDismiss()
    }
    LaunchedEffect(created) { if (created) onDismiss() }
    // The name is the first thing asked, so the keyboard is already up for it.
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { nameFocus.requestFocus() } }

    val isDeck = state.kind == NewDeckKind.DECK

    AppBottomSheet(
        onDismissRequest = close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen)
                .padding(bottom = Space.xl)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            PillGroup(modifier = Modifier.fillMaxWidth()) {
                NewDeckKind.entries.forEach { kind ->
                    val name = stringResource(
                        when (kind) {
                            NewDeckKind.DECK -> R.string.flashcards_kind_deck
                            NewDeckKind.COLLECTION -> R.string.flashcards_kind_collection
                        },
                    )
                    Pill(
                        selected = kind == state.kind,
                        onClick = { viewModel.onKindChange(kind) },
                        label = name,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = name, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            val taken = state.check as? DeckNames.Check.Taken
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = {
                    Text(
                        stringResource(
                            if (isDeck) {
                                R.string.flashcards_deck_name_label
                            } else {
                                R.string.flashcards_collection_name_label
                            },
                        ),
                    )
                },
                placeholder = {
                    Text(
                        stringResource(
                            if (isDeck) {
                                R.string.flashcards_deck_name_hint
                            } else {
                                R.string.flashcards_collection_name_hint
                            },
                        ),
                    )
                },
                singleLine = true,
                isError = taken != null,
                supportingText = taken?.let {
                    {
                        Text(
                            stringResource(
                                if (isDeck) {
                                    R.string.flashcards_deck_exists
                                } else {
                                    R.string.flashcards_collection_exists
                                },
                                it.existing,
                            ),
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.md)
                    .focusRequester(nameFocus),
            )

            if (isDeck) {
                Text(
                    text = stringResource(R.string.flashcards_picture),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Space.md, bottom = Space.sm),
                )
                val cover = state.coverPath
                if (cover != null) {
                    // Pressing the picture goes back to the grid, the way it
                    // does on a card: a picture on a form is a thing everybody
                    // expects to be able to press, and a button beside it
                    // saying so is a button spending a third of the row on
                    // something the picture already said.
                    CardImage(
                        path = cover,
                        maxEdge = 384,
                        contentDescription = stringResource(R.string.flashcards_picture_other),
                        pictogram = PictureResults.isPictogram(state.coverCredit),
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(
                                onClickLabel = stringResource(R.string.flashcards_picture_other),
                                onClick = viewModel::onRemoveCover,
                            ),
                    )
                } else {
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
                }
            }
            message?.resolved()?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Space.sm),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                modifier = Modifier.padding(top = Space.lg),
            ) {
                OutlinedButton(onClick = close, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = viewModel::onCreate,
                    enabled = state.check is DeckNames.Check.Ok && !state.isBusy,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.action_create)) }
            }
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

    AppBottomSheet(
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
 * Choosing a collection's picture: the deck sheet's twin, searched with the
 * shelf's name, with a way back to the borrowed picture when one has been
 * chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionPictureSheet(shelf: DeckShelf, onDismiss: () -> Unit) {
    val viewModel: CollectionPictureViewModel = viewModel(
        key = "collection-picture-${shelf.id}",
        factory = CollectionPictureViewModel.factory(shelf.id),
    )
    val suggestions by viewModel.pictures.state.collectAsStateWithLifecycle()
    val source by viewModel.pictures.source.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val pickOwn = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onPickOwn)
    }
    LaunchedEffect(done) { if (done) onDismiss() }

    AppBottomSheet(
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
                text = stringResource(
                    R.string.flashcards_deck_picture_title,
                    shelf.collection.name,
                ),
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
            if (shelf.chosenCover != null) {
                OutlinedButton(
                    onClick = viewModel::onRemove,
                    modifier = Modifier.padding(top = Space.lg),
                ) { Text(stringResource(R.string.flashcards_collection_picture_remove)) }
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
            Button(onClick = onExport, enabled = !isBusy, modifier = Modifier.weight(1f)) {
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

/**
 * Name a deck or a collection, new or existing.
 *
 * The name is checked as it is typed, and a clash is said under the field in
 * words, naming the one it clashes with — a disabled button with no reason
 * given is a puzzle, and this app is built not to set those.
 */
@Composable
private fun NameDialog(
    title: String,
    label: String,
    hint: String,
    takenRes: Int,
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
                label = { Text(label) },
                placeholder = { Text(hint) },
                singleLine = true,
                isError = taken != null,
                supportingText = taken?.let { { Text(stringResource(takenRes, it.existing)) } },
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

/** How far a deck on a shelf sits in from the margin. */
private val ShelfIndent = 20.dp

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
