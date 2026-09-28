package com.david.llegeix.ui.flashcards

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.david.llegeix.data.db.dao.DeckWithCount
import com.david.llegeix.data.flashcards.PictureResults
import com.david.llegeix.resources.*
import com.david.llegeix.ui.common.MenuIcon
import com.david.llegeix.ui.common.Space
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What can be done to a collection or a deck, from any of the three views:
 * the same actions whichever way the tab is laid out.
 */
class EntryActions(
    val openShelf: (DeckShelf) -> Unit,
    val openDeck: (DeckWithCount) -> Unit,
    val playShelf: (DeckShelf) -> Unit,
    val playDeck: (DeckWithCount) -> Unit,
    val pinShelf: (DeckShelf) -> Unit,
    val pinDeck: (DeckWithCount) -> Unit,
    val pictureShelf: (DeckShelf) -> Unit,
    val pictureDeck: (DeckWithCount) -> Unit,
    val moveShelf: (DeckShelf) -> Unit,
    val moveDeck: (DeckWithCount) -> Unit,
    val newInside: (DeckShelf) -> Unit,
    val renameShelf: (DeckShelf) -> Unit,
    val renameDeck: (DeckWithCount) -> Unit,
    val deleteShelf: (DeckShelf) -> Unit,
    val deleteDeck: (DeckWithCount) -> Unit,
)

// ---- Shared pieces ---------------------------------------------------------

/**
 * "2 col·leccions · 3 baralles · 124 targetes", leaving out what there is none
 * of — and just "Cap targeta" when there are no cards in it at all, however
 * many empty decks or collections it holds.
 */
@Composable
internal fun shelfSummary(shelf: DeckShelf): String = if (shelf.cardCount == 0) {
    stringResource(Res.string.flashcards_collection_no_cards)
} else buildList {
    val inside = shelf.children.size
    if (inside > 0) add(pluralStringResource(Res.plurals.flashcards_collection_count, inside, inside))
    val decks = shelf.decks.size
    if (decks > 0) add(pluralStringResource(Res.plurals.flashcards_deck_count, decks, decks))
    add(pluralStringResource(Res.plurals.flashcards_card_count, shelf.cardCount, shelf.cardCount))
}.joinToString(" · ")

@Composable
private fun deckSummary(deck: DeckWithCount): String =
    if (deck.cardCount == 0) {
        stringResource(Res.string.flashcards_deck_no_cards)
    } else {
        pluralStringResource(Res.plurals.flashcards_card_count, deck.cardCount, deck.cardCount)
    }

/**
 * A collection's mark: its picture if it was given one, otherwise the
 * collection glyph on the accent's container colour — never an initial, which
 * is what a deck wears, so the two are told apart before either name is read.
 */
@Composable
private fun ShelfMark(shelf: DeckShelf, modifier: Modifier, glyph: androidx.compose.ui.unit.Dp) {
    val image = shelf.collection.coverPath
    if (image != null) {
        CardImage(
            path = image,
            maxEdge = 384,
            contentDescription = null,
            kind = PictureResults.kindOf(shelf.collection.coverCredit),
            modifier = modifier,
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_collection),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(glyph),
            )
        }
    }
}

/** A deck's mark: its picture, or its initial on a soft tile. */
@Composable
private fun DeckMark(deck: DeckWithCount, modifier: Modifier, big: Boolean = false) {
    val image = deck.coverImage
    if (image != null) {
        CardImage(
            path = image,
            maxEdge = 384,
            contentDescription = null,
            kind = PictureResults.kindOf(deck.coverCredit),
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.background(MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Text(
                text = deck.name.trim().take(1).uppercase(),
                style = if (big) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** The name, up to two lines, with the pin in front when pinned. */
@Composable
private fun EntryName(name: String, pinned: Boolean, style: androidx.compose.ui.text.TextStyle, maxLines: Int = 2) {
    Row(verticalAlignment = Alignment.Top) {
        if (pinned) {
            Icon(
                painter = painterResource(Res.drawable.ic_pin),
                contentDescription = stringResource(Res.string.folders_pinned),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 3.dp, end = Space.xs)
                    .size(15.dp),
            )
        }
        Text(text = name, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MoreButton(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(Res.drawable.ic_more),
            contentDescription = stringResource(Res.string.document_actions, name),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The ⋮ menu of a collection. */
@Composable
internal fun ShelfMenu(shelf: DeckShelf, expanded: Boolean, onDismiss: () -> Unit, actions: EntryActions) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            leadingIcon = { MenuIcon(painterResource(Res.drawable.ic_new_collection)) },
            text = { Text(stringResource(Res.string.flashcards_new_collection_inside)) },
            onClick = { onDismiss(); actions.newInside(shelf) },
        )
        HorizontalDivider()
        DropdownMenuItem(
            leadingIcon = { MenuIcon(painterResource(Res.drawable.ic_pin)) },
            text = {
                Text(stringResource(if (shelf.collection.isPinned) Res.string.folders_unpin else Res.string.folders_pin))
            },
            onClick = { onDismiss(); actions.pinShelf(shelf) },
        )
        DropdownMenuItem(
            leadingIcon = { MenuIcon(painterResource(Res.drawable.ic_image)) },
            text = { Text(stringResource(Res.string.flashcards_deck_picture)) },
            onClick = { onDismiss(); actions.pictureShelf(shelf) },
        )
        DropdownMenuItem(
            leadingIcon = { MenuIcon(painterResource(Res.drawable.ic_move)) },
            text = { Text(stringResource(Res.string.flashcards_move_to_collection)) },
            onClick = { onDismiss(); actions.moveShelf(shelf) },
        )
        DropdownMenuItem(
            leadingIcon = { MenuIcon(Icons.Default.Edit) },
            text = { Text(stringResource(Res.string.folders_rename)) },
            onClick = { onDismiss(); actions.renameShelf(shelf) },
        )
        HorizontalDivider()
        DropdownMenuItem(
            leadingIcon = { MenuIcon(Icons.Default.Delete) },
            text = { Text(stringResource(Res.string.action_delete)) },
            onClick = { onDismiss(); actions.deleteShelf(shelf) },
        )
    }
}

/** The ⋮ menu of a deck. */
@Composable
private fun DeckMenu(deck: DeckWithCount, expanded: Boolean, onDismiss: () -> Unit, actions: EntryActions) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            leadingIcon = { MenuIcon(painterResource(Res.drawable.ic_pin)) },
            text = { Text(stringResource(if (deck.isPinned) Res.string.folders_unpin else Res.string.folders_pin)) },
            onClick = { onDismiss(); actions.pinDeck(deck) },
        )
        DropdownMenuItem(
            leadingIcon = { MenuIcon(painterResource(Res.drawable.ic_image)) },
            text = { Text(stringResource(Res.string.flashcards_deck_picture)) },
            onClick = { onDismiss(); actions.pictureDeck(deck) },
        )
        DropdownMenuItem(
            leadingIcon = { MenuIcon(painterResource(Res.drawable.ic_move)) },
            text = { Text(stringResource(Res.string.flashcards_move_to_collection)) },
            onClick = { onDismiss(); actions.moveDeck(deck) },
        )
        DropdownMenuItem(
            leadingIcon = { MenuIcon(Icons.Default.Edit) },
            text = { Text(stringResource(Res.string.folders_rename)) },
            onClick = { onDismiss(); actions.renameDeck(deck) },
        )
        HorizontalDivider()
        DropdownMenuItem(
            leadingIcon = { MenuIcon(Icons.Default.Delete) },
            text = { Text(stringResource(Res.string.action_delete)) },
            onClick = { onDismiss(); actions.deleteDeck(deck) },
        )
    }
}

// ---- List ------------------------------------------------------------------

/**
 * One collection or deck as a row: its mark, its name over up to two lines —
 * long names wrap rather than being cut to "Vocabulari de la c…" — what it
 * holds, and its play button. A collection ends in a chevron, since pressing
 * it goes inside.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EntryRow(entry: ListEntry, canPractise: Boolean, actions: EntryActions, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val isShelf = entry is ListEntry.Shelf
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen, vertical = 5.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (isShelf) scheme.surfaceContainer else scheme.surfaceContainerLow)
            .combinedClickable(
                onClick = {
                    when (entry) {
                        is ListEntry.Shelf -> actions.openShelf(entry.shelf)
                        is ListEntry.Deck -> actions.openDeck(entry.deck)
                    }
                },
                onLongClick = { menu = true },
            )
            .padding(start = Space.md, top = Space.md, bottom = Space.md, end = Space.xs),
    ) {
        val markShape = RoundedCornerShape(16.dp)
        when (entry) {
            is ListEntry.Shelf -> ShelfMark(entry.shelf, Modifier.size(56.dp).clip(markShape), glyph = 26.dp)
            is ListEntry.Deck -> DeckMark(entry.deck, Modifier.size(56.dp).clip(markShape))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.lg, end = Space.sm),
        ) {
            val name = if (entry is ListEntry.Shelf) entry.shelf.collection.name else (entry as ListEntry.Deck).deck.name
            val pinned = if (entry is ListEntry.Shelf) entry.shelf.collection.isPinned else (entry as ListEntry.Deck).deck.isPinned
            EntryName(name, pinned, MaterialTheme.typography.titleMedium.let { if (isShelf) it.copy(fontWeight = FontWeight.Bold) else it })
            Text(
                text = when (entry) {
                    is ListEntry.Shelf -> shelfSummary(entry.shelf)
                    is ListEntry.Deck -> deckSummary(entry.deck)
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (canPractise) {
            PlayButton(
                onClick = {
                    when (entry) {
                        is ListEntry.Shelf -> actions.playShelf(entry.shelf)
                        is ListEntry.Deck -> actions.playDeck(entry.deck)
                    }
                },
                contentDescription = stringResource(
                    Res.string.flashcards_practise_name,
                    if (entry is ListEntry.Shelf) entry.shelf.collection.name else (entry as ListEntry.Deck).deck.name,
                ),
            )
        }
        Box {
            when (entry) {
                is ListEntry.Shelf -> {
                    MoreButton(entry.shelf.collection.name, onClick = { menu = true })
                    ShelfMenu(entry.shelf, menu, { menu = false }, actions)
                }
                is ListEntry.Deck -> {
                    MoreButton(entry.deck.name, onClick = { menu = true })
                    DeckMenu(entry.deck, menu, { menu = false }, actions)
                }
            }
        }
    }
}

// ---- Compact ---------------------------------------------------------------

/**
 * One tight row, for a long list at a glance: a small mark, the name, the
 * count at the end and the way in. No cover and no card behind it — the rows
 * sit together on one panel, a hairline between them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CompactRow(
    entry: ListEntry,
    canPractise: Boolean,
    actions: EntryActions,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(
        topStart = if (isFirst) 20.dp else 0.dp,
        topEnd = if (isFirst) 20.dp else 0.dp,
        bottomStart = if (isLast) 20.dp else 0.dp,
        bottomEnd = if (isLast) 20.dp else 0.dp,
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen)
            .clip(shape)
            .background(scheme.surfaceContainerLow),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        when (entry) {
                            is ListEntry.Shelf -> actions.openShelf(entry.shelf)
                            is ListEntry.Deck -> actions.openDeck(entry.deck)
                        }
                    },
                    onLongClick = { menu = true },
                )
                .heightIn(min = 56.dp)
                .padding(start = Space.lg, end = Space.xs),
        ) {
            val markShape = RoundedCornerShape(10.dp)
            when (entry) {
                is ListEntry.Shelf -> ShelfMark(entry.shelf, Modifier.size(32.dp).clip(markShape), glyph = 18.dp)
                is ListEntry.Deck -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(markShape)
                        .background(scheme.secondaryContainer),
                ) {
                    Text(
                        text = entry.deck.name.trim().take(1).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSecondaryContainer,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Space.md, vertical = Space.sm),
            ) {
                val name = if (entry is ListEntry.Shelf) entry.shelf.collection.name else (entry as ListEntry.Deck).deck.name
                val pinned = if (entry is ListEntry.Shelf) entry.shelf.collection.isPinned else (entry as ListEntry.Deck).deck.isPinned
                EntryName(
                    name,
                    pinned,
                    MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (entry is ListEntry.Shelf) FontWeight.Bold else FontWeight.Medium,
                    ),
                )
            }
            val count = if (entry is ListEntry.Shelf) entry.shelf.cardCount else (entry as ListEntry.Deck).deck.cardCount
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
            if (entry is ListEntry.Shelf) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp).size(20.dp),
                )
            }
            if (canPractise) {
                IconButton(
                    onClick = {
                        when (entry) {
                            is ListEntry.Shelf -> actions.playShelf(entry.shelf)
                            is ListEntry.Deck -> actions.playDeck(entry.deck)
                        }
                    },
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            Res.string.flashcards_practise_name,
                            if (entry is ListEntry.Shelf) entry.shelf.collection.name else (entry as ListEntry.Deck).deck.name,
                        ),
                        tint = scheme.primary,
                    )
                }
            }
            Box {
                when (entry) {
                    is ListEntry.Shelf -> {
                        MoreButton(entry.shelf.collection.name, onClick = { menu = true })
                        ShelfMenu(entry.shelf, menu, { menu = false }, actions)
                    }
                    is ListEntry.Deck -> {
                        MoreButton(entry.deck.name, onClick = { menu = true })
                        DeckMenu(entry.deck, menu, { menu = false }, actions)
                    }
                }
            }
        }
        if (!isLast) {
            HorizontalDivider(
                color = scheme.outlineVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = Space.lg + 32.dp + Space.md),
            )
        }
    }
}

// ---- Grid ------------------------------------------------------------------

/**
 * One collection or deck as a card in a grid of two: the cover large across
 * the top, the play button riding on its corner, and the name under it over
 * up to two lines with what it holds.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EntryTile(entry: ListEntry, canPractise: Boolean, actions: EntryActions, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .padding(6.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainerLow)
            .combinedClickable(
                onClick = {
                    when (entry) {
                        is ListEntry.Shelf -> actions.openShelf(entry.shelf)
                        is ListEntry.Deck -> actions.openDeck(entry.deck)
                    }
                },
                onLongClick = { menu = true },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.25f)
                .padding(6.dp)
                .clip(RoundedCornerShape(18.dp)),
        ) {
            when (entry) {
                is ListEntry.Shelf -> ShelfMark(entry.shelf, Modifier.fillMaxSize(), glyph = 40.dp)
                is ListEntry.Deck -> DeckMark(entry.deck, Modifier.fillMaxSize(), big = true)
            }
            // What it is, in the corner, so a collection with a picture of
            // its own is still seen to be one.
            if (entry is ListEntry.Shelf && entry.shelf.collection.coverPath != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(scheme.surface.copy(alpha = 0.9f)),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_collection),
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            if (canPractise) {
                PlayButton(
                    onClick = {
                        when (entry) {
                            is ListEntry.Shelf -> actions.playShelf(entry.shelf)
                            is ListEntry.Deck -> actions.playDeck(entry.deck)
                        }
                    },
                    contentDescription = stringResource(
                        Res.string.flashcards_practise_name,
                        if (entry is ListEntry.Shelf) entry.shelf.collection.name else (entry as ListEntry.Deck).deck.name,
                    ),
                    // Solid and lifted, so it stands out on any cover.
                    prominent = true,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .shadow(4.dp, CircleShape),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(start = Space.md, bottom = Space.sm),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = Space.xs),
            ) {
                val name = if (entry is ListEntry.Shelf) entry.shelf.collection.name else (entry as ListEntry.Deck).deck.name
                val pinned = if (entry is ListEntry.Shelf) entry.shelf.collection.isPinned else (entry as ListEntry.Deck).deck.isPinned
                EntryName(
                    name,
                    pinned,
                    MaterialTheme.typography.titleSmall.let {
                        if (entry is ListEntry.Shelf) it.copy(fontWeight = FontWeight.Bold) else it
                    },
                )
                Text(
                    text = when (entry) {
                        is ListEntry.Shelf -> if (entry.shelf.cardCount == 0) {
                            stringResource(Res.string.flashcards_collection_no_cards)
                        } else {
                            pluralStringResource(Res.plurals.flashcards_card_count, entry.shelf.cardCount, entry.shelf.cardCount)
                        }
                        is ListEntry.Deck -> deckSummary(entry.deck)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                when (entry) {
                    is ListEntry.Shelf -> {
                        MoreButton(entry.shelf.collection.name, onClick = { menu = true })
                        ShelfMenu(entry.shelf, menu, { menu = false }, actions)
                    }
                    is ListEntry.Deck -> {
                        MoreButton(entry.deck.name, onClick = { menu = true })
                        DeckMenu(entry.deck, menu, { menu = false }, actions)
                    }
                }
            }
        }
    }
}

// ---- Inside a collection ---------------------------------------------------

/**
 * The top of a collection that has been opened: where it is — the path from
 * the top of the tab, each step a way back — its full name, however long, in
 * large type, what it holds, and the way into practising all of it.
 */
@Composable
internal fun CollectionHeader(
    path: List<DeckShelf>,
    canPractise: Boolean,
    onGoTo: (Long?) -> Unit,
    actions: EntryActions,
    modifier: Modifier = Modifier,
) {
    val shelf = path.lastOrNull() ?: return
    var menu by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.padding(horizontal = Space.screen).padding(top = Space.xs, bottom = Space.md)) {
        // The path: Targetes › Menjar › Fruita, scrolling sideways if deep.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Crumb(stringResource(Res.string.nav_flashcards), onClick = { onGoTo(null) })
            path.dropLast(1).forEach { step ->
                CrumbArrow()
                Crumb(step.collection.name, onClick = { onGoTo(step.id) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.sm)) {
            ShelfMark(shelf, Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)), glyph = 30.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Space.lg),
            ) {
                Text(
                    text = shelf.collection.name,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = shelfSummary(shelf),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Box {
                MoreButton(shelf.collection.name, onClick = { menu = true })
                ShelfMenu(shelf, menu, { menu = false }, actions)
            }
        }
        if (canPractise) {
            FilledTonalButton(
                onClick = { actions.playShelf(shelf) },
                modifier = Modifier.padding(top = Space.md),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(Res.string.flashcards_practise_collection),
                    modifier = Modifier.padding(start = Space.sm),
                )
            }
        }
    }
}

@Composable
private fun Crumb(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

@Composable
private fun CrumbArrow() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
    )
}

/** The view button for the top bar: list, compact or grid, the one in use ticked. */
@Composable
internal fun LayoutMenu(selected: ListLayout, onSelect: (ListLayout) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                painter = painterResource(
                    when (selected) {
                        ListLayout.GRID -> Res.drawable.ic_grid
                        ListLayout.COMPACT -> Res.drawable.ic_compact
                        ListLayout.LIST -> Res.drawable.ic_list
                    },
                ),
                contentDescription = stringResource(Res.string.flashcards_layout),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                ListLayout.LIST to (Res.string.flashcards_layout_list to Res.drawable.ic_list),
                ListLayout.COMPACT to (Res.string.flashcards_layout_compact to Res.drawable.ic_compact),
                ListLayout.GRID to (Res.string.flashcards_layout_grid to Res.drawable.ic_grid),
            ).forEach { (layout, labelAndIcon) ->
                val isSelected = layout == selected
                DropdownMenuItem(
                    leadingIcon = { MenuIcon(painterResource(labelAndIcon.second)) },
                    text = {
                        Text(
                            stringResource(labelAndIcon.first),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    trailingIcon = {
                        if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    onClick = {
                        onSelect(layout)
                        open = false
                    },
                )
            }
        }
    }
}
