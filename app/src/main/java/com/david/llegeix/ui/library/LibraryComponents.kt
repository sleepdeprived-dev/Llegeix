package com.david.llegeix.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.david.llegeix.R
import com.david.llegeix.data.model.PdfDocument
import com.david.llegeix.data.model.PdfOrigin
import com.david.llegeix.data.source.GrantedFolder
import com.david.llegeix.util.formatModified
import com.david.llegeix.util.formatSize

/** One PDF in the library list, with its per-document actions. */
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
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.pdf_badge),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
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
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
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
                        menuOpen = false
                    },
                )
                DropdownMenuItem(
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
                        menuOpen = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.document_move_to_folder)) },
                    onClick = {
                        onMoveToFolder()
                        menuOpen = false
                    },
                )
            }
        }
    }
}

/**
 * The two ways of adding PDFs, plus a chip per folder already being watched.
 *
 * Presented as buttons rather than the previous chip row: these are the primary
 * actions on an empty library, and burying them in a horizontally scrolling
 * strip made them read as secondary.
 */
@Composable
fun SourceButtons(
    folders: List<GrantedFolder>,
    deviceScanEnabled: Boolean,
    onScanDevice: () -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFolder: (GrantedFolder) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!deviceScanEnabled) {
                Button(
                    onClick = onScanDevice,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.action_scan_device)) }
            }
            OutlinedButton(
                onClick = onAddFolder,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.action_add_folder)) }
        }

        if (folders.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                items(folders) { folder ->
                    InputChip(
                        selected = true,
                        onClick = { onRemoveFolder(folder) },
                        label = {
                            Text(
                                text = folder.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 160.dp),
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(
                                    R.string.source_stop_watching,
                                    folder.label,
                                ),
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * Offers the optional whole-device sweep. Shown only while the grant is absent,
 * since there is nothing to do once it is held.
 */
@Composable
fun DeviceScanCard(
    onEnable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.device_scan_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.device_scan_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(
                onClick = onEnable,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.action_open_settings))
            }
        }
    }
}

/** Centred message used for every empty / not-yet-set-up state. */
@Composable
fun LibraryMessage(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (action != null) {
            Box(modifier = Modifier.padding(top = 16.dp)) { action() }
        }
    }
}
