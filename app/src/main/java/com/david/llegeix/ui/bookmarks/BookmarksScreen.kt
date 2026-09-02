package com.david.llegeix.ui.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.david.llegeix.data.db.dao.DocumentTag
import com.david.llegeix.data.db.entity.WordBookmarkEntity
import com.david.llegeix.ui.common.HighlightColors
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import com.david.llegeix.ui.common.CoverAspectRatio
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.PdfCover
import com.david.llegeix.ui.common.TagStrip
import com.david.llegeix.ui.library.ListCoverWidth
import com.david.llegeix.ui.common.Space
import com.david.llegeix.util.formatModified
import kotlinx.coroutines.launch
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
    val visibleWords by viewModel.visibleWords.collectAsStateWithLifecycle()
    val wordQuery by viewModel.wordQuery.collectAsStateWithLifecycle()
    val wordsAlphabetical by viewModel.wordsAlphabetical.collectAsStateWithLifecycle()
    val tagsByDocument by viewModel.tagsByDocument.collectAsStateWithLifecycle()
    // The pager owns the position; the tab row follows it, so a swipe and a tap
    // cannot disagree about which tab is showing.
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { BookmarkTab.entries.size },
    )
    val scope = rememberCoroutineScope()
    val selectedTab = pagerState.currentPage

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
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
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

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // Pages start at the top. The default is CenterVertically,
                // which quietly pushed the words tab's search field a third of
                // the way down the screen because its content is shorter than
                // the page.
                verticalAlignment = Alignment.Top,
            ) { page ->
                when (BookmarkTab.entries[page]) {
                BookmarkTab.PDFS -> if (documents.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.bookmarks_pdfs_empty_title),
                        body = stringResource(R.string.bookmarks_pdfs_empty_body),
                        icon = painterResource(R.drawable.ic_bookmark),
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = Space.lg)) {
                        items(documents, key = { it.uriString }) { document ->
                            BookmarkRow(
                                title = pdfTitle(document.displayName),
                                subtitle = null,
                                documentUri = document.uriString,
                                tags = tagsByDocument[document.uriString].orEmpty(),
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
                    LazyColumn(contentPadding = PaddingValues(bottom = Space.lg)) {
                        items(pages, key = { it.id }) { bookmark ->
                            BookmarkRow(
                                documentUri = bookmark.documentUri,
                                tags = tagsByDocument[bookmark.documentUri].orEmpty(),
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
                    Column {
                        WordControls(
                            query = wordQuery,
                            alphabetical = wordsAlphabetical,
                            onQueryChange = viewModel::onWordQueryChange,
                            onToggleSort = viewModel::onToggleWordSort,
                        )
                        if (visibleWords.isEmpty()) {
                            EmptyState(
                                title = stringResource(R.string.library_no_matches_title),
                                body = stringResource(
                                    R.string.words_none_match,
                                    wordQuery,
                                ),
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(bottom = Space.lg),
                                // Saved words are dense blocks of their own —
                                // word, pronunciation, translation, the quoted
                                // line and the source. Without a real gap two
                                // entries read as one.
                                verticalArrangement = Arrangement.spacedBy(Space.lg),
                            ) {
                                items(visibleWords, key = { it.id }) { word ->
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
        }
    }
}

/** The search field and the ordering toggle above the saved words. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordControls(
    query: String,
    alphabetical: Boolean,
    onQueryChange: (String) -> Unit,
    onToggleSort: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen)
            .padding(top = Space.sm, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                // Single line, always. The field shares its row with the sort
                // chip, so a placeholder long enough to wrap turns a 42dp field
                // into a 140dp one.
                Text(
                    text = stringResource(R.string.words_search),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(50),
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(
                                R.string.library_clear_search,
                            ),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 42.dp),
        )
        // A toggle rather than a menu: there are two orders, and a menu to pick
        // between two things is a menu too many.
        FilterChip(
            selected = alphabetical,
            onClick = onToggleSort,
            label = {
                Text(
                    text = stringResource(
                        if (alphabetical) {
                            R.string.words_sort_alphabetical
                        } else {
                            R.string.words_sort_recent
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            },
            modifier = Modifier.padding(start = Space.sm),
        )
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
            word.documentUri?.let { uri ->
                PdfCover(
                    uriString = uri,
                    width = ListCoverWidth,
                    modifier = Modifier
                        .width(ListCoverWidth)
                        .aspectRatio(CoverAspectRatio)
                        .padding(end = Space.lg),
                )
            }
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
    documentUri: String? = null,
    tags: List<DocumentTag> = emptyList(),
    swatchColor: Int? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = Space.screen, top = Space.row, bottom = Space.row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The same cover, at the same size, as the library and Recently viewed:
        // a bookmarked PDF should look like the PDF it is.
        if (documentUri != null) {
            PdfCover(
                uriString = documentUri,
                width = ListCoverWidth,
                modifier = Modifier
                    .width(ListCoverWidth)
                    .aspectRatio(CoverAspectRatio),
            )
        }
        if (swatchColor != null) {
            Box(
                modifier = Modifier
                    .padding(start = if (documentUri != null) Space.md else 0.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(HighlightColors.compose(swatchColor)),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = when {
                        documentUri != null || swatchColor != null -> Space.lg
                        else -> 0.dp
                    },
                ),
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
        // Set apart from the dismiss button on purpose: a coloured chip next to
        // an X reads as belonging to it, and one is a label while the other
        // destroys something.
        TagStrip(tags = tags, modifier = Modifier.padding(start = Space.sm))

        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.document_remove_bookmark),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
