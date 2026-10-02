package com.david.llegeix.ui.flashcards

import com.david.llegeix.ui.platform.onThisDevice
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.painter.Painter
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.data.db.dao.DeckWithCount
import com.david.llegeix.data.flashcards.DeckNames
import com.david.llegeix.data.flashcards.PictureResults
import com.david.llegeix.data.flashcards.StudyDirection
import com.david.llegeix.data.flashcards.StudyScope
import com.david.llegeix.data.practice.Leitner
import com.david.llegeix.resources.*
import com.david.llegeix.ui.common.AppBottomSheet
import com.david.llegeix.ui.common.AppSnackbarHost
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.MenuIcon
import com.david.llegeix.ui.common.Pill
import com.david.llegeix.ui.common.PillGroup
import com.david.llegeix.ui.common.ScreenTitle
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.common.resolved
import com.david.llegeix.ui.platform.PlatformBackHandler
import com.david.llegeix.ui.platform.rememberDocumentCreator
import com.david.llegeix.ui.platform.rememberDocumentOpener
import com.david.llegeix.ui.platform.rememberPicturePicker
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
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
 * Those settings are not on the tab at all now. Which languages and which way
 * round is asked when play is pressed — [PlayPairSheet] — which is the moment
 * it matters. What is left below the bar is the one thing anybody came to
 * this tab to do that is not about a particular deck: go through all of it.
 * The decks begin immediately underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsScreen(
    onOpenDeck: (deckId: Long) -> Unit,
    onOpenWeak: () -> Unit,
    onStudy: (scope: StudyScope, direction: StudyDirection) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FlashcardsViewModel = viewModel(factory = FlashcardsViewModel.Factory),
) {
    val list by viewModel.list.collectAsStateWithLifecycle()
    val current by viewModel.current.collectAsStateWithLifecycle()
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val direction by viewModel.direction.collectAsStateWithLifecycle()
    val weakCount by viewModel.weakCount.collectAsStateWithLifecycle()
    val backupBusy by viewModel.backupBusy.collectAsStateWithLifecycle()

    // The system's own file screens, so the copy goes wherever the reader
    // keeps things — Downloads, a memory card, a cloud drive they chose — and
    // the app is never given more than the one file.
    val exportLauncher = rememberDocumentCreator(BACKUP_MIME_TYPE, viewModel::onExport)
    val restoreLauncher = rememberDocumentOpener(viewModel::onRestore)
    val backupName = stringResource(
        Res.string.flashcards_backup_filename,
        LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    )
    val onExport = { exportLauncher(backupName) }
    val onRestore = { restoreLauncher(BACKUP_OPEN_TYPES) }
    val snackbarHostState = remember { SnackbarHostState() }

    var creating by remember { mutableStateOf(false) }
    var newDeckCount by remember { mutableIntStateOf(0) }
    var renaming by remember { mutableStateOf<DeckWithCount?>(null) }
    var deleting by remember { mutableStateOf<DeckWithCount?>(null) }
    var picturing by remember { mutableStateOf<DeckWithCount?>(null) }
    var picturingShelf by remember { mutableStateOf<DeckShelf?>(null) }
    var moving by remember { mutableStateOf<DeckWithCount?>(null) }
    var movingShelf by remember { mutableStateOf<DeckShelf?>(null) }
    var creatingInside by remember { mutableStateOf<DeckShelf?>(null) }
    var renamingShelf by remember { mutableStateOf<DeckShelf?>(null) }
    var deletingShelf by remember { mutableStateOf<DeckShelf?>(null) }
    var showingBackup by remember { mutableStateOf(false) }

    val messageText = message?.resolved()
    LaunchedEffect(messageText) {
        val text = messageText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onMessageShown()
    }

    /**
     * Whether a set of decks has anything to ask. Every card has its Romanian,
     * so any card at all is something; the English is asked about when play is
     * pressed, and only offered where there is some.
     */
    fun canPractise(decks: List<DeckWithCount>): Boolean = decks.any { it.cardCount > 0 }

    // Play asks which way round every time; this is what it is asking about.
    var asking by remember { mutableStateOf<PlayRequest?>(null) }
    val everything = stringResource(Res.string.flashcards_practise_all)
    val weakTitle = stringResource(Res.string.flashcards_weak_title)
    val practiseName = stringResource(Res.string.flashcards_practise_name)
    fun play(scope: StudyScope, title: String) {
        asking = PlayRequest(scope, title)
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
                        icon = Res.drawable.ic_flashcards,
                        title = stringResource(Res.string.nav_flashcards),
                    )
                },
                actions = {
                    val sort by viewModel.sort.collectAsStateWithLifecycle()
                    LayoutMenu(selected = layout, onSelect = viewModel::onLayout)
                    ListSortMenu(selected = sort, onSelect = viewModel::onSort)
                    IconButton(onClick = { showingBackup = true }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_backup),
                            contentDescription = stringResource(Res.string.flashcards_backup_title),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { newDeckCount++; creating = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.flashcards_new_deck))
            }
        },
    ) { innerPadding ->
        val loaded = list
        when {
            // Still asking the database: draw nothing rather than a false
            // "no decks yet" for one frame.
            loaded == null -> Box(Modifier.padding(innerPadding))

            loaded.isEmpty -> EmptyState(
                title = stringResource(Res.string.flashcards_empty_title),
                body = stringResource(Res.string.flashcards_empty_body),
                icon = painterResource(Res.drawable.ic_flashcards),
                modifier = Modifier.padding(innerPadding),
                // A new phone, or the app put back after an uninstall, starts
                // here — which is exactly when a saved copy is wanted, so the
                // way to bring one back is on the screen, not behind a button.
                secondaryAction = {
                    TextButton(onClick = onRestore, enabled = !backupBusy) {
                        Text(stringResource(Res.string.flashcards_backup_restore))
                    }
                },
            )

            else -> {
                // Inside a collection, the system's back goes up a level
                // before it leaves the tab.
                PlatformBackHandler(enabled = current != null) { viewModel.onUp() }
                val here = current?.let(loaded::shelf)
                val entries = here?.entries ?: loaded.entries
                val actions = EntryActions(
                    openShelf = { viewModel.onOpenShelf(it.id) },
                    openDeck = { onOpenDeck(it.id) },
                    playShelf = { play(StudyScope.Collection(it.id), practiseName.format(it.collection.name)) },
                    playDeck = { play(StudyScope.Deck(it.id), practiseName.format(it.name)) },
                    pinShelf = { viewModel.setCollectionPinned(it.id, !it.collection.isPinned) },
                    pinDeck = { viewModel.setPinned(it, !it.isPinned) },
                    pictureShelf = { picturingShelf = it },
                    pictureDeck = { picturing = it },
                    moveShelf = { movingShelf = it },
                    moveDeck = { moving = it },
                    newInside = { newDeckCount++; creatingInside = it },
                    renameShelf = { renamingShelf = it },
                    renameDeck = { renaming = it },
                    deleteShelf = { deletingShelf = it },
                    deleteDeck = { deleting = it },
                )
                fun practisable(entry: ListEntry) = when (entry) {
                    is ListEntry.Shelf -> canPractise(entry.shelf.allDecks)
                    is ListEntry.Deck -> canPractise(listOf(entry.deck))
                }
                val columns = if (layout == ListLayout.GRID) 2 else 1
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    // Room under the last one for the + button; the grid's
                    // own edges, since its cards carry their own margins.
                    contentPadding = PaddingValues(top = Space.xs, bottom = BottomClearance),
                ) {
                    val full: (LazyGridItemSpanScope) -> GridItemSpan = { GridItemSpan(it.maxLineSpan) }
                    val wide = Modifier
                    if (here == null) {
                        // Only once there is something to practise.
                        if (loaded.hasCards && canPractise(loaded.allDecks)) {
                            item(key = "practice", span = full) {
                                Box(wide) {
                                    PractiseEverything(onStudy = { play(StudyScope.Everything, everything) })
                                }
                            }
                        }
                        // The weak words, like a folder of their own above the
                        // rest, only once there are some.
                        if (weakCount > 0) {
                            item(key = "weak", span = full) {
                                Box(wide) {
                                    WeakRow(
                                        count = weakCount,
                                        onOpen = onOpenWeak,
                                        onPractise = { play(StudyScope.Weak, weakTitle) },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                    } else {
                        item(key = "header-${here.id}", span = full) {
                            Box(wide) {
                                CollectionHeader(
                                    path = loaded.pathTo(here.id),
                                    canPractise = canPractise(here.allDecks),
                                    onGoTo = viewModel::onGoTo,
                                    actions = actions,
                                )
                            }
                        }
                        if (here.isEmpty) {
                            item(key = "empty-${here.id}", span = full) {
                                Text(
                                    text = stringResource(Res.string.flashcards_collection_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = wide.padding(horizontal = Space.screen, vertical = Space.lg),
                                )
                            }
                        }
                    }
                    when (layout) {
                        ListLayout.LIST -> items(entries, key = { it.key }, span = { full(this) }) { entry ->
                            EntryRow(entry, practisable(entry), actions, Modifier.animateItem())
                        }
                        ListLayout.COMPACT -> itemsIndexed(entries, key = { _, it -> it.key }, span = { _, _ -> full(this) }) { index, entry ->
                            CompactRow(
                                entry = entry,
                                canPractise = practisable(entry),
                                actions = actions,
                                isFirst = index == 0,
                                isLast = index == entries.lastIndex,
                                modifier = Modifier.animateItem(),
                            )
                        }
                        ListLayout.GRID -> itemsIndexed(entries, key = { _, it -> it.key }) { index, entry ->
                            // Each card pads itself out to the screen margin
                            // on its own side of the grid.
                            val edge = Space.screen - 6.dp
                            EntryTile(
                                entry,
                                practisable(entry),
                                actions,
                                Modifier
                                    .animateItem()
                                    .padding(
                                        start = if (index % 2 == 0) edge else 0.dp,
                                        end = if (index % 2 == 1) edge else 0.dp,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }

    asking?.let { request ->
        PlayPairSheet(
            request = request,
            lastDirection = direction,
            onDismiss = { asking = null },
            onChoose = { way ->
                // Remembered, so it is the row lit next time.
                viewModel.onChooseDirection(way)
                onStudy(request.scope, way)
            },
        )
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
        MoveSheet(
            name = deck.name,
            current = deck.collectionId,
            shelves = list?.allShelves.orEmpty(),
            onDismiss = { moving = null },
            onMove = { collectionId -> viewModel.moveDeck(deck, collectionId) },
            emptyHint = list?.allShelves.orEmpty().isEmpty(),
        )
    }

    movingShelf?.let { shelf ->
        // Not into itself, nor into anything inside it: that would take the
        // whole branch out of the tree.
        val own = shelf.allShelves.mapTo(HashSet()) { it.id }
        MoveSheet(
            name = shelf.collection.name,
            current = shelf.collection.parentId,
            shelves = list?.allShelves.orEmpty().filter { it.id !in own },
            onDismiss = { movingShelf = null },
            onMove = { parentId -> viewModel.moveCollection(shelf, parentId) },
            emptyHint = false,
        )
    }

    if (creating) {
        // A fresh sheet each time it is opened, so the last attempt's name and
        // picture are not waiting in it.
        // Inside a collection, what the + makes goes inside it.
        NewDeckSheet(
            key = newDeckCount,
            onDismiss = { creating = false },
            parent = current?.let { id -> list?.shelf(id) },
        )
    }

    renaming?.let { deck ->
        NameDialog(
            title = stringResource(Res.string.flashcards_rename_deck_title),
            label = stringResource(Res.string.flashcards_deck_name_label),
            hint = stringResource(Res.string.flashcards_deck_name_hint),
            takenRes = Res.string.flashcards_deck_exists,
            confirmLabel = stringResource(Res.string.action_rename),
            initialName = deck.name,
            check = { viewModel.checkName(it, renaming = deck.id) },
            onDismiss = { renaming = null },
            onConfirm = { name ->
                viewModel.renameDeck(deck.id, name)
                renaming = null
            },
        )
    }

    creatingInside?.let { parent ->
        NewDeckSheet(
            key = newDeckCount,
            parent = parent,
            startKind = NewDeckKind.COLLECTION,
            onDismiss = { creatingInside = null },
            onCreated = { id -> id?.let(viewModel::revealInside) },
        )
    }

    renamingShelf?.let { shelf ->
        NameDialog(
            title = stringResource(Res.string.flashcards_rename_collection_title),
            label = stringResource(Res.string.flashcards_collection_name_label),
            hint = stringResource(Res.string.flashcards_collection_name_hint),
            takenRes = Res.string.flashcards_collection_exists,
            confirmLabel = stringResource(Res.string.action_rename),
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
            title = { Text(stringResource(Res.string.flashcards_delete_collection_title, shelf.collection.name)) },
            // The one thing anybody wants to know before deleting a shelf is
            // what happens to what was on it. Nothing happens to it, and saying
            // so is the difference between a tidy-up and a moment of panic.
            text = {
                val parent = list?.allShelves.orEmpty()
                    .firstOrNull { it.id == shelf.collection.parentId }
                Text(
                    when {
                        shelf.isEmpty -> stringResource(Res.string.flashcards_delete_collection_body_empty)
                        parent != null -> stringResource(
                            Res.string.flashcards_delete_collection_body_up_to,
                            parent.collection.name,
                        )
                        else -> stringResource(Res.string.flashcards_delete_collection_body_up_top)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCollection(shelf)
                        deletingShelf = null
                    },
                ) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingShelf = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    deleting?.let { deck ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = {
                Text(stringResource(Res.string.flashcards_delete_deck_title, deck.name))
            },
            // Says exactly what goes, in numbers: a deck is hand-made work, and
            // "delete this deck?" alone does not tell anybody it is sixty cards
            // and twelve photographs they chose one by one.
            text = {
                Text(
                    if (deck.cardCount == 0) {
                        stringResource(Res.string.flashcards_delete_deck_body_empty)
                    } else {
                        listOfNotNull(
                            pluralStringResource(
                                Res.plurals.flashcards_delete_deck_body,
                                deck.cardCount,
                                deck.cardCount,
                            ),
                            when {
                                deck.imageCount == 0 -> null
                                // "One of them" when there is only one reads as a slip.
                                deck.cardCount == 1 ->
                                    stringResource(Res.string.flashcards_delete_deck_only_picture)
                                else -> pluralStringResource(
                                    Res.plurals.flashcards_delete_deck_pictures,
                                    deck.imageCount,
                                    deck.imageCount,
                                )
                            },
                            stringResource(Res.string.flashcards_cannot_undo),
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
                ) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(Res.string.action_cancel))
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
                text = stringResource(Res.string.flashcards_practise_all),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = Space.sm),
            )
        }
    }
}

/**
 * The weak words, as a row of their own at the top of the tab: a smart folder
 * that fills itself with every word answered "Encara no", tinted so it is
 * never mistaken for a collection the reader made. The row opens the list;
 * its play button reviews exactly those words.
 */
@Composable
private fun WeakRow(count: Int, onOpen: () -> Unit, onPractise: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen, vertical = RowGap)
            .clip(RoundedCornerShape(22.dp))
            .background(scheme.tertiaryContainer)
            .clickable(onClick = onOpen)
            .padding(horizontal = Space.lg, vertical = RowInset),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.tertiary),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_weak),
                contentDescription = null,
                tint = scheme.onTertiary,
                modifier = Modifier.size(26.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.lg),
        ) {
            Text(
                text = stringResource(Res.string.flashcards_weak_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = scheme.onTertiaryContainer,
            )
            Text(
                text = pluralStringResource(Res.plurals.flashcards_card_count, count, count) + " · " +
                    stringResource(Res.string.flashcards_weak_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onTertiaryContainer.copy(alpha = 0.8f),
                maxLines = 2,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        PlayButton(
            onClick = onPractise,
            contentDescription = stringResource(Res.string.flashcards_weak_practise),
            modifier = Modifier.padding(start = Space.sm),
        )
    }
}

/**
 * Where a deck or a shelf goes, chosen from the tree as it stands.
 *
 * The top level first, then every shelf in the order the list shows them,
 * each stepped in under the one it is inside — so the sheet is a small map of
 * the tab rather than a flat list of names that could be anywhere. Where the
 * thing is now is lit and says so; tapping anywhere else moves it, and the
 * sheet slides away before the list below rearranges, so the move is seen
 * happening rather than arriving already done.
 *
 * Only places that exist are offered. Making a collection is what the + does,
 * and offering it here as well would be the same decision reachable two ways.
 *
 * @param current the shelf it is in now, or null for the top level.
 * @param emptyHint say that there are no collections yet, and how to make one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveSheet(
    name: String,
    current: Long?,
    shelves: List<DeckShelf>,
    onDismiss: () -> Unit,
    onMove: (collectionId: Long?) -> Unit,
    emptyHint: Boolean,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // A second tap while the sheet is closing would move it twice.
    var chosen by remember { mutableStateOf(false) }
    val choose: (Long?) -> Unit = { target ->
        if (!chosen) {
            chosen = true
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                if (target != current) onMove(target)
                onDismiss()
            }
        }
    }
    // The current place is known even when its shelf has gone, as the top.
    val here = current?.takeIf { id -> shelves.any { it.id == id } }

    AppBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Space.xl)
                .navigationBarsPadding(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.sm),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_move),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = stringResource(Res.string.flashcards_move_title, name),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Space.md),
                )
            }
            if (emptyHint) {
                Text(
                    text = stringResource(Res.string.flashcards_no_collections),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = Space.screen)
                        .padding(top = Space.xs, bottom = Space.sm),
                )
            }
            Spacer(Modifier.height(Space.sm))
            MoveChoice(
                icon = painterResource(Res.drawable.ic_flashcards),
                name = stringResource(Res.string.flashcards_move_none),
                detail = stringResource(Res.string.flashcards_move_top_hint),
                depth = 0,
                isHere = here == null,
                onClick = { choose(null) },
            )
            shelves.forEach { shelf ->
                MoveChoice(
                    icon = painterResource(Res.drawable.ic_collection),
                    name = shelf.collection.name,
                    detail = null,
                    // The top level is depth 0, so a shelf at the top is one in.
                    depth = shelf.depth.coerceAtMost(MaxDrawnDepth) + 1,
                    isHere = here == shelf.id,
                    onClick = { choose(shelf.id) },
                )
            }
        }
    }
}

/** One place in [MoveSheet]: a rounded row, stepped in by its depth, lit when it is where the thing is now. */
@Composable
private fun MoveChoice(
    icon: Painter,
    name: String,
    detail: String?,
    depth: Int,
    isHere: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.md + MoveStep * depth, end = Space.md, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isHere) scheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = Space.md, vertical = Space.sm),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isHere) scheme.surface.copy(alpha = 0.6f) else scheme.surfaceContainerHighest),
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = if (isHere) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Space.md),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isHere) scheme.onSecondaryContainer else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isHere) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isHere) {
            Text(
                text = stringResource(Res.string.flashcards_move_here_now),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSecondaryContainer,
                modifier = Modifier.padding(end = Space.xs),
            )
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = scheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
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
private fun NewDeckSheet(
    key: Int,
    onDismiss: () -> Unit,
    /** Make it inside this collection rather than at the top of the list. */
    parent: DeckShelf? = null,
    /** Which kind the sheet opens on. */
    startKind: NewDeckKind = NewDeckKind.DECK,
    onCreated: (parentId: Long?) -> Unit = {},
) {
    val viewModel: NewDeckViewModel = viewModel(key = "new-deck-$key", factory = NewDeckViewModel.Factory)
    LaunchedEffect(parent?.id, startKind) {
        viewModel.setParent(parent?.id, parent?.collection?.name)
        viewModel.onKindChange(startKind)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val suggestions by viewModel.pictures.state.collectAsStateWithLifecycle()
    val source by viewModel.pictures.source.collectAsStateWithLifecycle()
    val created by viewModel.created.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val pickOwn = rememberPicturePicker(viewModel::onPickOwn)
    val close = {
        viewModel.onCancel()
        onDismiss()
    }
    LaunchedEffect(created) {
        if (created) {
            onCreated(state.parentId)
            onDismiss()
        }
    }
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
            state.parentName?.let { inside ->
                Text(
                    text = stringResource(Res.string.flashcards_new_inside_title, inside),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = Space.md),
                )
            }
            PillGroup(modifier = Modifier.fillMaxWidth()) {
                NewDeckKind.entries.forEach { kind ->
                    val name = stringResource(
                        when (kind) {
                            NewDeckKind.DECK -> Res.string.flashcards_kind_deck
                            NewDeckKind.COLLECTION -> Res.string.flashcards_kind_collection
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
                                Res.string.flashcards_deck_name_label
                            } else {
                                Res.string.flashcards_collection_name_label
                            },
                        ),
                    )
                },
                placeholder = {
                    Text(
                        stringResource(
                            if (isDeck) {
                                Res.string.flashcards_deck_name_hint
                            } else {
                                Res.string.flashcards_collection_name_hint
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
                                    Res.string.flashcards_deck_exists
                                } else {
                                    Res.string.flashcards_collection_exists
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

            // A picture for either kind, found from the name as it is typed.
            run {
                Text(
                    text = stringResource(Res.string.flashcards_picture),
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
                        contentDescription = stringResource(Res.string.flashcards_picture_other),
                        kind = PictureResults.kindOf(state.coverCredit),
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(
                                onClickLabel = stringResource(Res.string.flashcards_picture_other),
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
                            pickOwn()
                        },
                        onRetry = viewModel::onRetry,
                        onSearch = viewModel::onSearchPictures,
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
                    Text(stringResource(Res.string.action_cancel))
                }
                Button(
                    onClick = viewModel::onCreate,
                    enabled = state.check is DeckNames.Check.Ok && !state.isBusy,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(Res.string.action_create)) }
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
    val pickOwn = rememberPicturePicker(viewModel::onPickOwn)
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
                text = stringResource(Res.string.flashcards_deck_picture_title, deck.name),
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
                    pickOwn()
                },
                onRetry = viewModel::onRetry,
                onSearch = viewModel::onSearchPictures,
            )
            if (deck.chosenCover != null) {
                OutlinedButton(
                    onClick = viewModel::onRemove,
                    modifier = Modifier.padding(top = Space.lg),
                ) { Text(stringResource(Res.string.flashcards_deck_picture_remove)) }
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
    val pickOwn = rememberPicturePicker(viewModel::onPickOwn)
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
                    Res.string.flashcards_deck_picture_title,
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
                    pickOwn()
                },
                onRetry = viewModel::onRetry,
                onSearch = viewModel::onSearchPictures,
            )
            if (shelf.chosenCover != null) {
                OutlinedButton(
                    onClick = viewModel::onRemove,
                    modifier = Modifier.padding(top = Space.lg),
                ) { Text(stringResource(Res.string.flashcards_collection_picture_remove)) }
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
            text = stringResource(Res.string.flashcards_backup_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(onThisDevice(Res.string.flashcards_backup_body, Res.string.flashcards_backup_body_mac)),
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
                Text(stringResource(Res.string.flashcards_backup_save))
            }
            OutlinedButton(onClick = onRestore, enabled = !isBusy, modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.flashcards_backup_restore))
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
    takenRes: StringResource,
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
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}



/** Half the space between two rows of the tab: room to breathe between them. */
private val RowGap = 8.dp

/** The space inside a row above and below its content. */
private val RowInset = 14.dp

/**
 * How deep the indent keeps going. Past this a shelf inside a shelf is drawn at
 * the same step as its parent, rather than squeezing its name into a sliver at
 * the right edge of the phone.
 */
private const val MaxDrawnDepth = 3

/** A level of the tree in the move sheet: tighter than the list's, since it has no covers. */
private val MoveStep = 16.dp

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
