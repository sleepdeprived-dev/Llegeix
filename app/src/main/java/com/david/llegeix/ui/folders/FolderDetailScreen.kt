package com.david.llegeix.ui.folders

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.data.db.entity.DocumentEntity
import com.david.llegeix.ui.common.CoverAspectRatio
import com.david.llegeix.ui.common.MenuIcon
import com.david.llegeix.ui.common.TagPickerDialog
import com.david.llegeix.ui.common.TagStrip
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.PdfCover
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.library.ListCoverWidth
import com.david.llegeix.util.pdfTitle

/**
 * One collection, and the way to fill it.
 *
 * The button is the point of this screen. Making a collection used to be the
 * easy half and filling it the hard one: every PDF had to be found in the
 * library, opened at its overflow menu, and filed through a dialog, one at a
 * time, with the collection you were building never once on screen. Now the
 * collection is the screen, and everything you own is one press away with a
 * tick beside it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FolderDetailScreen(
    folderId: Long,
    folderName: String,
    onBack: () -> Unit,
    onOpenDocument: (uriString: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
    /** True when the reader has just made this collection and it is empty. */
    startAdding: Boolean = false,
) {
    val viewModel: FolderDetailViewModel = viewModel(
        key = "folder-$folderId",
        factory = FolderDetailViewModel.factory(folderId),
    )
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val names by viewModel.names.collectAsStateWithLifecycle()
    val allTags by viewModel.tags.collectAsStateWithLifecycle()
    val tagsByDocument by viewModel.tagsByDocument.collectAsStateWithLifecycle()
    var tagsFor by remember { mutableStateOf<DocumentEntity?>(null) }
    var renaming by remember { mutableStateOf<DocumentEntity?>(null) }
    // A collection made a moment ago is a collection about to be filled, so the
    // picker is already up rather than waiting behind a button on an empty
    // screen.
    var adding by remember { mutableStateOf(startAdding) }

    Scaffold(
        // The app shell's Scaffold has already inset this screen for the
        // status bar and the navigation bar; counting them a second time
        // put a dead band above the bottom bar and made every top bar
        // 24dp taller than it asks to be.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                expandedHeight = Space.topBar,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.folder_detail_back),
                        )
                    }
                },
                title = { Text(folderName) },
            )
        },
        floatingActionButton = {
            // A bare plus, like every add button in the app; what it adds to is
            // the collection whose name is in the bar above it, and the words
            // are kept for anyone listening rather than looking.
            if (documents.isNotEmpty()) {
                FloatingActionButton(onClick = { adding = true }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.collections_add_action),
                    )
                }
            }
        },
    ) { innerPadding ->
        if (documents.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.folder_detail_empty_title),
                body = stringResource(R.string.folder_detail_empty_body),
                icon = painterResource(R.drawable.ic_collection),
                modifier = Modifier.padding(innerPadding),
                primaryAction = {
                    Button(onClick = { adding = true }) {
                        Text(stringResource(R.string.collections_add_action))
                    }
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                // Clear of the button, which would otherwise sit on top of the
                // last document in the collection.
                contentPadding = PaddingValues(bottom = Space.huge + Space.xl),
            ) {
                items(documents, key = { it.uriString }) { document ->
                    var menuOpen by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Held rather than tapped: the same gesture as the
                            // library's rows, landing on the same three things
                            // — the tags, the name, and taking it off the
                            // shelf. A PDF should not answer to different
                            // gestures depending on which list it is met in.
                            .combinedClickable(
                                onClick = {
                                    onOpenDocument(
                                        document.uriString,
                                        names.titleFor(
                                            document.uriString,
                                            document.displayName,
                                        ),
                                    )
                                },
                                onLongClick = { menuOpen = true },
                            )
                            .padding(start = Space.screen, top = Space.row, bottom = Space.row),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The gap after the cover is the row's, not the
                        // cover's: inside the modifier chain the padding came
                        // off the width the aspect ratio was then applied to,
                        // so every cover on this screen was drawn a third too
                        // narrow and looked nothing like the same object as the
                        // one in the library.
                        PdfCover(
                            uriString = document.uriString,
                            width = ListCoverWidth,
                            progress = progress[document.uriString]?.fraction,
                            modifier = Modifier
                                .width(ListCoverWidth)
                                .aspectRatio(CoverAspectRatio),
                        )
                        Spacer(modifier = Modifier.width(Space.lg))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = names.titleFor(
                                    document.uriString,
                                    document.displayName,
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TagStrip(
                            tags = tagsByDocument[document.uriString].orEmpty(),
                            modifier = Modifier.padding(start = Space.sm),
                        )
                        Box {
                            IconButton(
                                onClick = { viewModel.removeFromFolder(document.uriString) },
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(
                                        R.string.folder_detail_remove,
                                    ),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = {
                                        MenuIcon(painterResource(R.drawable.ic_tag))
                                    },
                                    text = { Text(stringResource(R.string.tags_open)) },
                                    onClick = { menuOpen = false; tagsFor = document },
                                )
                                DropdownMenuItem(
                                    leadingIcon = { MenuIcon(Icons.Default.Edit) },
                                    text = { Text(stringResource(R.string.document_rename)) },
                                    onClick = { menuOpen = false; renaming = document },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    leadingIcon = { MenuIcon(Icons.Default.Close) },
                                    text = {
                                        Text(stringResource(R.string.folder_detail_remove))
                                    },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.removeFromFolder(document.uriString)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        AddToCollectionSheet(
            collectionId = folderId,
            collectionName = folderName,
            onDismiss = { adding = false },
        )
    }

    tagsFor?.let { document ->
        TagPickerDialog(
            documentTitle = names.titleFor(document.uriString, document.displayName),
            allTags = allTags,
            selectedIds = tagsByDocument[document.uriString].orEmpty().map { it.id }.toSet(),
            onToggle = { viewModel.onToggleTag(document, it) },
            onCreate = { name, colour -> viewModel.onCreateTag(document, name, colour) },
            onRecolour = viewModel::onRecolourTag,
            onRename = viewModel::onRenameTag,
            onDelete = viewModel::onDeleteTag,
            onDismiss = { tagsFor = null },
        )
    }

    renaming?.let { document ->
        CollectionDocumentNameDialog(
            current = names.titleFor(document.uriString, document.displayName),
            hasCustomName = names.isRenamed(document.uriString),
            onDismiss = { renaming = null },
            onConfirm = { name ->
                viewModel.onRenameDocument(document, name)
                renaming = null
            },
        )
    }
}

/**
 * Rename a PDF from inside a collection, in the app only.
 *
 * The note under the field is the reason this is a dialog rather than an inline
 * edit: "rename" on a phone means renaming the file, and this does not. Llegeix
 * holds a read-only grant on the reader's PDFs and never writes to them.
 */
@Composable
private fun CollectionDocumentNameDialog(
    current: String,
    hasCustomName: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.document_rename_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.document_rename_label)) },
                    singleLine = true,
                )
                Text(
                    text = stringResource(R.string.document_rename_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.md),
                )
                if (hasCustomName) {
                    TextButton(
                        onClick = { onConfirm("") },
                        modifier = Modifier.padding(top = Space.sm),
                    ) { Text(stringResource(R.string.document_rename_reset)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
