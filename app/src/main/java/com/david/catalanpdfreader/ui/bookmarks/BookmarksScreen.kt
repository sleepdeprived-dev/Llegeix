package com.david.catalanpdfreader.ui.bookmarks

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.catalanpdfreader.ui.common.HighlightColors
import com.david.catalanpdfreader.ui.library.LibraryMessage
import com.david.catalanpdfreader.util.formatModified
import com.david.catalanpdfreader.util.pdfTitle

private enum class BookmarkTab(val label: String) {
    PDFS("PDFs"),
    PAGES("Pages"),
    FOLDERS("Folders"),
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
        topBar = { TopAppBar(title = { Text("Bookmarks") }) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            TabRow(selectedTabIndex = selectedTab) {
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
                            Text(if (count > 0) "${tab.label} ($count)" else tab.label)
                        },
                    )
                }
            }

            when (BookmarkTab.entries[selectedTab]) {
                BookmarkTab.PDFS -> if (documents.isEmpty()) {
                    LibraryMessage(
                        title = "No bookmarked PDFs",
                        body = "Bookmark a whole PDF from its menu in the library. " +
                            "Bookmarked PDFs also appear as a \"Bookmarked\" " +
                            "collection under Folders.",
                    )
                } else {
                    LazyColumn {
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
                    LibraryMessage(
                        title = "No bookmarked pages",
                        body = "Tap the star while reading to bookmark the page " +
                            "you are on. Those land here, separately from whole PDFs.",
                    )
                } else {
                    LazyColumn {
                        items(pages, key = { it.id }) { bookmark ->
                            BookmarkRow(
                                title = pdfTitle(bookmark.displayName),
                                subtitle = "Page ${bookmark.pageIndex + 1}" +
                                    (bookmark.label?.let { " · $it" } ?: "") +
                                    " · ${formatModified(bookmark.createdAt)}",
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
                    LibraryMessage(
                        title = "No bookmarked folders",
                        body = "Bookmark a folder from its menu under Folders.",
                    )
                } else {
                    LazyColumn {
                        items(folders, key = { it.id }) { folder ->
                            BookmarkRow(
                                title = folder.name,
                                subtitle = when (folder.documentCount) {
                                    0 -> "Empty"
                                    1 -> "1 PDF"
                                    else -> "${folder.documentCount} PDFs"
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
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
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
                .padding(start = if (swatchColor != null) 12.dp else 0.dp),
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
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove bookmark",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}
