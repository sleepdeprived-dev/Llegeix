package com.david.llegeix.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.david.llegeix.R
import com.david.llegeix.data.model.PdfDocument
import com.david.llegeix.ui.common.CoverAspectRatio
import com.david.llegeix.ui.common.MenuIcon
import com.david.llegeix.ui.common.PdfCover
import com.david.llegeix.data.db.dao.DocumentTag
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.common.TagStrip
import com.david.llegeix.util.formatModified
import com.david.llegeix.util.formatSize

/**
 * One PDF in the library list.
 *
 * No divider underneath: at this row height the title, its detail line and the
 * gap to the next row carry the separation on their own, and a rule between
 * every pair of rows turns a list of ten books into a list of twenty lines.
 */
@Composable
fun DocumentRow(
    document: PdfDocument,
    isBookmarked: Boolean,
    isReadLater: Boolean,
    tags: List<DocumentTag>,
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = Space.screen, top = Space.row, bottom = Space.row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PdfCover(
            uriString = document.uriString,
            width = ListCoverWidth,
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
                        contentDescription = stringResource(R.string.document_bookmarked),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 2.dp),
                    )
                }
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val readLater = stringResource(R.string.document_read_later)
            val details = listOfNotNull(
                readLater.takeIf { isReadLater },
                // Ahead of the folder on disk, and phrased so the two cannot be
                // mistaken for each other: this one is a decision the reader
                // made, the other is where the file happens to live.
                folderName?.let { stringResource(R.string.library_filed_in, it) },
                document.parentLabel?.takeIf { showLocation && it.isNotBlank() },
                formatSize(context, document.sizeBytes).takeIf { it.isNotBlank() },
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
                    contentDescription = stringResource(
                        R.string.document_actions,
                        document.title,
                    ),
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
            )
        }
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
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            leadingIcon = { MenuIcon(Icons.Default.Star) },
            text = {
                Text(
                    stringResource(
                        if (isBookmarked) {
                            R.string.document_remove_bookmark
                        } else {
                            R.string.document_add_bookmark
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
            leadingIcon = { MenuIcon(painterResource(R.drawable.ic_bookmark)) },
            text = {
                Text(
                    stringResource(
                        if (isReadLater) {
                            R.string.document_remove_read_later
                        } else {
                            R.string.document_read_later
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
            leadingIcon = { MenuIcon(painterResource(R.drawable.ic_folder)) },
            text = { Text(stringResource(R.string.document_move_to_folder)) },
            onClick = {
                onMoveToFolder()
                onDismiss()
            },
        )
        DropdownMenuItem(
            leadingIcon = { MenuIcon(painterResource(R.drawable.ic_tag)) },
            text = { Text(stringResource(R.string.tags_open)) },
            onClick = {
                onEditTags()
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
@Composable
fun DocumentCell(
    document: PdfDocument,
    isBookmarked: Boolean,
    isReadLater: Boolean,
    tags: List<DocumentTag>,
    onClick: () -> Unit,
    onMoveToFolder: () -> Unit,
    onToggleReadLater: () -> Unit,
    onToggleBookmarked: () -> Unit,
    onEditTags: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(Space.sm),
    ) {
        Box {
            PdfCover(
                uriString = document.uriString,
                width = GridCoverWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(CoverAspectRatio),
                cornerRadius = 12.dp,
            )
            if (isBookmarked) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = stringResource(R.string.document_bookmarked),
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
                text = document.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(
                            R.string.document_actions,
                            document.title,
                        ),
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

/** Cover sizes, named so the list and the grid stay in proportion. */
val ListCoverWidth = 46.dp
val GridCoverWidth = 150.dp
