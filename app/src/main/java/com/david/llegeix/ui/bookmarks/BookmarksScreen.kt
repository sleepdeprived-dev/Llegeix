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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.david.llegeix.data.db.entity.WordBookmarkEntity
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
    WORDS(R.string.bookmarks_tab_words),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onOpenDocument: (uriString: String, title: String, page: Int?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookmarksViewModel = viewModel(factory = BookmarksViewModel.Factory),
) {
    val documents by viewModel.bookmarkedDocuments.collectAsStateWithLifecycle()
    val pages by viewModel.pageBookmarks.collectAsStateWithLifecycle()
    val savedWords by viewModel.savedWords.collectAsStateWithLifecycle()
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
                        BookmarkTab.WORDS -> savedWords.size
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

                BookmarkTab.WORDS -> if (savedWords.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.bookmarks_words_empty_title),
                        body = stringResource(R.string.bookmarks_words_empty_body),
                        icon = painterResource(R.drawable.ic_bookmark),
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = Space.xxl)) {
                        items(savedWords, key = { it.id }) { word ->
                            SavedWordRow(
                                word = word,
                                onOpen = {
                                    val uri = word.documentUri
                                    if (uri != null) {
                                        onOpenDocument(
                                            uri,
                                            word.displayName.orEmpty(),
                                            word.pageIndex,
                                        )
                                    }
                                },
                                onRemove = { viewModel.removeWord(word.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One saved word, with everything needed to remember why it was saved.
 *
 * Catalan and English sit on the same line because they are the same fact seen
 * twice; the pronunciation follows because it belongs to the Catalan. The line
 * it came from is quoted underneath, and the document, page and line are the
 * last thing, because they answer "where was this?" rather than "what does it
 * mean?".
 */
@Composable
private fun SavedWordRow(
    word: WordBookmarkEntity,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = word.documentUri != null, onClick = onOpen)
            .padding(
                start = Space.screen,
                end = Space.sm,
                top = Space.lg,
                bottom = Space.lg,
            ),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = word.word,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!word.ipa.isNullOrBlank()) {
                    Text(
                        text = "[${word.ipa}]",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                if (!word.translation.isNullOrBlank()) {
                    Text(
                        text = word.translation,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.words_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!word.context.isNullOrBlank()) {
            Text(
                text = word.context,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = Space.sm, end = Space.lg)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = Space.md, vertical = Space.sm),
            )
        }

        Text(
            text = stringResource(
                R.string.words_source,
                pdfTitle(word.displayName.orEmpty()),
                word.pageIndex + 1,
                word.lineNumber,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Space.sm),
        )
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
