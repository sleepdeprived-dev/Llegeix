package com.david.llegeix.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
 * Where the library's documents come from, along the top of the library.
 *
 * This replaced an item in the overflow menu called "Fonts". A reader looking
 * at a library that is missing a book does not think "I should open the
 * three-dot menu"; they think "where has it gone", and the honest answer —
 * *these are the places I am looking, and this is how much of each one counts*
 * — belongs in front of them, on the screen the question is asked about.
 *
 * Each tile says its own name and what it is contributing. A source that is
 * only partly reaching the library says "31 of 48" rather than "48", which is
 * the whole point: a library quietly missing a folder used to look exactly like
 * a library showing everything. Pressing a tile opens that source; pressing the
 * last one adds another.
 */
@Composable
fun SourcesStrip(
    sources: List<LibrarySource>,
    onOpenSource: (LibrarySource) -> Unit,
    onAddSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.library_sources),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Space.screen, bottom = Space.sm),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            contentPadding = PaddingValues(horizontal = Space.screen),
        ) {
            items(sources, key = { it.kind.name + ":" + it.label }) { source ->
                SourceTile(source = source, onClick = { onOpenSource(source) })
            }
            item {
                AddSourceTile(onClick = onAddSource)
            }
        }
    }
}

/** One source, as a tile: what it is called, and what it is contributing. */
@Composable
private fun SourceTile(source: LibrarySource, onClick: () -> Unit) {
    val label = if (source.kind == LibrarySource.Kind.DEVICE) {
        stringResource(R.string.library_source_device)
    } else {
        source.label.substringAfterLast('/')
    }
    // Quieter when nothing is coming through, so a switched-off source reads as
    // switched off from across the strip rather than only once it is opened.
    val tint = if (source.isSilenced) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.primary
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(SourceTileWidth)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.md),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(
                    if (source.kind == LibrarySource.Kind.DEVICE) {
                        R.drawable.ic_device
                    } else {
                        R.drawable.ic_folder
                    },
                ),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.padding(start = Space.md)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    source.isSilenced -> stringResource(R.string.sources_none_shown)
                    source.isPartial -> stringResource(
                        R.string.sources_count_partial,
                        source.visibleCount,
                        source.totalCount,
                    )
                    else -> pluralStringResource(
                        R.plurals.folders_pdf_count,
                        source.totalCount,
                        source.totalCount,
                    )
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

/**
 * The tile that adds a source.
 *
 * Outlined rather than filled, so it reads as an empty slot waiting to be
 * filled rather than as another place documents are already coming from.
 */
@Composable
private fun AddSourceTile(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(AddTileWidth)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.md),
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = stringResource(R.string.library_source_add),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier.padding(start = Space.sm),
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
private val SourceTileWidth = 200.dp
private val AddTileWidth = 132.dp
