package com.david.llegeix.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.david.llegeix.R
import com.david.llegeix.data.db.dao.RecentDocument
import com.david.llegeix.data.source.DocumentNames
import com.david.llegeix.resources.*
import com.david.llegeix.ui.common.CoverAspectRatio
import com.david.llegeix.ui.common.PdfCover
import com.david.llegeix.ui.common.Space
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Where the library's documents come from, as one row at the top of it.
 *
 * This has been three things. It was an item in the overflow menu, which is the
 * worst place for it: a reader looking at a library missing a book thinks "where
 * has it gone", not "I should open the three-dot menu". Then it was a strip of
 * tiles that scrolled sideways, one per source — honest, and three lines of
 * permanent furniture with half of itself always off the edge of the screen.
 * Then it was a text button reading *See sources* with a bare plus disc beside
 * it, which packed two unrelated jobs into one line: going to the sources, and
 * bringing in a new one.
 *
 * It is now one card that does one thing. It was briefly built exactly like the
 * folder rows below it, on the grounds that it is a place you go into — and that
 * turned out to be the mistake: it is *not* one of the library's folders, and
 * dressing it as one put a row reading "Sources" in among the reader's own
 * folders where it read as another of them. So it is a card now, lifted off the
 * ground with the tinted disc the app gives to things that are actions rather
 * than contents, sitting in its own space above the list. Adding documents left
 * this row for a button of its own that says the word "add", since a plus disc
 * at the end of a row is the least obvious control on the screen and importing
 * is the first thing anybody has to do.
 *
 * The detail line is the one thing the strip of tiles said that a button could
 * not: a library quietly missing a folder looks exactly like a library showing
 * everything, so when the two counts differ this card says "31 of 48 PDFs shown"
 * rather than a total — in the accent, because it is the one line here that is
 * telling the reader something is missing.
 */
@Composable
fun SourcesRow(
    sourceCount: Int,
    visibleCount: Int,
    totalCount: Int,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onOpenSources)
            .padding(start = Space.lg, end = Space.md)
            .padding(vertical = Space.md),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_folder),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.lg),
        ) {
            Text(
                text = stringResource(Res.string.library_sources),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(
                    if (visibleCount < totalCount) {
                        stringResource(Res.string.sources_count_partial, visibleCount, totalCount)
                    } else {
                        pluralStringResource(
                            Res.plurals.folders_pdf_count,
                            totalCount,
                            totalCount,
                        )
                    },
                    pluralStringResource(
                        Res.plurals.sources_source_count,
                        sourceCount,
                        sourceCount,
                    ),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (visibleCount < totalCount) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
 *
 * ### Folding it away
 *
 * Not everybody wants it open. Somebody who reads one book at a time has a
 * shelf of one, and somebody who browses their library more than they resume
 * it has a shelf that is simply in the way — so the heading is a control rather
 * than a label, and a tap on it folds the cards away or brings them back.
 * Folded, the heading stays and says how many books are on the shelf.
 *
 * That is the only thing it does, on purpose. The shelf used to be hideable
 * too, from a button on the folded heading, a dialog behind a hold, and a
 * section in Configuració: three places to manage one shelf, one of them on
 * another screen, and a state in which the shelf was gone with nothing on the
 * library to say it had ever been there. Folded is as far as it goes now, so
 * the way back is always the heading itself, where the reader is looking.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContinueReadingRow(
    entries: List<RecentDocument>,
    names: DocumentNames,
    collapsed: Boolean,
    onOpen: (RecentDocument) -> Unit,
    onForget: (RecentDocument) -> Unit,
    onSeeAll: () -> Unit,
    onToggleCollapsed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.md, end = Space.sm),
        ) {
            // The whole heading is the target, not just the chevron: the
            // chevron is what says the row can be folded, and a 24dp arrow is
            // not what anybody aims at once they know it can.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggleCollapsed)
                    .padding(vertical = Space.xs),
            ) {
                // One arrow turned rather than two icons swapped, so the fold
                // reads as the same control moving instead of a different one
                // appearing.
                val turn by animateFloatAsState(
                    targetValue = if (collapsed) 0f else 90f,
                    label = "continueChevron",
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(
                        if (collapsed) {
                            Res.string.library_continue_expand
                        } else {
                            Res.string.library_continue_collapse
                        },
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = Space.xs)
                        .size(22.dp)
                        .rotate(turn),
                )
                Column(modifier = Modifier.padding(start = Space.xs)) {
                    Text(
                        text = stringResource(Res.string.library_continue_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    // Only while folded. Expanded, the cards underneath are a
                    // better answer to "how many" than a number is.
                    if (collapsed) {
                        Text(
                            text = pluralStringResource(
                                Res.plurals.library_continue_count,
                                entries.size,
                                entries.size,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // The rest of the history, open or folded: the shelf is only the
            // books part-read, and the full list is one press further.
            TextButton(onClick = onSeeAll) {
                Text(stringResource(Res.string.library_continue_history))
            }
        }
        AnimatedVisibility(visible = !collapsed) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                contentPadding = PaddingValues(horizontal = Space.screen),
                modifier = Modifier.padding(top = Space.xs),
            ) {
                items(entries, key = { it.uriString }) { entry ->
                    ContinueCard(
                        entry = entry,
                        title = names.titleFor(entry.uriString, entry.displayName),
                        onClick = { onOpen(entry) },
                        onLongClick = { onForget(entry) },
                    )
                }
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
    title: String,
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
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (total != null && fraction != null) {
                    stringResource(
                        Res.string.library_continue_position,
                        entry.lastPageIndex + 1,
                        total,
                        (fraction * 100).roundToInt(),
                    )
                } else {
                    stringResource(Res.string.recent_page, entry.lastPageIndex + 1)
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
