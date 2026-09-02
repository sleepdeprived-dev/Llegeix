package com.david.llegeix.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.david.llegeix.R

/**
 * The one search field in the app.
 *
 * There were three of them — the library's, the saved words', and the reader's
 * find bar — written separately and drifting apart: different heights,
 * different shapes, a clear button on two of them and not the third. Searching
 * is the same act wherever you do it, so it should be the same object, and
 * fixing it once should fix it everywhere.
 *
 * Compact rather than Material's default 56dp box with a floating label.
 * Search is something you reach for occasionally; it should never be the
 * tallest thing on the screen.
 */
@Composable
fun SearchField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Sits before the clear button — the reader puts its match count here. */
    trailing: (@Composable () -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = RoundedCornerShape(50),
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailing?.invoke()
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(R.string.library_clear_search),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        },
        modifier = modifier
            .heightIn(min = 42.dp)
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                },
            ),
    )
}

/**
 * What was searched for last, offered back.
 *
 * Shown only under an empty field, so it is a way to start and never something
 * in the way of a search already under way. The list is short by design and
 * only ever holds searches that found something, which is why there is one
 * "clear" for the row and no way to pick items off one at a time: there is
 * nothing in it to be embarrassed by or to correct.
 *
 * A row of chips rather than a list of rows. Under the library's field a list
 * would push the documents off the screen to offer three words.
 */
@Composable
fun SearchHistoryRow(
    history: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (history.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        contentPadding = PaddingValues(horizontal = Space.screen),
    ) {
        item {
            Text(
                text = stringResource(R.string.search_recent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = Space.xs),
            )
        }
        items(history, key = { it }) { past ->
            SuggestionChip(
                onClick = { onPick(past) },
                label = {
                    Text(
                        text = past,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                shape = RoundedCornerShape(50),
                colors = SuggestionChipDefaults.suggestionChipColors(
                    labelColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
        item {
            TextButton(onClick = onClear) {
                Text(
                    text = stringResource(R.string.search_recent_clear),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
