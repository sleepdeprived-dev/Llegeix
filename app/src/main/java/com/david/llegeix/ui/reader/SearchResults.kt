package com.david.llegeix.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.david.llegeix.R
import com.david.llegeix.pdf.MATCH_LIMIT
import com.david.llegeix.pdf.PdfMatch
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.Space

/**
 * The strip under the find bar: how much was found, and the way into it.
 *
 * It is both the count and the switch on purpose. A number on its own leaves
 * the reader to discover that the results can be listed at all, and a bare
 * chevron leaves them to guess what it opens; together they are one wide,
 * obvious target that says what it will do and what is behind it.
 */
@Composable
internal fun SearchResultsBar(
    matchCount: Int,
    pageCount: Int,
    isOpen: Boolean,
    onToggle: () -> Unit,
) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .heightIn(min = 44.dp)
                .padding(horizontal = Space.screen, vertical = Space.sm),
        ) {
            Text(
                text = pluralStringResource(
                    R.plurals.reader_find_match_count,
                    matchCount,
                    matchCount,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = " · " + pluralStringResource(
                    R.plurals.reader_find_page_count,
                    pageCount,
                    pageCount,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (isOpen) {
                    Icons.Default.KeyboardArrowUp
                } else {
                    Icons.Default.KeyboardArrowDown
                },
                contentDescription = stringResource(
                    if (isOpen) R.string.reader_find_results_hide else R.string.reader_find_results_show,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

/**
 * Every occurrence of the searched word, in the words it was found in.
 *
 * The list exists because "3 / 47" is not an answer. Stepping through
 * forty-seven hits one arrow-press at a time to find the one that matters is
 * the reader doing the searching, and the whole page has to be read at each
 * stop to work out whether this is the *cap* that means a head. A row that
 * quotes the line answers that before anything is jumped to.
 *
 * It covers the page rather than sharing it. On a phone there is no room for
 * both, and a list crammed into a third of the screen next to the text it is
 * about splits the reader's attention between two things that say the same
 * word. Picking a result hands the whole screen back to the page.
 *
 * The design is deliberately quiet: one accent colour, used for exactly one
 * thing — the word that was searched for — so that finding it in a row costs a
 * glance rather than a read. Everything else is grey, grouped under the page it
 * is on, and spaced far enough apart to be aimed at with a thumb.
 */
@Composable
internal fun SearchResultsPanel(
    search: SearchState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        if (search.matches.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.reader_find_empty_title),
                body = stringResource(R.string.reader_find_empty_body, search.query),
            )
            return@Surface
        }

        // Page headings and hits, flattened once, so the list is a plain list
        // and the grouping costs nothing to scroll past.
        val rows = remember(search.matches) { rowsFor(search.matches) }
        val listState = rememberLazyListState()

        // A finger on the list is a reader who wants to see the list, and the
        // keyboard is covering half of it.
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(listState.isScrollInProgress) {
            if (listState.isScrollInProgress) keyboard?.hide()
        }

        // Stepping through with the arrows moves the marked row, which is no
        // use if it is somewhere off the screen. Only when it is out of sight:
        // scrolling a list the reader is already looking at moves what is under
        // their thumb.
        LaunchedEffect(search.currentIndex) {
            val target = rows.indexOfFirst {
                it is ResultRow.Hit && it.index == search.currentIndex
            }
            if (target < 0) return@LaunchedEffect
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.none { it.index == target }) {
                listState.animateScrollToItem(target)
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = Space.xxl),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                items = rows,
                // Keyed so the rows arriving as the sweep goes on are added to
                // the list rather than recomposing the ones already drawn.
                key = { row ->
                    when (row) {
                        is ResultRow.PageHeading -> "page-${row.pageIndex}"
                        is ResultRow.Hit -> "hit-${row.index}"
                    }
                },
            ) { row ->
                when (row) {
                    is ResultRow.PageHeading -> PageHeading(row.pageIndex)
                    is ResultRow.Hit -> MatchRow(
                        match = row.match,
                        isCurrent = row.index == search.currentIndex,
                        onClick = { onSelect(row.index) },
                    )
                }
            }
            if (search.isAtLimit) {
                item {
                    Text(
                        text = stringResource(R.string.reader_find_limit, MATCH_LIMIT),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = Space.screen,
                            end = Space.screen,
                            top = Space.xl,
                        ),
                    )
                }
            }
        }
    }
}

/** Which page the rows below it are on. */
@Composable
private fun PageHeading(pageIndex: Int) {
    Text(
        text = stringResource(R.string.reader_find_result_page, pageIndex + 1),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = Space.screen,
            end = Space.screen,
            top = Space.lg,
            bottom = Space.xs,
        ),
    )
}

/**
 * One occurrence, quoted.
 *
 * The searched word is the only coloured thing on the row, and the row the
 * reader is currently on is the only shaded one, so "which one is this" and
 * "where am I" are answered by two different signals that cannot be confused
 * with each other.
 */
@Composable
private fun MatchRow(
    match: PdfMatch,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val quoted = buildAnnotatedString {
        append(match.snippet)
        if (match.snippetEnd > match.snippetStart && match.snippetEnd <= match.snippet.length) {
            addStyle(
                SpanStyle(color = accent, fontWeight = FontWeight.SemiBold),
                match.snippetStart,
                match.snippetEnd,
            )
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isCurrent) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            // Comfortably past the 48dp minimum: the rows are the whole screen
            // here, and there is nothing to be gained by fitting more of them
            // in than can be told apart.
            .heightIn(min = 60.dp)
            .padding(horizontal = Space.sm, vertical = Space.md),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(50))
                .background(if (isCurrent) accent else Color.Transparent),
        )
        Text(
            text = quoted,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A heading or a hit; the list is one flat run of both. */
private sealed interface ResultRow {
    data class PageHeading(val pageIndex: Int) : ResultRow

    /** [index] is the match's position in the search's own list. */
    data class Hit(val index: Int, val match: PdfMatch) : ResultRow
}

/**
 * Flatten the matches into headings and hits.
 *
 * The matches arrive page by page and in order, so a heading is simply a change
 * of page: no grouping, no sorting, and the list stays in the order the
 * document is read in.
 */
private fun rowsFor(matches: List<PdfMatch>): List<ResultRow> {
    val rows = ArrayList<ResultRow>(matches.size + 8)
    var page = -1
    matches.forEachIndexed { index, match ->
        if (match.pageIndex != page) {
            page = match.pageIndex
            rows += ResultRow.PageHeading(page)
        }
        rows += ResultRow.Hit(index, match)
    }
    return rows
}
