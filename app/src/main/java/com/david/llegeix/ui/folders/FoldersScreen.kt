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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.data.db.dao.FolderWithCount
import com.david.llegeix.ui.common.HighlightColors
import com.david.llegeix.ui.common.MenuIcon
import com.david.llegeix.ui.common.resolved
import androidx.compose.foundation.layout.PaddingValues
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.Space

/**
 * The reader's own collections, as one pane of the Saved tab.
 *
 * A pane rather than a screen: it draws its list and its dialogs and nothing
 * else. The bar above it, the snackbar under it and the button that makes a new
 * collection all belong to [com.david.llegeix.ui.saved.SavedScreen], because
 * three panes sharing one set of chrome is the whole point of putting them
 * together — a tab row with a top bar per tab is two rows of furniture over
 * every list.
 *
 * @param showCreateDialog hoisted, because the button that raises it is up in
 *   the chrome and the dialog it raises belongs down here with the collections.
 */
@Composable
fun CollectionsPane(
    onOpenFolder: (folderId: Long, name: String) -> Unit,
    onOpenBookmarked: () -> Unit,
    onOpenReadLater: () -> Unit,
    onOpenRecent: () -> Unit,
    snackbarHostState: SnackbarHostState,
    showCreateDialog: Boolean,
    onCreateDialogDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the collection with its picker already up, straight after making it. */
    onFillNewCollection: (folderId: Long, name: String) -> Unit = onOpenFolder,
    viewModel: FoldersViewModel = viewModel(factory = FoldersViewModel.Factory),
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val bookmarkedCount by viewModel.bookmarkedCount.collectAsStateWithLifecycle()
    val readLaterCount by viewModel.readLaterCount.collectAsStateWithLifecycle()
    val recentCount by viewModel.recentCount.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Long?>(null) }
    var colorPickerFor by remember { mutableStateOf<FolderWithCount?>(null) }
    var renameTarget by remember { mutableStateOf<FolderWithCount?>(null) }
    var addingTo by remember { mutableStateOf<FolderWithCount?>(null) }

    val created by viewModel.created.collectAsStateWithLifecycle()
    LaunchedEffect(created) {
        val collection = created ?: return@LaunchedEffect
        viewModel.onCreatedHandled()
        onFillNewCollection(collection.id, collection.name)
    }

    val messageText = message?.resolved()
    LaunchedEffect(messageText) {
        val text = messageText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onMessageShown()
    }


    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // Room at the bottom for the button that makes a new collection, so the
        // last row can always be scrolled clear of it.
        contentPadding = PaddingValues(bottom = Space.huge),
    ) {
        // The three automatic collections, always first and not user-editable.
        //
        // They are grouped rather than listed one per card, because they are
        // the same kind of thing three times over: shelves the app keeps for
        // you, as against the ones you build. Starred and read-later are flags
        // on a document; "Recently viewed" is the reading history. All three
        // used to be somewhere else — two of them in the library, as a chip and
        // a menu item — which meant the answer to "where did I put that" was
        // spread across two tabs.
        item {
            AutomaticCollections(
                bookmarkedCount = bookmarkedCount,
                readLaterCount = readLaterCount,
                recentCount = recentCount,
                onOpenBookmarked = onOpenBookmarked,
                onOpenReadLater = onOpenReadLater,
                onOpenRecent = onOpenRecent,
            )
        }

        if (folders.isEmpty()) {
            item {
                // Not the shared EmptyState: this list already has the
                // bookmarked collection above it, so filling the screen and
                // centring would push that row off the top.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.xxl)
                        .padding(top = Space.huge),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.collections_empty_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.collections_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = Space.md),
                    )
                }
            }
        } else {
            items(folders, key = { it.id }) { folder ->
                FolderRow(
                    folder = folder,
                    onClick = { onOpenFolder(folder.id, folder.name) },
                    onAddDocuments = { addingTo = folder },
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

    addingTo?.let { folder ->
        AddToCollectionSheet(
            collectionId = folder.id,
            collectionName = folder.name,
            onDismiss = { addingTo = null },
        )
    }

    if (showCreateDialog) {
        FolderNameDialog(
            onDismiss = onCreateDialogDismissed,
            onConfirm = { name ->
                viewModel.createFolder(name)
                onCreateDialogDismissed()
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
 * The shelves the app keeps for you, as one card of three rows.
 *
 * None of them is a real folder, and none of them can be: a document belongs to
 * at most one folder, so making any of these a folder row would quietly pull
 * every PDF in it out of the collection the reader had filed it in. They are
 * live views — of the bookmark flag, of the read-later flag, of the reading
 * history — so they cannot drift out of sync with what they are views of.
 *
 * One card rather than three separate ones, and above the reader's own
 * collections rather than mixed in with them, so the two kinds are told apart
 * by looking rather than by remembering which names are special.
 */
@Composable
private fun AutomaticCollections(
    bookmarkedCount: Int,
    readLaterCount: Int,
    recentCount: Int,
    onOpenBookmarked: () -> Unit,
    onOpenReadLater: () -> Unit,
    onOpenRecent: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen)
            .padding(top = Space.sm, bottom = Space.lg)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        AutomaticCollectionRow(
            icon = { tint ->
                Icon(Icons.Filled.Star, contentDescription = null, tint = tint)
            },
            title = stringResource(R.string.bookmarked_collection_title),
            count = bookmarkedCount,
            onClick = onOpenBookmarked,
        )
        AutomaticCollectionRow(
            icon = { tint ->
                Icon(painterResource(R.drawable.ic_bookmark), contentDescription = null, tint = tint)
            },
            title = stringResource(R.string.read_later_collection_title),
            count = readLaterCount,
            onClick = onOpenReadLater,
        )
        AutomaticCollectionRow(
            icon = { tint ->
                Icon(painterResource(R.drawable.ic_recent), contentDescription = null, tint = tint)
            },
            title = stringResource(R.string.recent_title),
            count = recentCount,
            // The history is a list of visits rather than a shelf of PDFs, so
            // it says what it holds instead of counting PDFs: "3 PDFs" on a
            // reading history would be counting the wrong noun.
            summary = stringResource(R.string.recent_collection_summary),
            onClick = onOpenRecent,
        )
    }
}

@Composable
private fun AutomaticCollectionRow(
    icon: @Composable (Color) -> Unit,
    title: String,
    count: Int,
    onClick: () -> Unit,
    summary: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(MaterialTheme.colorScheme.primary)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.lg),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = when {
                    summary != null && count > 0 -> summary
                    count == 0 -> stringResource(R.string.folders_bookmarked_auto)
                    else -> pluralStringResource(R.plurals.folders_pdf_count, count, count)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        // The count only where the second line is not already carrying it: the
        // history says what it holds rather than counting PDFs, so it is the
        // one row with room for a number at the end.
        if (summary != null && count > 0) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FolderRow(
    folder: FolderWithCount,
    onClick: () -> Unit,
    onAddDocuments: () -> Unit,
    onTogglePinned: () -> Unit,
    onToggleBookmarked: () -> Unit,
    onPickColor: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    val tint = folder.colorArgb?.let { HighlightColors.compose(it) }
        ?: MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = Space.screen, top = Space.row, bottom = Space.row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The colour as a disc behind the icon rather than on the glyph itself.
        // Tinting a 24dp outline is the smallest possible way to show a colour
        // someone deliberately chose, and at a glance down a list of folders it
        // was doing almost none of the telling-apart it was picked for.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_collection),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(folder.name, style = MaterialTheme.typography.titleMedium)
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
                modifier = Modifier.padding(top = 2.dp),
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
                    leadingIcon = { MenuIcon(Icons.Default.Add) },
                    text = { Text(stringResource(R.string.collections_add_action)) },
                    onClick = { onAddDocuments(); menuOpen = false },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    leadingIcon = { MenuIcon(painterResource(R.drawable.ic_pin)) },
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
                    leadingIcon = { MenuIcon(Icons.Default.Star) },
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
                    leadingIcon = { MenuIcon(painterResource(R.drawable.ic_circle)) },
                    text = { Text(stringResource(R.string.folders_colour)) },
                    onClick = { onPickColor(); menuOpen = false },
                )
                DropdownMenuItem(
                    leadingIcon = { MenuIcon(Icons.Default.Edit) },
                    text = { Text(stringResource(R.string.folders_rename)) },
                    onClick = { onRename(); menuOpen = false },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    leadingIcon = { MenuIcon(Icons.Default.Delete) },
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = { onDelete(); menuOpen = false },
                )
            }
        }
    }
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
    title: String = stringResource(R.string.collections_new),
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
