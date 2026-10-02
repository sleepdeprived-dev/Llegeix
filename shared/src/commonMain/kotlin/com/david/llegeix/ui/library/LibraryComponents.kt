package com.david.llegeix.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.david.llegeix.data.model.PdfDocument
import com.david.llegeix.resources.*
import com.david.llegeix.ui.common.CoverAspectRatio
import com.david.llegeix.ui.common.MenuIcon
import com.david.llegeix.ui.common.PdfCover
import com.david.llegeix.data.db.dao.DocumentTag
import com.david.llegeix.data.db.dao.ReadingProgress
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.common.TagStrip
import com.david.llegeix.util.formatModified
import com.david.llegeix.util.formatSize
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * One PDF in the library list.
 *
 * No divider underneath: at this row height the title, its detail line and the
 * gap to the next row carry the separation on their own, and a rule between
 * every pair of rows turns a list of ten books into a list of twenty lines.
 *
 * The two things most often done to a document are done by swiping it. They
 * were both four taps away behind the overflow menu — which is still there, and
 * still holds everything — but starring a book you have just spotted should not
 * be a menu transaction, and a menu button on every row is a piece of furniture
 * on every row.
 *
 * Holding the row opens that same menu. The three dots at the end are a small
 * target at the far edge of a wide row, and "press and hold the thing you mean"
 * is the gesture every file list on the phone already answers to — so the row
 * answers to it as well, and lands on exactly the menu the dots would have
 * opened. Two ways in to one menu, not two menus.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DocumentRow(
    document: PdfDocument,
    /** What to call it: the reader's own name, or the file's. */
    title: String,
    isBookmarked: Boolean,
    isReadLater: Boolean,
    tags: List<DocumentTag>,
    /** How far through this one the reader got, or null if never opened. */
    progress: ReadingProgress?,
    /** The user's folder this PDF is filed in, or null if it is unfiled. */
    folderName: String?,
    /**
     * Whether to say where on the device the file is.
     *
     * False while browsing folders: every row in an open folder would repeat
     * the folder the reader is standing in, which is the path bar's job and is
     * three words of noise on every line.
     */
    showLocation: Boolean,
    onClick: () -> Unit,
    onMoveToFolder: () -> Unit,
    onToggleReadLater: () -> Unit,
    onToggleBookmarked: () -> Unit,
    onEditTags: () -> Unit,
    onRename: () -> Unit,
    onSearchInside: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    // Neither direction ever dismisses anything: the row is a document that
    // exists on the phone, and no swipe on this screen should be able to make
    // it stop existing. Refusing the value change is what makes the row do the
    // thing and then spring back, which is also the acknowledgement — the row
    // moving is how the reader knows the swipe was heard.
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onToggleBookmarked()
                SwipeToDismissBoxValue.EndToStart -> onToggleReadLater()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )

    SwipeToDismissBox(
        state = swipeState,
        modifier = modifier,
        backgroundContent = {
            SwipeBackground(
                direction = swipeState.dismissDirection,
                isBookmarked = isBookmarked,
                isReadLater = isReadLater,
            )
        },
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
            .padding(start = Space.screen, top = Space.row, bottom = Space.row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PdfCover(
            uriString = document.uriString,
            width = ListCoverWidth,
            progress = progress?.fraction,
            modifier = Modifier
                .width(ListCoverWidth)
                .aspectRatio(CoverAspectRatio),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isBookmarked) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = stringResource(Res.string.document_bookmarked),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 2.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val readLater = stringResource(Res.string.document_read_later)
            val details = listOfNotNull(
                // First in the line, ahead of everything about the file: how
                // far in you are is the one thing on a row about a book you
                // have already started that you actually want.
                progress?.let { read ->
                    if (read.isFinished) {
                        stringResource(Res.string.library_read_finished)
                    } else {
                        read.fraction?.let { fraction ->
                            stringResource(
                                Res.string.library_read_progress,
                                (fraction * 100).roundToInt(),
                            )
                        }
                    }
                },
                readLater.takeIf { isReadLater },
                // Ahead of the folder on disk, and phrased so the two cannot be
                // mistaken for each other: this one is a decision the reader
                // made, the other is where the file happens to live.
                folderName?.let { stringResource(Res.string.library_filed_in, it) },
                document.parentLabel?.takeIf { showLocation && it.isNotBlank() },
                formatSize(document.sizeBytes).takeIf { it.isNotBlank() },
                formatModified(document.lastModified).takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        // Between the title and the overflow button, with a gap on either side:
        // a coloured chip pressed against a menu or a dismiss button reads as
        // part of it, and it is neither.
        TagStrip(tags = tags, modifier = Modifier.padding(start = Space.sm))

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(Res.string.document_actions, title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DocumentMenu(
                expanded = menuOpen,
                isBookmarked = isBookmarked,
                isReadLater = isReadLater,
                onDismiss = { menuOpen = false },
                onToggleBookmarked = onToggleBookmarked,
                onToggleReadLater = onToggleReadLater,
                onMoveToFolder = onMoveToFolder,
                onEditTags = onEditTags,
                onRename = onRename,
                onSearchInside = onSearchInside,
            )
        }
    }
    }
}

/**
 * What is behind a row being swiped.
 *
 * It says which way the toggle is going, because the two actions are not
 * symmetrical and a bare star would be a lie half the time: swiping a book that
 * is already starred takes the star off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(
    direction: SwipeToDismissBoxValue,
    isBookmarked: Boolean,
    isReadLater: Boolean,
) {
    if (direction == SwipeToDismissBoxValue.Settled) return
    val starring = direction == SwipeToDismissBoxValue.StartToEnd
    val on = if (starring) !isBookmarked else !isReadLater
    val label = stringResource(
        when {
            starring && on -> Res.string.swipe_star
            starring -> Res.string.swipe_unstar
            on -> Res.string.swipe_read_later
            else -> Res.string.swipe_not_read_later
        },
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (starring) Arrangement.Start else Arrangement.End,
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (on) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
            )
            .padding(horizontal = Space.xl),
    ) {
        if (starring) {
            SwipeMark(starring = true, on = on, label = label)
        } else {
            SwipeMark(starring = false, on = on, label = label)
        }
    }
}

@Composable
private fun SwipeMark(starring: Boolean, on: Boolean, label: String) {
    val tint = if (on) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (starring) {
            Icon(
                imageVector = if (on) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = null,
                tint = tint,
            )
        } else {
            Icon(
                painter = painterResource(Res.drawable.ic_bookmark),
                contentDescription = null,
                tint = tint,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            maxLines = 1,
            modifier = Modifier.padding(start = Space.md),
        )
    }
}

/** The per-document actions, shared by the list row and the grid cell. */
@Composable
private fun DocumentMenu(
    expanded: Boolean,
    isBookmarked: Boolean,
    isReadLater: Boolean,
    onDismiss: () -> Unit,
    onToggleBookmarked: () -> Unit,
    onToggleReadLater: () -> Unit,
    onMoveToFolder: () -> Unit,
    onEditTags: () -> Unit,
    onRename: () -> Unit,
    onSearchInside: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        // The library's own search box only ever matched filenames, which is
        // not what a search box on a shelf of books looks like it should do.
        // This is the honest half of the difference: the library finds the
        // book, and this looks inside it — carrying whatever was typed
        // upstairs, so a word searched for in the library is already in the
        // find bar when the document opens.
        DropdownMenuItem(
            leadingIcon = { MenuIcon(Icons.Default.Search) },
            text = { Text(stringResource(Res.string.document_search_inside)) },
            onClick = {
                onSearchInside()
                onDismiss()
            },
        )
        HorizontalDivider()
        DropdownMenuItem(
            leadingIcon = { MenuIcon(Icons.Default.Star) },
            text = {
                Text(
                    stringResource(
                        if (isBookmarked) {
                            Res.string.document_remove_bookmark
                        } else {
                            Res.string.document_add_bookmark
                        },
                    ),
                )
            },
            onClick = {
                onToggleBookmarked()
                onDismiss()
            },
        )
        DropdownMenuItem(
            leadingIcon = { MenuIcon(painterResource(Res.drawable.ic_bookmark)) },
            text = {
                Text(
                    stringResource(
                        if (isReadLater) {
                            Res.string.document_remove_read_later
                        } else {
                            Res.string.document_read_later
                        },
                    ),
                )
            },
            onClick = {
                onToggleReadLater()
                onDismiss()
            },
        )
        DropdownMenuItem(
            leadingIcon = { MenuIcon(painterResource(Res.drawable.ic_folder)) },
            text = { Text(stringResource(Res.string.document_move_to_folder)) },
            onClick = {
                onMoveToFolder()
                onDismiss()
            },
        )
        DropdownMenuItem(
            leadingIcon = { MenuIcon(painterResource(Res.drawable.ic_tag)) },
            text = { Text(stringResource(Res.string.tags_open)) },
            onClick = {
                onEditTags()
                onDismiss()
            },
        )
        // Last, and after a rule, because it is the only item here that changes
        // what the document is called rather than where it is filed — and
        // because it is the one people will look for hardest once they know it
        // exists. What it renames is spelled out in the dialog it opens: the
        // file on the phone is not touched.
        HorizontalDivider()
        DropdownMenuItem(
            leadingIcon = { MenuIcon(Icons.Default.Edit) },
            text = { Text(stringResource(Res.string.document_rename)) },
            onClick = {
                onRename()
                onDismiss()
            },
        )
    }
}

/**
 * One PDF as a cover in the grid.
 *
 * The cover does most of the identifying work here, so the title is allowed two
 * lines and the detail line is dropped entirely — in a grid it would be noise
 * repeated across every cell.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentCell(
    document: PdfDocument,
    /** What to call it: the reader's own name, or the file's. */
    title: String,
    isBookmarked: Boolean,
    isReadLater: Boolean,
    tags: List<DocumentTag>,
    /** How far through this one the reader got, or null if never opened. */
    progress: ReadingProgress?,
    onClick: () -> Unit,
    onMoveToFolder: () -> Unit,
    onToggleReadLater: () -> Unit,
    onToggleBookmarked: () -> Unit,
    onEditTags: () -> Unit,
    onRename: () -> Unit,
    onSearchInside: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
            .padding(Space.sm),
    ) {
        Box {
            PdfCover(
                uriString = document.uriString,
                width = GridCoverWidth,
                progress = progress?.fraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(CoverAspectRatio),
                cornerRadius = 12.dp,
            )
            if (isBookmarked) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = stringResource(Res.string.document_bookmarked),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Space.sm)
                        .size(18.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box {
                // A full-sized target with a small glyph in it, rather than a
                // small target. It was 28dp square, which is under the 48dp
                // minimum and, in a grid of covers, sat a few pixels from the
                // cover that opens the book — so missing the menu opened the
                // document instead.
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(GridMenuTarget),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(Res.string.document_actions, title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DocumentMenu(
                    expanded = menuOpen,
                    isBookmarked = isBookmarked,
                    isReadLater = isReadLater,
                    onDismiss = { menuOpen = false },
                    onToggleBookmarked = onToggleBookmarked,
                    onToggleReadLater = onToggleReadLater,
                    onMoveToFolder = onMoveToFolder,
                    onEditTags = onEditTags,
                    onRename = onRename,
                    onSearchInside = onSearchInside,
                )
            }
        }
        // In a grid the cover carries the identity, so tags sit under the title
        // rather than competing with it for the same line.
        TagStrip(
            tags = tags,
            maxVisible = 3,
            maxWidth = GridCoverWidth,
            modifier = Modifier.padding(top = Space.xs),
        )
    }
}

/**
 * The grid cell's overflow target.
 *
 * The platform minimum. The glyph inside stays small — a cell is mostly cover,
 * and a full-weight icon on it would compete with the artwork — but what the
 * thumb has to hit is the whole 48dp.
 */
private val GridMenuTarget = 48.dp

/** Cover sizes, named so the list and the grid stay in proportion. */
val ListCoverWidth = 46.dp
val GridCoverWidth = 150.dp
