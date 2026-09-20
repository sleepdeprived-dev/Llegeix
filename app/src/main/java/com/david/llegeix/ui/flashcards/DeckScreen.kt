package com.david.llegeix.ui.flashcards

import com.david.llegeix.data.flashcards.PictureResults
import androidx.compose.foundation.Image
import androidx.compose.ui.res.pluralStringResource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.data.db.entity.FlashcardEntity
import com.david.llegeix.ui.common.AppSnackbarHost
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.MenuIcon
import com.david.llegeix.ui.common.SearchField
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.common.resolved

/**
 * The cards in one deck, in Catalan alphabetical order.
 *
 * Pressing a card opens it to be changed, because a card is the words on it
 * and changing them is the only thing to do with one here; studying it is the
 * business of another screen. Holding it, or its three dots, also offers to
 * delete it without opening it first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckScreen(
    deckId: Long,
    onBack: () -> Unit,
    onAddCard: () -> Unit,
    onEditCard: (cardId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeckViewModel = viewModel(key = "deck-$deckId", factory = DeckViewModel.factory(deckId)),
) {
    val deck by viewModel.deck.collectAsStateWithLifecycle()
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val shown by viewModel.shown.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val missingEnglish by viewModel.missingEnglish.collectAsStateWithLifecycle()
    val fillingEnglish by viewModel.fillingEnglish.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var deleting by remember { mutableStateOf<FlashcardEntity?>(null) }

    val messageText = message?.resolved()
    LaunchedEffect(messageText) {
        val text = messageText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onMessageShown()
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
                    Text(
                        text = deck?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCard) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.flashcards_add_card))
            }
        },
    ) { innerPadding ->
        val list = cards
        when {
            list == null -> Box(Modifier.padding(innerPadding))

            list.isEmpty() -> EmptyState(
                title = stringResource(R.string.flashcards_deck_empty_title),
                body = stringResource(R.string.flashcards_deck_empty_body),
                icon = painterResource(R.drawable.ic_flashcards),
                modifier = Modifier.padding(innerPadding),
            )

            else -> Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                // Above the list rather than in it, so it stays put while the
                // cards scroll under it: a deck is searched while looking
                // through it, not before.
                SearchField(
                    query = query,
                    placeholder = stringResource(R.string.flashcards_search_deck),
                    onQueryChange = viewModel::onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.screen)
                        .padding(top = Space.sm),
                )
                val found = shown.orEmpty()
                if (found.isEmpty() && query.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.flashcards_search_none, query.trim()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.xl),
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = Space.sm, bottom = CardListBottomClearance),
                ) {
                    // No pair of direction bars here any more.
                    //
                    // They said how well the deck was known each way round,
                    // under two pairs of flags, directly below the search field
                    // — and the tab this screen is opened from asks the very
                    // same question with the very same flags, two taps earlier
                    // and about every deck at once. Two controls saying the
                    // same thing in the same language is one too many, and this
                    // was the one in the way: it sat between the search field
                    // and the cards, which are the two reasons anybody opens a
                    // deck.
                    if (query.isBlank()) {
                        if (missingEnglish > 0) {
                            item(key = "english") {
                                MissingEnglish(
                                    count = missingEnglish,
                                    isFilling = fillingEnglish,
                                    onFill = viewModel::onFillEnglish,
                                )
                            }
                        }
                    }
                    items(found, key = { it.id }) { card ->
                        CardRow(
                            card = card,
                            onOpen = { onEditCard(card.id) },
                            onDelete = { deleting = card },
                        )
                    }
                }
            }
        }
    }

    deleting?.let { card ->
        DeleteCardDialog(
            word = card.catalan,
            hasPicture = card.imagePath != null,
            onDismiss = { deleting = null },
            onConfirm = {
                viewModel.deleteCard(card)
                deleting = null
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardRow(
    card: FlashcardEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })
            .padding(start = Space.md, top = Space.md, bottom = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Every row keeps the square, picture or not, so the words line up
        // down the list instead of stepping in and out.
        val thumbnail = Modifier
            .size(ThumbnailSize)
            .clip(RoundedCornerShape(12.dp))
        val path = card.imagePath
        if (path != null) {
            CardImage(
                path = path,
                maxEdge = ThumbnailPixels,
                contentDescription = null,
                pictogram = PictureResults.isPictogram(card.imageCredit),
                modifier = thumbnail,
            )
        } else {
            Box(
                modifier = thumbnail.background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_flashcards),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.lg),
        ) {
            Text(
                text = card.catalan,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = card.romanian,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more),
                    contentDescription = stringResource(R.string.document_actions, card.catalan),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    leadingIcon = { MenuIcon(Icons.Default.Edit) },
                    text = { Text(stringResource(R.string.flashcards_edit_card)) },
                    onClick = { onOpen(); menuOpen = false },
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

/** Asked before a card goes, from the list and from the card itself alike. */
@Composable
internal fun DeleteCardDialog(
    word: String,
    hasPicture: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.flashcards_delete_card_title, word)) },
        text = {
            Text(
                stringResource(
                    if (hasPicture) {
                        R.string.flashcards_delete_card_body_picture
                    } else {
                        R.string.flashcards_delete_card_body
                    },
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private val ThumbnailSize = 56.dp

/** A 48dp square on a dense screen is about 144px; this leaves some headroom. */
private const val ThumbnailPixels = 192

/** Clears the extended button: its 56dp, and the 16dp it floats above the edge. */
private val CardListBottomClearance = 88.dp

/**
 * Cards an English session would leave out, said once, with the way to fix it
 * beside it — rather than an English session that is mysteriously shorter
 * than the deck.
 */
@Composable
private fun MissingEnglish(count: Int, isFilling: Boolean, onFill: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen)
            .padding(bottom = Space.sm)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(start = Space.lg, end = Space.sm, top = Space.sm, bottom = Space.sm),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_flag_uk),
            contentDescription = null,
            modifier = Modifier
                .size(width = 21.dp, height = 14.dp)
                .clip(RoundedCornerShape(2.dp)),
        )
        Text(
            text = pluralStringResource(R.plurals.flashcards_english_missing, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Space.md),
        )
        TextButton(onClick = onFill, enabled = !isFilling) {
            Text(stringResource(R.string.flashcards_english_fill))
        }
    }
}
