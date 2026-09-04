package com.david.llegeix.ui.folders

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.david.llegeix.ui.common.CoverAspectRatio
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
@OptIn(ExperimentalMaterial3Api::class)
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
            // Extended, and only here. A bare plus on a screen showing a list of
            // books would be read as "add a book to my phone"; the word is what
            // says it is about this collection.
            if (documents.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                        )
                    },
                    text = { Text(stringResource(R.string.collections_add_action)) },
                )
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenDocument(document.uriString, document.displayName)
                            }
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
                                text = pdfTitle(document.displayName),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { viewModel.removeFromFolder(document.uriString) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(
                                    R.string.folder_detail_remove,
                                ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
}
