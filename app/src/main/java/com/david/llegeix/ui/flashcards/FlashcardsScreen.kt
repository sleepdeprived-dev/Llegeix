package com.david.llegeix.ui.flashcards

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    val studyDeck by viewModel.studyDeck.collectAsStateWithLifecycle()
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
                // Only once there is something to study: a panel of choices
                // over decks with nothing in them is a question with no answer.
                val studyable = list.filter { it.cardCount > 0 }
                if (studyable.isNotEmpty()) {
                    item(key = "study") {
                        // A deck that has since been deleted or emptied quietly
                        // falls back to all of them.
                        val chosen = studyDeck?.takeIf { id -> studyable.any { it.id == id } }
                        StudyPanel(
                            decks = studyable,
                            chosenDeck = chosen,
                            direction = direction,
                            dueCount = if (chosen == null) {
                                dueByDeck.values.sumOf { it.dueIn(direction) }
                            } else {
                                dueByDeck[chosen]?.dueIn(direction) ?: 0
                            },
                            onChooseDeck = viewModel::onChooseStudyDeck,
                            onChooseDirection = viewModel::onChooseDirection,
                            onStudy = { onStudy(chosen, direction) },
                        )
                    }
                }
                items(list, key = { it.id }) { deck ->
                    DeckRow(
                        deck = deck,
                        dueCount = dueByDeck[deck.id]?.dueIn(direction) ?: 0,
                        onOpen = { onOpenDeck(deck.id) },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckRow(
    deck: DeckWithCount,
    /** Due in the direction currently chosen, so the numbers agree with the panel. */
    dueCount: Int,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })
            .padding(start = Space.screen, top = Space.row, bottom = Space.row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_flashcards),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.lg),
        ) {
            Text(
                text = deck.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (deck.cardCount == 0) {
                    stringResource(R.string.flashcards_deck_no_cards)
                } else {
                    listOfNotNull(
                        pluralStringResource(
                            R.plurals.flashcards_card_count,
                            deck.cardCount,
                            deck.cardCount,
                        ),
                        dueCount.takeIf { it > 0 }?.let {
                            pluralStringResource(R.plurals.flashcards_due_count, it, it)
                        },
                    ).joinToString(" · ")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
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
 * What to study, chosen in the open.
 *
 * Deck and direction are two rows of choices with the current one lit, rather
 * than a menu or a dialog in front of the session: which deck and which way
 * round are the two facts a session is made of, and the reader should be able
 * to see both before pressing the button, not find out after.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyPanel(
    decks: List<DeckWithCount>,
    chosenDeck: Long?,
    direction: StudyDirection,
    dueCount: Int,
    onChooseDeck: (Long?) -> Unit,
    onChooseDirection: (StudyDirection) -> Unit,
    onStudy: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen)
            .padding(bottom = Space.lg)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = Space.lg),
    ) {
        Text(
            text = stringResource(R.string.flashcards_study_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Space.lg),
        )

        // Decks scroll sideways rather than wrapping, so a long list of decks
        // costs one row of height rather than five.
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Space.lg)
                .padding(top = Space.md),
        ) {
            FilterChip(
                selected = chosenDeck == null,
                onClick = { onChooseDeck(null) },
                label = { Text(stringResource(R.string.flashcards_all_decks)) },
            )
            decks.forEach { deck ->
                FilterChip(
                    selected = chosenDeck == deck.id,
                    onClick = { onChooseDeck(deck.id) },
                    label = { Text(deck.name, maxLines = 1) },
                )
            }
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg)
                .padding(top = Space.sm),
        ) {
            StudyDirection.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = entry == direction,
                    onClick = { onChooseDirection(entry) },
                    shape = SegmentedButtonDefaults.itemShape(index, StudyDirection.entries.size),
                    label = { Text(stringResource(directionLabel(entry)), maxLines = 1) },
                )
            }
        }

        Button(
            onClick = onStudy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg)
                .padding(top = Space.lg),
        ) {
            Text(
                if (dueCount > 0) {
                    pluralStringResource(R.plurals.flashcards_study_cards, dueCount, dueCount)
                } else {
                    stringResource(R.string.flashcards_study_start)
                },
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
