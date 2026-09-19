package com.david.llegeix.ui.flashcards

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.data.db.dao.DeckWithCount
import com.david.llegeix.data.flashcards.DeckNames
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
    modifier: Modifier = Modifier,
    viewModel: FlashcardsViewModel = viewModel(factory = FlashcardsViewModel.Factory),
) {
    val decks by viewModel.decks.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
            )

            else -> LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                // Room under the last row for the button, so it can always be
                // scrolled clear of it.
                contentPadding = PaddingValues(top = Space.sm, bottom = BottomClearance),
            ) {
                items(list, key = { it.id }) { deck ->
                    DeckRow(
                        deck = deck,
                        onOpen = { onOpenDeck(deck.id) },
                        onRename = { renaming = deck },
                        onDelete = { deleting = deck },
                    )
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
            text = {
                Text(
                    if (deck.cardCount == 0) {
                        stringResource(R.string.flashcards_delete_deck_body_empty)
                    } else {
                        pluralStringResource(
                            R.plurals.flashcards_delete_deck_body,
                            deck.cardCount,
                            deck.cardCount,
                        )
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
                    pluralStringResource(
                        R.plurals.flashcards_card_count,
                        deck.cardCount,
                        deck.cardCount,
                    )
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
