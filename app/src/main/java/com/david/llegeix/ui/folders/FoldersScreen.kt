package com.david.llegeix.ui.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.data.db.dao.FolderWithCount
import com.david.llegeix.ui.common.HighlightColors
import com.david.llegeix.ui.common.resolved
import com.david.llegeix.ui.library.LibraryMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    onOpenFolder: (folderId: Long, name: String) -> Unit,
    onOpenBookmarked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FoldersViewModel = viewModel(factory = FoldersViewModel.Factory),
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val bookmarkedCount by viewModel.bookmarkedCount.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Long?>(null) }
    var colorPickerFor by remember { mutableStateOf<FolderWithCount?>(null) }
    var renameTarget by remember { mutableStateOf<FolderWithCount?>(null) }

    val messageText = message?.resolved()
    LaunchedEffect(messageText) {
        val text = messageText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onMessageShown()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.folders_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.folders_new),
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            // The automatic collection, always first and not user-editable.
            item {
                BookmarkedCollectionRow(
                    count = bookmarkedCount,
                    onClick = onOpenBookmarked,
                )
            }

            if (folders.isEmpty()) {
                item {
                    LibraryMessage(
                        title = stringResource(R.string.folders_empty_title),
                        body = stringResource(R.string.folders_empty_body),
                    )
                }
            } else {
                items(folders, key = { it.id }) { folder ->
                    FolderRow(
                        folder = folder,
                        onClick = { onOpenFolder(folder.id, folder.name) },
                        onTogglePinned = { viewModel.setPinned(folder.id, !folder.isPinned) },
                        onToggleBookmarked = {
                            viewModel.setBookmarked(folder.id, !folder.isBookmarked)
                        },
                        onPickColor = { colorPickerFor = folder },
                        onRename = { renameTarget = folder },
                        onDelete = { pendingDelete = folder.id },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        FolderNameDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                viewModel.createFolder(name)
                showCreateDialog = false
            },
        )
    }

    renameTarget?.let { folder ->
        FolderNameDialog(
            title = stringResource(R.string.folders_rename_title),
            confirmLabel = stringResource(R.string.action_rename),
            initialName = folder.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                viewModel.renameFolder(folder.id, name)
                renameTarget = null
            },
        )
    }

    colorPickerFor?.let { folder ->
        FolderColorDialog(
            current = folder.colorArgb,
            onDismiss = { colorPickerFor = null },
            onChoose = { color ->
                viewModel.setColor(folder.id, color)
                colorPickerFor = null
            },
        )
    }

    pendingDelete?.let { folderId ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.folders_delete_title)) },
            text = { Text(stringResource(R.string.folders_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFolder(folderId)
                        pendingDelete = null
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * The automatic "Bookmarked" collection.
 *
 * Rendered here so bookmarked PDFs are reachable from Folders as expected, but
 * it is a live view of the bookmark flag rather than a folder row — so it can
 * never drift out of sync, and filing a PDF here cannot displace it from a real
 * folder.
 */
@Composable
private fun BookmarkedCollectionRow(
    count: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier
            .weight(1f)
            .padding(start = 16.dp)) {
            Text(
                text = stringResource(R.string.bookmarked_collection_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = if (count == 0) {
                    stringResource(R.string.folders_bookmarked_auto)
                } else {
                    pluralStringResource(R.plurals.folders_pdf_count, count, count)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun FolderRow(
    folder: FolderWithCount,
    onClick: () -> Unit,
    onTogglePinned: () -> Unit,
    onToggleBookmarked: () -> Unit,
    onPickColor: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_folder),
            contentDescription = null,
            tint = folder.colorArgb?.let { HighlightColors.compose(it) }
                ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(folder.name, style = MaterialTheme.typography.bodyLarge)
                if (folder.isBookmarked) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = stringResource(R.string.document_bookmarked),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(16.dp),
                    )
                }
            }
            Text(
                text = listOfNotNull(
                    stringResource(R.string.folders_pinned).takeIf { folder.isPinned },
                    if (folder.documentCount == 0) {
                        stringResource(R.string.folders_empty_count)
                    } else {
                        pluralStringResource(
                            R.plurals.folders_pdf_count,
                            folder.documentCount,
                            folder.documentCount,
                        )
                    },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more),
                    contentDescription = stringResource(
                        R.string.document_actions,
                        folder.name,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (folder.isPinned) {
                                    R.string.folders_unpin
                                } else {
                                    R.string.folders_pin
                                },
                            ),
                        )
                    },
                    onClick = { onTogglePinned(); menuOpen = false },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (folder.isBookmarked) {
                                    R.string.document_remove_bookmark
                                } else {
                                    R.string.folders_bookmark
                                },
                            ),
                        )
                    },
                    onClick = { onToggleBookmarked(); menuOpen = false },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.folders_colour)) },
                    onClick = { onPickColor(); menuOpen = false },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.folders_rename)) },
                    onClick = { onRename(); menuOpen = false },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = { onDelete(); menuOpen = false },
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun FolderColorDialog(
    current: Int?,
    onDismiss: () -> Unit,
    onChoose: (Int?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folders_colour_title)) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HighlightColors.palette.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(HighlightColors.compose(color))
                                .border(
                                    width = if (color == current) 3.dp else 1.dp,
                                    color = if (color == current) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        Color.Black.copy(alpha = 0.2f)
                                    },
                                    shape = CircleShape,
                                )
                                .clickable { onChoose(color) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (color == current) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.Black.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                TextButton(
                    onClick = { onChoose(null) },
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text(stringResource(R.string.folders_no_colour)) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    )
}

@Composable
fun FolderNameDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initialName: String = "",
    title: String = stringResource(R.string.folders_new),
    confirmLabel: String = stringResource(R.string.action_create),
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.folders_name_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
