package com.david.llegeix.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.david.llegeix.R
import com.david.llegeix.data.db.dao.RecentDocument
import com.david.llegeix.ui.common.CoverAspectRatio
import com.david.llegeix.ui.common.PdfCover
import com.david.llegeix.ui.common.Space
import com.david.llegeix.util.pdfTitle
import kotlin.math.roundToInt

/**
 * Where the library's documents come from, as one line at the top of it.
 *
 * This was a heading reading *Sources* over a strip of tiles that scrolled
 * sideways, one per source, each with its own count. That strip replaced an
 * item in an overflow menu and it was right to: a reader looking at a library
 * missing a book thinks "where has it gone", not "I should open the three-dot
 * menu". But a permanent horizontal scroller is a heavy way to say it. Half of
 * it is always off the edge of the screen, it takes three lines of height on
 * every visit to the library, and the counts on it are only ever read on the
 * one visit in fifty where something is actually missing.
 *
 * So the whole thing is one press now. *See sources* opens the sheet that has
 * always held the real answer — every source, every folder inside it, and a
 * tick box against each — and the plus beside it adds one. The plus carries no
 * word because it does not need one: a plus at the end of a row about sources
 * adds a source, and the word was the widest thing in the row.
 */
@Composable
fun SourcesStrip(
    onOpenSources: () -> Unit,
    onAddSource: () -> Unit,
    modifier: Modifier = Modifier,
    /** Only drawn when the two numbers differ. See [LibraryUiState.isPartlyShown]. */
    partial: Pair<Int, Int>? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Space.md, end = Space.screen),
    ) {
        TextButton(onClick = onOpenSources) {
            Icon(
                painter = painterResource(R.drawable.ic_folder),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.library_see_sources),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = Space.sm),
            )
        }
        // The one thing the strip of tiles said that this row would otherwise
        // lose: a library quietly missing a folder looks exactly like a library
        // showing everything, and this is the sentence that tells them apart.
        partial?.let { (visible, total) ->
            Text(
                text = stringResource(R.string.sources_count_partial, visible, total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Space.sm),
            )
        } ?: Box(modifier = Modifier.weight(1f))
        AddSourceButton(onClick = onAddSource)
    }
}

/**
 * The plus that brings in another source.
 *
 * A filled disc rather than an outlined tile with the word "Add" in it. It sits
 * at the end of a row that has just said the word *sources*, so what it adds is
 * not in doubt, and a round target under the thumb is easier to hit than a
 * pill it has to be aimed inside.
 */
@Composable
private fun AddSourceButton(onClick: () -> Unit) {
    val label = stringResource(R.string.library_add_source)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(AddButtonSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The books that are part-read, at the top of the library.
 *
 * This is the reason most people open the app, so it is the first thing the
 * library says. It used to be a tab called Recent — one of five — which put
 * "carry on with what I was reading" a tap away from the screen that appears on
 * launch, and made it look like a fourth way of listing the same documents
 * rather than the shortcut it is.
 *
 * Finished books drop off it on their own, and so do ones never opened past the
 * first page: a shelf of things to resume should hold only things there is
 * something to resume.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContinueReadingRow(
    entries: List<RecentDocument>,
    onOpen: (RecentDocument) -> Unit,
    onForget: (RecentDocument) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.screen, end = Space.sm),
        ) {
            Text(
                text = stringResource(R.string.library_continue_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSeeAll) {
                Text(stringResource(R.string.library_continue_history))
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            contentPadding = PaddingValues(horizontal = Space.screen),
            modifier = Modifier.padding(top = Space.xs),
        ) {
            items(entries, key = { it.uriString }) { entry ->
                ContinueCard(
                    entry = entry,
                    onClick = { onOpen(entry) },
                    onLongClick = { onForget(entry) },
                )
            }
        }
    }
}

/**
 * One part-read book.
 *
 * The cover carries the progress bar it already knows how to draw, and the line
 * underneath spells the same fact out for anyone who wants the number. Both,
 * because they answer different questions: the bar says "roughly here" without
 * being read, and "page 34 of 210" says whether it is worth picking up tonight.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueCard(
    entry: RecentDocument,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val total = entry.pageCount
    val fraction = total?.takeIf { it > 0 }
        ?.let { ((entry.lastPageIndex + 1).toFloat() / it).coerceIn(0f, 1f) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(ContinueCardWidth)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            // Hold to forget, as on the history screen. A cross on every card
            // would put a destructive control on a shelf whose whole purpose is
            // being tapped without looking.
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(Space.md),
    ) {
        PdfCover(
            uriString = entry.uriString,
            width = ContinueCoverWidth,
            progress = fraction,
            cornerRadius = 8.dp,
            modifier = Modifier
                .width(ContinueCoverWidth)
                .aspectRatio(CoverAspectRatio),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.md),
        ) {
            Text(
                text = pdfTitle(entry.displayName),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (total != null && fraction != null) {
                    stringResource(
                        R.string.library_continue_position,
                        entry.lastPageIndex + 1,
                        total,
                        (fraction * 100).roundToInt(),
                    )
                } else {
                    stringResource(R.string.recent_page, entry.lastPageIndex + 1)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Space.xs),
            )
        }
    }
}

/**
 * Wide enough for two lines of a real title, narrow enough that the next card
 * is always half in view — which is what says the shelf scrolls without a hint
 * having to be printed on it.
 */
private val ContinueCardWidth = 250.dp
private val ContinueCoverWidth = 44.dp

/** The platform's smallest comfortable round target. */
private val AddButtonSize = 40.dp
