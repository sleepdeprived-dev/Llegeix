package com.david.llegeix.ui.bookmarks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.ui.library.LibraryMessage
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
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
            LibraryMessage(
                title = stringResource(R.string.bookmarked_collection_empty_title),
                body = stringResource(R.string.bookmarked_collection_empty_body),
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            LazyColumn(modifier = Modifier.padding(innerPadding)) {
                items(documents, key = { it.uriString }) { document ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenDocument(document.uriString, document.displayName)
                            }
                            .padding(start = 16.dp, top = 14.dp, bottom = 14.dp),
                    ) {
                        Text(
                            text = pdfTitle(document.displayName),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}
