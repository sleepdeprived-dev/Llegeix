package com.david.llegeix.ui.flashcards

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import com.david.llegeix.data.flashcards.CardSort
import com.david.llegeix.data.flashcards.ListSort
import com.david.llegeix.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * A sort button for a top bar: the sort icon, opening the orders on offer
 * with the one in force ticked and in bold. On the screen it sorts, in plain
 * sight, rather than in an overflow menu.
 */
@Composable
private fun <T> SortMenu(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                painter = painterResource(Res.drawable.ic_sort),
                contentDescription = stringResource(Res.string.flashcards_sort),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (option, label) ->
                val isSelected = option == selected
                DropdownMenuItem(
                    text = {
                        Text(label, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                    },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    onClick = {
                        onSelect(option)
                        open = false
                    },
                )
            }
        }
    }
}

/** The tab's order for its collections and decks. */
@Composable
fun ListSortMenu(selected: ListSort, onSelect: (ListSort) -> Unit) = SortMenu(
    options = listOf(
        ListSort.NAME to stringResource(Res.string.flashcards_sort_name),
        ListSort.NAME_DESCENDING to stringResource(Res.string.flashcards_sort_name_desc),
        ListSort.NEWEST to stringResource(Res.string.flashcards_sort_newest),
        ListSort.OLDEST to stringResource(Res.string.flashcards_sort_oldest),
        ListSort.MOST_CARDS to stringResource(Res.string.flashcards_sort_most_cards),
    ),
    selected = selected,
    onSelect = onSelect,
)

/** A deck's order for its cards. */
@Composable
fun CardSortMenu(selected: CardSort, onSelect: (CardSort) -> Unit) = SortMenu(
    options = listOf(
        CardSort.ALPHABETICAL to stringResource(Res.string.flashcards_sort_name),
        CardSort.NEWEST to stringResource(Res.string.flashcards_sort_newest),
        CardSort.OLDEST to stringResource(Res.string.flashcards_sort_oldest),
        CardSort.WEAKEST to stringResource(Res.string.flashcards_sort_weakest),
    ),
    selected = selected,
    onSelect = onSelect,
)
