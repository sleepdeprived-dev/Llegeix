package com.david.llegeix.ui.folders

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
import androidx.compose.material.icons.filled.Close
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    folderId: Long,
    folderName: String,
    onBack: () -> Unit,
    onOpenDocument: (uriString: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: FolderDetailViewModel = viewModel(
        key = "folder-$folderId",
        factory = FolderDetailViewModel.factory(folderId),
    )
    val documents by viewModel.documents.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
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
    ) { innerPadding ->
        if (documents.isEmpty()) {
            LibraryMessage(
                title = stringResource(R.string.folder_detail_empty_title),
                body = stringResource(R.string.folder_detail_empty_body),
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            LazyColumn(modifier = Modifier.padding(innerPadding)) {
                items(documents, key = { it.uriString }) { document ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenDocument(document.uriString, document.displayName)
                            }
                            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}
