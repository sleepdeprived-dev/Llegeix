package com.david.llegeix.ui.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.david.llegeix.ui.common.SearchField
import com.david.llegeix.ui.common.RecentSearches
import com.david.llegeix.data.practice.Leitner
import com.david.llegeix.ui.common.Space
import com.david.llegeix.util.formatModified
import com.david.llegeix.util.pdfTitle

/**
 * The reader's page bookmarks, as one pane of the Saved tab.
 *
 * The tab this came from had a third pane above these two, listing the starred
 * PDFs. It was the same query as the automatic "Bookmarked" collection, drawn
 * twice: [com.david.llegeix.ui.bookmarks.BookmarkedCollectionScreen] and that
 * pane both read `bookmarkedDocuments` off this very ViewModel, so a reader
 * could reach one list by two routes and had no way of telling they were the
 * same list. Merging the two tabs was the moment to keep one of them, and the
 * collection is the one that survives, because that is where somebody looking
 * for a shelf of PDFs goes.
 */
@Composable
fun PagesPane(
    onOpenDocument: (uriString: String, title: String, page: Int?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookmarksViewModel = viewModel(factory = BookmarksViewModel.Factory),
) {
    val pages by viewModel.pageBookmarks.collectAsStateWithLifecycle()
    val tagsByDocument by viewModel.tagsByDocument.collectAsStateWithLifecycle()

    if (pages.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.bookmarks_pages_empty_title),
            body = stringResource(R.string.bookmarks_pages_empty_body),
            icon = painterResource(R.drawable.ic_bookmark),
            modifier = modifier,
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Space.lg),
        ) {
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
                                stringResource(R.string.recent_page, bookmark.pageIndex + 1),
                                label,
                            )
                        } ?: stringResource(R.string.recent_page, bookmark.pageIndex + 1),
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
}

/** The reader's saved vocabulary and the way into practising it. */
@Composable
fun WordsPane(
    onOpenDocument: (uriString: String, title: String, page: Int?) -> Unit,
    onPractise: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookmarksViewModel = viewModel(factory = BookmarksViewModel.Factory),
) {
    val savedWords by viewModel.savedWords.collectAsStateWithLifecycle()
    val visibleWords by viewModel.visibleWords.collectAsStateWithLifecycle()
    val wordQuery by viewModel.wordQuery.collectAsStateWithLifecycle()
    val wordsAlphabetical by viewModel.wordsAlphabetical.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val dueCount by viewModel.dueCount.collectAsStateWithLifecycle()
    val savedPerDay by viewModel.savedPerDay.collectAsStateWithLifecycle()

    if (savedWords.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.bookmarks_words_empty_title),
            body = stringResource(R.string.bookmarks_words_empty_body),
            icon = painterResource(R.drawable.ic_bookmark),
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        PracticeHeader(
            savedCount = savedWords.size,
            dueCount = dueCount,
            savedPerDay = savedPerDay,
            onPractise = onPractise,
        )
        WordControls(
            query = wordQuery,
            alphabetical = wordsAlphabetical,
            recentSearches = recentSearches,
            onQueryChange = viewModel::onWordQueryChange,
            onToggleSort = viewModel::onToggleWordSort,
            onForgetSearches = viewModel::onForgetSearches,
        )
        if (visibleWords.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.library_no_matches_title),
                body = stringResource(R.string.words_none_match, wordQuery),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = Space.lg),
                // Saved words are dense blocks of their own — word,
                // pronunciation, translation, the quoted line and the source.
                // Without a real gap two entries read as one.
                verticalArrangement = Arrangement.spacedBy(Space.lg),
            ) {
                items(visibleWords, key = { it.id }) { word ->
                    SavedWordRow(
                        word = word,
                        onOpen = {
                            val uri = word.documentUri
                            if (uri != null) {
                                onOpenDocument(uri, word.displayName.orEmpty(), word.pageIndex)
                            }
                        },
                        onRemove = { viewModel.removeWord(word.id) },
                    )
                }
            }
        }
    }
}

/**
 * What the saved words add up to, and the way into practising them.
 *
 * The list underneath is a record; this is the part that asks something of the
 * reader. Saving a word and never meeting it again is the one reliable way not
 * to learn it, and until this existed that was the only thing the app offered
 * to do with a vocabulary list.
 *
 * The strip of bars is fourteen days of saving, drawn small. It is not
 * analytics — there is nothing to drill into and no number written on it — it
 * is there so a list that grows slowly still visibly grows.
 */
@Composable
private fun PracticeHeader(
    savedCount: Int,
    dueCount: Int,
    savedPerDay: List<Int>,
    onPractise: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen)
            .padding(top = Space.lg)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(Space.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (dueCount > 0) {
                        pluralStringResource(R.plurals.practice_due, dueCount, dueCount)
                    } else {
                        stringResource(R.string.practice_nothing_due)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.practice_saved_total,
                        savedCount,
                        savedCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Button(onClick = onPractise, enabled = savedCount > 0) {
                Text(stringResource(R.string.practice_start))
            }
        }
        if (savedPerDay.any { it > 0 }) {
            ActivityStrip(
                counts = savedPerDay,
                modifier = Modifier.padding(top = Space.lg),
            )
        }
    }
}

/**
 * Fourteen days of saving, as fourteen bars.
 *
 * Scaled against the busiest day rather than against a fixed ceiling, so a
 * quiet fortnight is still legible instead of being fourteen invisible stubs.
 * Every day gets at least a mark, because a bar of no height reads as missing
 * data rather than as a day with nothing in it.
 */
@Composable
private fun ActivityStrip(counts: List<Int>, modifier: Modifier = Modifier) {
    val peak = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .fillMaxWidth()
            .height(ActivityStripHeight),
    ) {
        counts.forEach { count ->
            val share = count.toFloat() / peak
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(if (count == 0) 0.12f else (0.25f + share * 0.75f))
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (count == 0) {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        },
                    ),
            )
        }
    }
}

private val ActivityStripHeight = 28.dp

/**
 * How well a saved word is known, as a short bar.
 *
 * Drawn only once the word has actually been answered about. Before that every
 * word would wear an identical empty bar, which says nothing and puts a mark
 * against every row in the list for the privilege.
 */
@Composable
private fun BoxScore(box: Int, reviewed: Boolean, modifier: Modifier = Modifier) {
    if (!reviewed) return
    val filled = Leitner.progressOf(box)
    Box(
        modifier = modifier
            .width(BoxScoreWidth)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(filled.coerceAtLeast(0.08f))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
        )
    }
}

private val BoxScoreWidth = 32.dp

/**
 * Search and ordering for the saved words.
 *
 * Two lines rather than one. Sharing a row, the field and the chip squeezed
 * each other until the placeholder read "Cerca para…", and two controls of
 * different shapes and weights sat side by side competing for the same
 * attention. Given the width it asks for, the field matches the one in the
 * library exactly, so search looks and behaves the same everywhere in the app.
 * Ordering is the smaller question, so it sits underneath, quieter, and out of
 * the way of the thing most people came here to do.
 */
@Composable
private fun WordControls(
    query: String,
    alphabetical: Boolean,
    recentSearches: List<String>,
    onQueryChange: (String) -> Unit,
    onToggleSort: () -> Unit,
    onForgetSearches: () -> Unit,
) {
    // Whether somebody is searching, as opposed to the field merely being
    // empty — which it is whenever the screen is just being read.
    var isSearchFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.lg, bottom = Space.md),
    ) {
        SearchField(
            query = query,
            placeholder = stringResource(R.string.words_search),
            onQueryChange = onQueryChange,
            onFocusChanged = { isSearchFocused = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.screen),
        )

        // Directly under the field it belongs to, and above the sort control
        // rather than below it. A list of suggestions separated from its field
        // by an unrelated button is a list that has to be worked out rather
        // than read.
        if (query.isBlank() && isSearchFocused) {
            RecentSearches(
                history = recentSearches,
                onPick = onQueryChange,
                onClear = onForgetSearches,
                modifier = Modifier.padding(top = Space.sm),
            )
        }

        TextButton(
            onClick = onToggleSort,
            contentPadding = PaddingValues(horizontal = Space.sm, vertical = 0.dp),
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = Space.sm, end = Space.screen),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_sort),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(
                    if (alphabetical) {
                        R.string.words_sort_alphabetical
                    } else {
                        R.string.words_sort_recent
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = Space.sm),
            )
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
            word.documentUri?.let { uri ->
                // The gap is the row's rather than the cover's: applied inside
                // the chain the padding came off the width the aspect ratio was
                // then taken of, and the cover came out a third too narrow.
                PdfCover(
                    uriString = uri,
                    width = ListCoverWidth,
                    modifier = Modifier
                        .width(ListCoverWidth)
                        .aspectRatio(CoverAspectRatio),
                )
                Spacer(modifier = Modifier.width(Space.lg))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = word.word,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // How settled the word is, as five small marks. It is the
                    // one thing the list could say that the reader cannot work
                    // out by looking: which of these they actually know, as
                    // against which they merely saved.
                    BoxScore(
                        box = word.box,
                        reviewed = word.reviewCount > 0,
                        modifier = Modifier.padding(start = Space.sm),
                    )
                }
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
                // What it meant in the line it was saved from, when that was
                // not what it means alone. Kept next to the plain translation
                // and labelled, exactly as the reader's sheet showed it — the
                // note should not quietly become more certain than the moment
                // it was made in.
                if (!word.senseTranslation.isNullOrBlank()) {
                    Text(
                        text = word.senseSource?.takeIf { it.isNotBlank() }
                            ?.let { stringResource(R.string.words_sense_from, it) }
                            ?: stringResource(R.string.lookup_here),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = Space.sm),
                    )
                    Text(
                        text = word.senseTranslation,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
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
            Column(
                modifier = Modifier
                    .padding(top = Space.sm, end = Space.lg)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = Space.md, vertical = Space.sm),
            ) {
                Text(
                    text = word.context,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                // The quoted line is only worth quoting if it can be read, and
                // the whole point of saving a word in a language you are
                // learning is the sentence it was doing a job in.
                if (!word.contextTranslation.isNullOrBlank()) {
                    Text(
                        text = word.contextTranslation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                }
            }
        }

        // A word starred in the Dictionary tab has no page behind it, so it
        // says where it came from instead of quoting a page and a line it was
        // never on.
        Text(
            text = if (word.documentUri == null) {
                stringResource(R.string.words_from_dictionary)
            } else {
                stringResource(
                    R.string.words_source,
                    pdfTitle(word.displayName.orEmpty()),
                    word.pageIndex + 1,
                    word.lineNumber,
                )
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Space.sm),
        )
    }
}

@Composable
internal fun BookmarkRow(
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
