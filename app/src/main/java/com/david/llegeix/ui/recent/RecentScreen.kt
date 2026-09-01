package com.david.llegeix.ui.recent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.ui.library.LibraryMessage
import com.david.llegeix.util.pdfTitle
import com.david.llegeix.util.formatModified

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentScreen(
    onOpenDocument: (uriString: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecentViewModel = viewModel(factory = RecentViewModel.Factory),
) {
    val recents by viewModel.recents.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recent_title)) },
                actions = {
                    if (recents.isNotEmpty()) {
                        TextButton(onClick = viewModel::clearAll) {
                            Text(stringResource(R.string.action_clear))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (recents.isEmpty()) {
            LibraryMessage(
                title = stringResource(R.string.recent_empty_title),
                body = stringResource(R.string.recent_empty_body),
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            LazyColumn(modifier = Modifier.padding(innerPadding)) {
                items(recents, key = { it.uriString }) { recent ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenDocument(recent.uriString, recent.displayName)
                            }
                            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                    ) {
                        Text(
                            text = pdfTitle(recent.displayName),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(
                                R.string.recent_subtitle,
                                stringResource(
                                    R.string.recent_page,
                                    recent.lastPageIndex + 1,
                                ),
                                formatModified(recent.viewedAt),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}
