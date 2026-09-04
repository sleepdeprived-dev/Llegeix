package com.david.llegeix.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.david.llegeix.R

/**
 * What was searched for last, offered back.
 *
 * This was a row of chips that scrolled sideways, and it was wrong in three
 * ways at once. A horizontal scroller hides most of itself: the fourth word is
 * off the edge of the screen and nothing says so, so a list of eight reads as a
 * list of three. Chips are for choices that are all in view, and these are not.
 * And because it was drawn whenever the field was empty — which, on the library
 * and the saved words, is nearly always — it was not an offer made while
 * searching, it was a strip of old words permanently glued under the search
 * bar, in the way of the thing the screen is actually for.
 *
 * So: a short vertical list, one word per line, the whole width of the screen
 * to aim at, and shown only while the field is focused — while somebody is
 * actually searching. It is what every search field on the phone already does,
 * which is the other half of the argument: this is not the place to be
 * inventive.
 */
@Composable
fun RecentSearches(
    history: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = history.take(MAX_SHOWN)
    if (shown.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.screen, end = Space.sm),
        ) {
            Text(
                text = stringResource(R.string.search_recent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // One "clear" for the whole list and no way to pick items off one
            // at a time. The list is short and only ever holds searches that
            // found something: there is nothing in it to be embarrassed by, and
            // a delete button on every line would be eight more things to read.
            TextButton(onClick = onClear) {
                Text(
                    text = stringResource(R.string.search_recent_clear),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        shown.forEach { past ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(past) }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = Space.screen, vertical = Space.sm),
            ) {
                MenuIcon(painterResource(R.drawable.ic_recent))
                Text(
                    text = past,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * How many of the remembered searches are drawn.
 *
 * Fewer than are kept. Eight rows is most of a phone screen given over to what
 * somebody typed last week, and the point of the list is to save typing a word
 * you searched for a minute ago — which is always near the top.
 */
private const val MAX_SHOWN = 5
