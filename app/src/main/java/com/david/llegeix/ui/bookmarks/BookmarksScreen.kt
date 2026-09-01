package com.david.llegeix.ui.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.annotation.StringRes
import com.david.llegeix.R
import com.david.llegeix.ui.common.HighlightColors
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.res.painterResource
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.Space
import com.david.llegeix.util.formatModified
import com.david.llegeix.util.pdfTitle

private enum class BookmarkTab(@param:StringRes val labelRes: Int) {
    PDFS(R.string.bookmarks_tab_pdfs),
    PAGES(R.string.bookmarks_tab_pages),
    FOLDERS(R.string.bookmarks_tab_folders),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onOpenDocument: (uriString: String, title: String, page: Int?) -> Unit,
    onOpenFolder: (folderId: Long, name: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookmarksViewModel = viewModel(factory = BookmarksViewModel.Factory),
) {
    val documents by viewModel.bookmarkedDocuments.collectAsStateWithLifecycle()
    val pages by viewModel.pageBookmarks.collectAsStateWithLifecycle()
    val folders by viewModel.bookmarkedFolders.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bookmarks_title)) },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                BookmarkTab.entries.forEachIndexed { index, tab ->
                    val count = when (tab) {
                        BookmarkTab.PDFS -> documents.size
                        BookmarkTab.PAGES -> pages.size
                        BookmarkTab.FOLDERS -> folders.size
                    }
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            val name = stringResource(tab.labelRes)
                            Text(
                                if (count > 0) {
                                    stringResource(
                                        R.string.bookmarks_tab_with_count,
                                        name,
                                        count,
                                    )
                                } else {
                                    name
                                },
                            )
                        },
                    )
                }
            }

            when (BookmarkTab.entries[selectedTab]) {
                BookmarkTab.PDFS -> if (documents.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.bookmarks_pdfs_empty_title),
                        body = stringResource(R.string.bookmarks_pdfs_empty_body),
                        icon = painterResource(R.drawable.ic_bookmark),
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = Space.xxl)) {
                        items(documents, key = { it.uriString }) { document ->
                            BookmarkRow(
                                title = pdfTitle(document.displayName),
                                subtitle = null,
                                onClick = {
                                    onOpenDocument(document.uriString, document.displayName, null)
                                },
                                onRemove = { viewModel.removeDocumentBookmark(document) },
                            )
                        }
                    }
                }

                BookmarkTab.PAGES -> if (pages.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.bookmarks_pages_empty_title),
                        body = stringResource(R.string.bookmarks_pages_empty_body),
                        icon = painterResource(R.drawable.ic_bookmark),
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = Space.xxl)) {
                        items(pages, key = { it.id }) { bookmark ->
                            BookmarkRow(
                                title = pdfTitle(bookmark.displayName),
                                subtitle = stringResource(
                                    R.string.bookmarks_page_detail,
                                    bookmark.label?.let { label ->
                                        stringResource(
                                            R.string.bookmarks_page_detail,
                                            stringResource(
                                                R.string.recent_page,
                                                bookmark.pageIndex + 1,
                                            ),
                                            label,
                                        )
                                    } ?: stringResource(
                                        R.string.recent_page,
                                        bookmark.pageIndex + 1,
                                    ),
                                    formatModified(bookmark.createdAt),
                                ),
                                swatchColor = bookmark.highlightColor,
                                onClick = {
                                    onOpenDocument(
                                        bookmark.documentUri,
                                        bookmark.displayName,
                                        bookmark.pageIndex,
                                    )
                                },
                                onRemove = { viewModel.removePageBookmark(bookmark.id) },
                            )
                        }
                    }
                }

                BookmarkTab.FOLDERS -> if (folders.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.bookmarks_folders_empty_title),
                        body = stringResource(R.string.bookmarks_folders_empty_body),
                        icon = painterResource(R.drawable.ic_folder),
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = Space.xxl)) {
                        items(folders, key = { it.id }) { folder ->
                            BookmarkRow(
                                title = folder.name,
                                subtitle = if (folder.documentCount == 0) {
                                    stringResource(R.string.folders_empty_count)
                                } else {
                                    pluralStringResource(
                                        R.plurals.folders_pdf_count,
                                        folder.documentCount,
                                        folder.documentCount,
                                    )
                                },
                                swatchColor = folder.colorArgb,
                                onClick = { onOpenFolder(folder.id, folder.name) },
                                onRemove = { viewModel.removeFolderBookmark(folder.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    swatchColor: Int? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = Space.screen, top = Space.row, bottom = Space.row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (swatchColor != null) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(HighlightColors.compose(swatchColor)),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (swatchColor != null) Space.md else 0.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.document_remove_bookmark),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
