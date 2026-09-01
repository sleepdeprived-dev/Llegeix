package com.david.llegeix.ui.recent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.data.settings.LibraryLayout
import com.david.llegeix.ui.common.CoverAspectRatio
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.MenuEmoji
import com.david.llegeix.ui.common.PdfCover
import com.david.llegeix.ui.library.GridCoverWidth
import com.david.llegeix.ui.library.ListCoverWidth
import com.david.llegeix.ui.common.Space
import com.david.llegeix.util.formatModified
import com.david.llegeix.util.pdfTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentScreen(
    onOpenDocument: (uriString: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecentViewModel = viewModel(factory = RecentViewModel.Factory),
) {
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recent_title)) },
                actions = {
                    // Clearing the history is destructive and rarely wanted, so
                    // it sits behind the menu rather than one stray tap away.
                    if (recents.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(
                                        R.string.action_more_options,
                                    ),
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = {
                                        MenuEmoji(
                                            if (layout == LibraryLayout.GRID) "☰" else "🔳",
                                        )
                                    },
                                    text = {
                                        Text(
                                            stringResource(
                                                if (layout == LibraryLayout.GRID) {
                                                    R.string.library_view_list
                                                } else {
                                                    R.string.library_view_grid
                                                },
                                            ),
                                        )
                                    },
                                    onClick = {
                                        viewModel.onToggleLayout()
                                        menuOpen = false
                                    },
                                )
                                DropdownMenuItem(
                                    leadingIcon = { MenuEmoji("🧹") },
                                    text = {
                                        Text(stringResource(R.string.recent_clear_history))
                                    },
                                    onClick = {
                                        viewModel.clearAll()
                                        menuOpen = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (recents.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.recent_empty_title),
                body = stringResource(R.string.recent_empty_body),
                icon = painterResource(R.drawable.ic_recent),
                modifier = Modifier.padding(innerPadding),
            )
        } else if (layout == LibraryLayout.GRID) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(
                    start = Space.md,
                    end = Space.md,
                    bottom = Space.xxl,
                ),
            ) {
                items(recents, key = { it.uriString }) { recent ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onOpenDocument(recent.uriString, recent.displayName)
                            }
                            .padding(Space.sm),
                    ) {
                        PdfCover(
                            uriString = recent.uriString,
                            width = GridCoverWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(CoverAspectRatio),
                            cornerRadius = 12.dp,
                        )
                        Text(
                            text = pdfTitle(recent.displayName),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = Space.sm),
                        )
                        Text(
                            text = stringResource(
                                R.string.recent_page,
                                recent.lastPageIndex + 1,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(bottom = Space.xxl),
            ) {
                items(recents, key = { it.uriString }) { recent ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenDocument(recent.uriString, recent.displayName)
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
                            uriString = recent.uriString,
                            width = ListCoverWidth,
                            modifier = Modifier
                                .width(ListCoverWidth)
                                .aspectRatio(CoverAspectRatio),
                        )
                        Column(modifier = Modifier.padding(start = Space.lg)) {
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
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
