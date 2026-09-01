package com.david.llegeix.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.david.llegeix.R
import com.david.llegeix.data.model.PdfDocument
import com.david.llegeix.data.source.GrantedFolder
import com.david.llegeix.ui.common.CoverAspectRatio
import com.david.llegeix.ui.common.MenuEmoji
import com.david.llegeix.ui.common.PdfCover
import com.david.llegeix.ui.common.Space
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
    onClick: () -> Unit,
    onMoveToFolder: () -> Unit,
    onToggleReadLater: () -> Unit,
    onToggleBookmarked: () -> Unit,
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
                document.parentLabel?.takeIf { it.isNotBlank() },
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
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            leadingIcon = { MenuEmoji(if (isBookmarked) "💔" else "⭐") },
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
            leadingIcon = { MenuEmoji(if (isReadLater) "✅" else "🔖") },
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
            leadingIcon = { MenuEmoji("📁") },
            text = { Text(stringResource(R.string.document_move_to_folder)) },
            onClick = {
                onMoveToFolder()
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
    onClick: () -> Unit,
    onMoveToFolder: () -> Unit,
    onToggleReadLater: () -> Unit,
    onToggleBookmarked: () -> Unit,
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
                )
            }
        }
    }
}

/** Cover sizes, named so the list and the grid stay in proportion. */
val ListCoverWidth = 46.dp
val GridCoverWidth = 150.dp

/**
 * Everything to do with where PDFs come from, in one place.
 *
 * Previously two buttons and a chip row sitting above the library at all times.
 * Setting up a source is a one-off, so keeping it permanently on screen spent
 * the most valuable part of the layout on a task already finished.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesSheet(
    folders: List<GrantedFolder>,
    deviceScanEnabled: Boolean,
    onDismiss: () -> Unit,
    onScanDevice: () -> Unit,
    onAddFolder: () -> Unit,
    onAddFiles: () -> Unit,
    onRemoveFolder: (GrantedFolder) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = Space.xl),
        ) {
            Column(modifier = Modifier.padding(horizontal = Space.xl)) {
                Text(
                    text = stringResource(R.string.library_sources),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.library_sources_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }

            Column(modifier = Modifier.padding(top = Space.xl)) {
                SourceAction(
                    // A sweep of the device, not another thing to add.
                    emoji = if (deviceScanEnabled) "✅" else "🔍",
                    title = stringResource(R.string.action_scan_device),
                    subtitle = stringResource(
                        if (deviceScanEnabled) {
                            R.string.library_device_scan_on
                        } else {
                            R.string.library_device_scan_off
                        },
                    ),
                    onClick = {
                        onScanDevice()
                        onDismiss()
                    },
                )
                SourceAction(
                    emoji = "📁",
                    title = stringResource(R.string.action_add_folder),
                    onClick = {
                        onAddFolder()
                        onDismiss()
                    },
                )
                SourceAction(
                    emoji = "📄",
                    title = stringResource(R.string.library_add_files),
                    onClick = {
                        onAddFiles()
                        onDismiss()
                    },
                )
            }

            if (folders.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.lg),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text = stringResource(R.string.library_watched_folders),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.sm),
                )
                folders.forEach { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = Space.xl, top = Space.md, bottom = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = folder.label,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onRemoveFolder(folder) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(
                                    R.string.source_stop_watching,
                                    folder.label,
                                ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceAction(
    emoji: String,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.xl, vertical = Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuEmoji(emoji)
        Column(modifier = Modifier.padding(start = Space.lg)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
