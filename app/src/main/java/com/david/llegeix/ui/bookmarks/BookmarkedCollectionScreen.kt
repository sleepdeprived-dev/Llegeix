package com.david.llegeix.ui.bookmarks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.data.db.entity.DocumentEntity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.res.painterResource
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.Space
import com.david.llegeix.util.pdfTitle

/**
 * The automatic "Bookmarked" collection, opened from the folder list.
 *
 * Shares [BookmarksViewModel] with the Bookmarks tab, so the two can never
 * disagree about what is bookmarked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkedCollectionScreen(
    onBack: () -> Unit,
    onOpenDocument: (uriString: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookmarksViewModel = viewModel(factory = BookmarksViewModel.Factory),
) {
    val documents by viewModel.bookmarkedDocuments.collectAsStateWithLifecycle()
    val tagsByDocument by viewModel.tagsByDocument.collectAsStateWithLifecycle()
    val names by viewModel.names.collectAsStateWithLifecycle()
    var tagsFor by remember { mutableStateOf<DocumentEntity?>(null) }
    var renaming by remember { mutableStateOf<DocumentEntity?>(null) }

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
                            contentDescription = stringResource(
                                R.string.bookmarked_collection_back,
                            ),
                        )
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.bookmarked_collection_title),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (documents.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.bookmarked_collection_empty_title),
                body = stringResource(R.string.bookmarked_collection_empty_body),
                icon = painterResource(R.drawable.ic_bookmark),
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(bottom = Space.lg),
            ) {
                items(documents, key = { it.uriString }) { document ->
                    // The same row the Saved tab draws, rather than a plainer
                    // one of this screen's own. This list used to exist twice —
                    // here and as a tab — and the tab was the one that showed
                    // the document's tags and let a PDF be unstarred from the
                    // list. Keeping one list meant keeping the better row.
                    BookmarkRow(
                        title = names.titleFor(document.uriString, document.displayName),
                        subtitle = null,
                        documentUri = document.uriString,
                        tags = tagsByDocument[document.uriString].orEmpty(),
                        onClick = {
                            onOpenDocument(
                                document.uriString,
                                names.titleFor(document.uriString, document.displayName),
                            )
                        },
                        onEditTags = { tagsFor = document },
                        onRename = { renaming = document },
                        onRemove = { viewModel.removeDocumentBookmark(document) },
                    )
                }
            }
        }
    }

    SavedDocumentDialogs(
        tagsFor = tagsFor,
        renaming = renaming,
        onDismissTags = { tagsFor = null },
        onDismissRename = { renaming = null },
        viewModel = viewModel,
    )
}
