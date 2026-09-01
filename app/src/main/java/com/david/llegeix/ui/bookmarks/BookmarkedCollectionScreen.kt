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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import com.david.llegeix.ui.common.CoverAspectRatio
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.PdfCover
import com.david.llegeix.ui.library.ListCoverWidth
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
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
                contentPadding = PaddingValues(bottom = Space.xxl),
            ) {
                items(documents, key = { it.uriString }) { document ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenDocument(document.uriString, document.displayName)
                            }
                            .padding(
                                start = Space.screen,
                                end = Space.screen,
                                top = Space.row,
                                bottom = Space.row,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PdfCover(
                            uriString = document.uriString,
                            width = ListCoverWidth,
                            modifier = Modifier
                                .width(ListCoverWidth)
                                .aspectRatio(CoverAspectRatio)
                                .padding(end = Space.lg),
                        )
                        Text(
                            text = pdfTitle(document.displayName),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
