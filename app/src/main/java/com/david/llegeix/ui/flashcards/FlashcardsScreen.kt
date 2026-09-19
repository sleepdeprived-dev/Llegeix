package com.david.llegeix.ui.flashcards

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import com.david.llegeix.data.practice.Leitner
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.data.db.dao.DeckDue
import com.david.llegeix.data.db.dao.DeckWithCount
import com.david.llegeix.data.flashcards.DeckNames
import com.david.llegeix.data.flashcards.StudyDirection
import com.david.llegeix.ui.common.AppSnackbarHost
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.MenuIcon
import com.david.llegeix.ui.common.ScreenTitle
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.common.resolved

/**
 * The Flashcards tab: the decks the reader has made.
 *
 * A tab of its own because this is somewhere things are *made*. The other three
 * are the books, the language, and what was set aside while reading; this is
 * vocabulary written by hand, and it is a main reason to open the app rather
 * than a view of something that already lives elsewhere.
 *
 * The one action that is always available — making a deck — is the button at
 * the bottom right, in words, where the library puts *Add documents*. What can
 * be done to a deck is on the deck: the three dots, or a hold anywhere on its
 * row, which is the gesture every list in this app answers to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsScreen(
    onOpenDeck: (deckId: Long) -> Unit,
    onStudy: (deckId: Long?, direction: StudyDirection) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FlashcardsViewModel = viewModel(factory = FlashcardsViewModel.Factory),
) {
    val decks by viewModel.decks.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val dueByDeck by viewModel.dueByDeck.collectAsStateWithLifecycle()
    val direction by viewModel.direction.collectAsStateWithLifecycle()
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
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.flashcards_new_deck)) },
            )
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
                // way to bring one back cannot wait for a deck to exist.
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
                // Room under the last row for the button, so it can always be
                // scrolled clear of it.
                contentPadding = PaddingValues(top = Space.sm, bottom = BottomClearance),
            ) {
                // Only once there is something to practise: a panel over decks
                // with nothing in them is a question with no answer.
                if (list.any { it.cardCount > 0 }) {
                    item(key = "study") {
                        PracticePanel(
                            direction = direction,
                            dueCount = dueByDeck.values.sumOf { it.dueIn(direction) },
                            onChooseDirection = viewModel::onChooseDirection,
                            onStudy = { onStudy(null, direction) },
                        )
                    }
                }
                items(list, key = { it.id }) { deck ->
                    DeckCard(
                        deck = deck,
                        direction = direction,
                        dueCount = dueByDeck[deck.id]?.dueIn(direction) ?: 0,
                        onOpen = { onOpenDeck(deck.id) },
                        onPractise = { onStudy(deck.id, direction) },
                        onRename = { renaming = deck },
                        onDelete = { deleting = deck },
                    )
                }
                item(key = "backup") {
                    BackupPanel(isBusy = backupBusy, onExport = onExport, onRestore = onRestore)
                }
            }
        }
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
 * One deck, as a card of its own.
 *
 * Its cover is the first picture in it, so a deck of food looks like food; one
 * without pictures shows its initial. Under the name, how well the deck is
 * known this way round, as a bar rather than a number — how far through a deck
 * you are is the same kind of fact as how far through a book, and the library
 * already answers that with a bar. On the right, the deck's own way into
 * practice, carrying how many are waiting: the deck is the choice, so the
 * button is on it rather than in a list of decks somewhere else.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckCard(
    deck: DeckWithCount,
    direction: StudyDirection,
    /** Due in the direction currently chosen, so the numbers agree with the panel. */
    dueCount: Int,
    onOpen: () -> Unit,
    onPractise: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
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
            Text(
                text = deck.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                    modifier = Modifier
                        .padding(top = Space.sm)
                        .fillMaxWidth(0.8f)
                        .height(6.dp),
                )
            }
        }

        if (dueCount > 0) {
            // A play mark and a number read at a glance; said out loud they
            // need the words.
            val practiseLabel = stringResource(R.string.flashcards_practise_deck, deck.name, dueCount)
            FilledTonalButton(
                onClick = onPractise,
                contentPadding = PaddingValues(horizontal = Space.md),
                modifier = Modifier
                    .padding(start = Space.sm)
                    .clearAndSetSemantics {
                        contentDescription = practiseLabel
                        role = Role.Button
                    },
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "$dueCount",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = Space.xs),
                )
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

/** The first picture in the deck, or its initial on a tinted tile. */
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
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = deck.name.trim().take(1).uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
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
        StudyDirection.CATALAN_TO_ROMANIAN -> boxTotal
        StudyDirection.ROMANIAN_TO_CATALAN -> reverseBoxTotal
    }
    return (total.toFloat() / (cardCount * Leitner.LAST_BOX)).coerceIn(0f, 1f)
}

private val CoverSize = 64.dp

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

/** Clears the extended button: its 56dp, and the 16dp it floats above the bar. */
private val BottomClearance = 88.dp

/** How many of the deck's cards are due in [direction]. */
private fun DeckDue.dueIn(direction: StudyDirection): Int = when (direction) {
    StudyDirection.CATALAN_TO_ROMANIAN -> forwardDue
    StudyDirection.ROMANIAN_TO_CATALAN -> reverseDue
}

/**
 * Practice over every deck, and which way round.
 *
 * The one place on the tab that is about the whole collection rather than one
 * deck, so it is the one thing with colour behind it. The number is the point:
 * how many cards are waiting, large enough to read from across a room. The
 * direction is two flags a thumb can tell apart without reading anything.
 */
@Composable
private fun PracticePanel(
    direction: StudyDirection,
    dueCount: Int,
    onChooseDirection: (StudyDirection) -> Unit,
    onStudy: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen)
            .padding(top = Space.sm, bottom = Space.md)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(Space.xl),
    ) {
        val onPanel = MaterialTheme.colorScheme.onPrimaryContainer
        Text(
            text = stringResource(R.string.flashcards_study_title),
            style = MaterialTheme.typography.labelLarge,
            color = onPanel.copy(alpha = 0.8f),
        )
        if (dueCount > 0) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = Space.xs)) {
                Text(
                    text = "$dueCount",
                    style = MaterialTheme.typography.displayMedium,
                    color = onPanel,
                )
                Text(
                    text = pluralStringResource(R.plurals.flashcards_panel_due, dueCount),
                    style = MaterialTheme.typography.titleMedium,
                    color = onPanel,
                    modifier = Modifier.padding(start = Space.sm, bottom = Space.sm),
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.sm)) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = onPanel,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = stringResource(R.string.flashcards_panel_all_done),
                    style = MaterialTheme.typography.headlineSmall,
                    color = onPanel,
                    modifier = Modifier.padding(start = Space.sm),
                )
            }
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.lg),
        ) {
            StudyDirection.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = entry == direction,
                    onClick = { onChooseDirection(entry) },
                    shape = SegmentedButtonDefaults.itemShape(index, StudyDirection.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.surface,
                        inactiveContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeBorderColor = onPanel.copy(alpha = 0.4f),
                        inactiveBorderColor = onPanel.copy(alpha = 0.4f),
                    ),
                    // The flags say which is chosen as well as any tick would,
                    // and a tick beside them would crowd two flags and an arrow.
                    icon = {},
                    label = { DirectionFlags(entry) },
                )
            }
        }

        Button(
            onClick = onStudy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.md)
                .height(52.dp),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text(
                text = stringResource(
                    if (dueCount > 0) R.string.flashcards_panel_start else R.string.flashcards_study_start,
                ),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = Space.sm),
            )
        }
    }
}

/**
 * Saving a copy of the cards, and bringing one back.
 *
 * At the foot of the decks, in the open, with a line saying why it matters:
 * the cards are on this phone and nowhere else, which is a fact nobody would
 * guess from the rest of the screen and the one that decides whether a copy
 * is worth making.
 */
@Composable
private fun BackupPanel(isBusy: Boolean, onExport: () -> Unit, onRestore: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen)
            .padding(top = Space.xl)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(Space.lg),
    ) {
        Text(
            text = stringResource(R.string.flashcards_backup_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.flashcards_backup_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.xs),
        )
        if (isBusy) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.md),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            modifier = Modifier.padding(top = Space.md),
        ) {
            OutlinedButton(onClick = onExport, enabled = !isBusy) {
                Text(stringResource(R.string.flashcards_backup_save))
            }
            TextButton(onClick = onRestore, enabled = !isBusy) {
                Text(stringResource(R.string.flashcards_backup_restore))
            }
        }
    }
}

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
