package com.david.llegeix.ui.bookmarks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.data.db.dao.DocumentTag
import com.david.llegeix.data.db.entity.DocumentEntity
import com.david.llegeix.data.db.entity.WordBookmarkEntity
import com.david.llegeix.resources.*
import com.david.llegeix.ui.common.MenuIcon
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import com.david.llegeix.ui.common.CoverAspectRatio
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.PdfCover
import com.david.llegeix.ui.common.TagPickerDialog
import com.david.llegeix.ui.common.TagStrip
import com.david.llegeix.ui.library.ListCoverWidth
import com.david.llegeix.ui.common.SearchField
import com.david.llegeix.ui.common.RecentSearches
import com.david.llegeix.data.practice.Leitner
import com.david.llegeix.ui.common.Space
import com.david.llegeix.util.formatModified
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

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
    val names by viewModel.names.collectAsStateWithLifecycle()

    if (pages.isEmpty()) {
        EmptyState(
            title = stringResource(Res.string.bookmarks_pages_empty_title),
            body = stringResource(Res.string.bookmarks_pages_empty_body),
            icon = painterResource(Res.drawable.ic_bookmark),
            modifier = modifier,
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Space.lg),
        ) {
            items(pages, key = { it.id }) { bookmark ->
                // No colour on these rows, neither the highlighter's swatch nor
                // the document's tags.
                //
                // Both were saying something true and neither was answering a
                // question anybody asks here. The swatch was the colour the
                // page was marked in, which defaults to yellow, so a list of
                // marked pages arrived as a column of identical yellow dots
                // that looked like a status nobody had set. The tags belong to
                // the *document*, not to the page, so the same three chips
                // repeated down every row of a book read twice — and tags are
                // how collections are sorted through, which is where they have
                // work to do. A marked page is a title, a page number and a
                // date; that is the whole of it.
                BookmarkRow(
                    documentUri = bookmark.documentUri,
                    title = names.titleFor(bookmark.documentUri, bookmark.displayName),
                    subtitle = stringResource(
                        Res.string.bookmarks_page_detail,
                        bookmark.label?.let { label ->
                            stringResource(
                                Res.string.bookmarks_page_detail,
                                stringResource(Res.string.recent_page, bookmark.pageIndex + 1),
                                label,
                            )
                        } ?: stringResource(Res.string.recent_page, bookmark.pageIndex + 1),
                        formatModified(bookmark.createdAt),
                    ),
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
    onOpenLearned: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookmarksViewModel = viewModel(factory = BookmarksViewModel.Factory),
) {
    val learnedViewModel: LearnedWordsViewModel = viewModel(factory = LearnedWordsViewModel.Factory)
    val learnedDays by learnedViewModel.days.collectAsStateWithLifecycle()
    val addedToday = learnedDays.orEmpty().firstOrNull { it.date == java.time.LocalDate.now() }?.words?.size ?: 0
    val savedWords by viewModel.savedWords.collectAsStateWithLifecycle()
    val visibleWords by viewModel.visibleWords.collectAsStateWithLifecycle()
    val wordQuery by viewModel.wordQuery.collectAsStateWithLifecycle()
    val wordsAlphabetical by viewModel.wordsAlphabetical.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val names by viewModel.names.collectAsStateWithLifecycle()
    val dueCount by viewModel.dueCount.collectAsStateWithLifecycle()
    val savedPerDay by viewModel.savedPerDay.collectAsStateWithLifecycle()
    val learnedToday by viewModel.learnedToday.collectAsStateWithLifecycle()

    if (savedWords.isEmpty()) {
        Column(modifier = modifier.fillMaxSize()) {
            LearnedFolderRow(todayCount = addedToday, onOpen = onOpenLearned)
            EmptyState(
                title = stringResource(Res.string.bookmarks_words_empty_title),
                body = stringResource(Res.string.bookmarks_words_empty_body),
                icon = painterResource(Res.drawable.ic_bookmark),
            )
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        LearnedFolderRow(todayCount = addedToday, onOpen = onOpenLearned)
        PracticeHeader(
            savedCount = savedWords.size,
            dueCount = dueCount,
            savedPerDay = savedPerDay,
            onPractise = onPractise,
        )
        TodayCard(words = learnedToday)
        WordControls(
            query = wordQuery,
            alphabetical = wordsAlphabetical,
            recentSearches = recentSearches,
            onQueryChange = viewModel::onWordQueryChange,
            onToggleSort = viewModel::onToggleWordSort,
            onForgetSearches = viewModel::onForgetSearches,
            onForgetSearch = viewModel::onForgetSearch,
        )
        if (visibleWords.isEmpty()) {
            EmptyState(
                title = stringResource(Res.string.library_no_matches_title),
                body = stringResource(Res.string.words_none_match, wordQuery),
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
                        source = word.documentUri?.let {
                            names.titleFor(it, word.displayName.orEmpty())
                        },
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
 * The way into "Paraules que he après avui", as a folder at the top of the
 * saved words: the words the reader adds by hand, day by day, apart from the
 * ones saved while reading.
 */
@Composable
private fun LearnedFolderRow(todayCount: Int, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen)
            .padding(top = Space.lg)
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.secondaryContainer)
            .clickable(onClick = onOpen)
            .padding(Space.lg),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.secondary),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_folder),
                contentDescription = null,
                tint = scheme.onSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.lg),
        ) {
            Text(
                text = stringResource(Res.string.learned_title),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSecondaryContainer,
            )
            Text(
                text = if (todayCount > 0) {
                    pluralStringResource(Res.plurals.learned_folder_subtitle, todayCount, todayCount)
                } else {
                    stringResource(Res.string.learned_folder_subtitle_none)
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSecondaryContainer.copy(alpha = 0.8f),
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = scheme.onSecondaryContainer,
        )
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
                        pluralStringResource(Res.plurals.practice_due, dueCount, dueCount)
                    } else {
                        stringResource(Res.string.practice_nothing_due)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = pluralStringResource(
                        Res.plurals.practice_saved_total,
                        savedCount,
                        savedCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Button(onClick = onPractise, enabled = savedCount > 0) {
                Text(stringResource(Res.string.practice_start))
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
 * What today gave you, as the words themselves.
 *
 * The card above it is a scoreboard — how many are saved, how many are due — and
 * a scoreboard is not a thing anybody learns from. This is the same day written
 * out in the only units that mean anything in a language: *enrenou*, *capgirar*,
 * *a contracor*. Saved today or practised today, because those are the two ways
 * a word gets worked on here, and a card that emptied itself on every day spent
 * revising would be a card that only ever congratulated new reading.
 *
 * Every pill is a small test rather than an answer. It shows the Catalan, and
 * pressing it turns over the meaning it was saved with — which is the whole
 * transaction of learning a word, offered in the one place where the words of
 * the day are already gathered, at the cost of a tap and no navigation at all.
 * Pressing it again puts it back, so the card can be gone through twice.
 *
 * Nothing is drawn on a day with nothing on it. An empty card headed "today"
 * is a reproach, and this list is a record of what happened rather than a target
 * that was missed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayCard(words: List<WordBookmarkEntity>, modifier: Modifier = Modifier) {
    if (words.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen)
            .padding(top = Space.md)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(Space.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.words_today_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            // The count is worth a glance of its own on a good day, and it is
            // the one number here that goes up.
            Text(
                text = words.size.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = stringResource(Res.string.words_today_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
            modifier = Modifier.padding(top = Space.md),
        ) {
            words.take(TodayWordLimit).forEach { word -> TodayWord(word) }
            // A very good day is capped rather than allowed to push the list of
            // saved words off the bottom of the screen. The header carries the
            // true count and the list below holds every one of them, newest
            // first, so nothing is hidden by this — only deferred by a scroll.
            val rest = words.size - TodayWordLimit
            if (rest > 0) {
                Text(
                    text = stringResource(Res.string.words_today_more, rest),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.sm),
                )
            }
        }
    }
}

/** As many words as fit in a card without it becoming the screen. */
private const val TodayWordLimit = 12

/**
 * One word of the day, with its meaning on the other side.
 *
 * Turned over in place rather than expanded into a row, so going through the
 * card is reading along a line rather than opening and closing entries in a
 * list. The pill takes the tinted background while it is showing its answer,
 * which is what lets the reader see at a glance how much of the day they have
 * already been through. A word saved with no translation — from a page the
 * model could not reach — has nothing to turn over, and says so by not
 * answering the press at all.
 */
@Composable
private fun TodayWord(word: WordBookmarkEntity) {
    val meaning = word.senseTranslation ?: word.translation
    var shown by remember(word.id) { mutableStateOf(false) }
    val revealed = shown && meaning != null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (revealed) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
            )
            .then(
                if (meaning == null) {
                    Modifier
                } else {
                    Modifier.clickable { shown = !shown }
                },
            )
            .padding(horizontal = Space.md, vertical = Space.sm),
    ) {
        Text(
            text = if (revealed) meaning.orEmpty() else word.word,
            style = MaterialTheme.typography.bodyMedium,
            color = if (revealed) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
    onForgetSearch: (String) -> Unit,
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
            placeholder = stringResource(Res.string.words_search),
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
                onRemove = onForgetSearch,
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
                painter = painterResource(Res.drawable.ic_sort),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(
                    if (alphabetical) {
                        Res.string.words_sort_alphabetical
                    } else {
                        Res.string.words_sort_recent
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedWordRow(
    word: WordBookmarkEntity,
    /** What the document is called now, or null for a word from the dictionary. */
    source: String?,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // A saved word is a tall block of text and the cross is at the top
            // right of it, which is a long way from wherever the eye happens to
            // be. Holding anywhere on the block reaches the same action.
            .combinedClickable(
                onClick = { if (word.documentUri != null) onOpen() },
                onLongClick = { menuOpen = true },
            )
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
                        fontFamily = com.david.llegeix.ui.theme.IpaFont,
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
                            ?.let { stringResource(Res.string.words_sense_from, it) }
                            ?: stringResource(Res.string.lookup_here),
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
            Box {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.words_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    // Only where there is a page to go back to. A word starred
                    // in the Dictionary was never on a page, and an item that
                    // silently does nothing is worse than one that is absent.
                    if (word.documentUri != null) {
                        DropdownMenuItem(
                            leadingIcon = { MenuIcon(painterResource(Res.drawable.ic_library)) },
                            text = { Text(stringResource(Res.string.words_open_source)) },
                            onClick = { menuOpen = false; onOpen() },
                        )
                        HorizontalDivider()
                    }
                    DropdownMenuItem(
                        leadingIcon = { MenuIcon(Icons.Default.Close) },
                        text = { Text(stringResource(Res.string.words_remove)) },
                        onClick = { menuOpen = false; onRemove() },
                    )
                }
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
                stringResource(Res.string.words_from_dictionary)
            } else {
                stringResource(
                    Res.string.words_source,
                    source.orEmpty(),
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

/**
 * One set-aside PDF or page, in any of the collections that list them.
 *
 * Holding it opens the menu, where a collection's rows carry one — which is
 * every list of *documents*, since a document is the thing tags and names
 * belong to. It is the same gesture and very nearly the same menu as the
 * library's rows, on purpose: a PDF met in a collection and the same PDF met in
 * the library should not answer to different things.
 *
 * @param onEditTags null on a list where tags make no sense, which is what
 *   leaves the row with no menu at all rather than an empty one.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookmarkRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    documentUri: String? = null,
    tags: List<DocumentTag> = emptyList(),
    onEditTags: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    /** What the menu's "remove" says, since it differs by collection. */
    removeLabel: String? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val hasMenu = onEditTags != null || onRename != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (hasMenu) {
                    { menuOpen = true }
                } else {
                    null
                },
            )
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
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (documentUri != null) Space.lg else 0.dp),
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

        Box {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = removeLabel
                        ?: stringResource(Res.string.document_remove_bookmark),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                onEditTags?.let { edit ->
                    DropdownMenuItem(
                        leadingIcon = { MenuIcon(painterResource(Res.drawable.ic_tag)) },
                        text = { Text(stringResource(Res.string.tags_open)) },
                        onClick = { menuOpen = false; edit() },
                    )
                }
                onRename?.let { rename ->
                    DropdownMenuItem(
                        leadingIcon = { MenuIcon(Icons.Default.Edit) },
                        text = { Text(stringResource(Res.string.document_rename)) },
                        onClick = { menuOpen = false; rename() },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    leadingIcon = { MenuIcon(Icons.Default.Close) },
                    text = {
                        Text(
                            removeLabel ?: stringResource(Res.string.document_remove_bookmark),
                        )
                    },
                    onClick = { menuOpen = false; onRemove() },
                )
            }
        }
    }
}

/**
 * The dialogs a collection's hold-to-open menu raises.
 *
 * Lifted out of the two screens that need them because they are the same two
 * dialogs, working on the same rows, through the same ViewModel: a PDF starred
 * and a PDF set aside to read later are the same document, and tagging one from
 * either list has to mean the same thing.
 */
@Composable
internal fun SavedDocumentDialogs(
    tagsFor: DocumentEntity?,
    renaming: DocumentEntity?,
    onDismissTags: () -> Unit,
    onDismissRename: () -> Unit,
    viewModel: BookmarksViewModel,
) {
    val allTags by viewModel.tags.collectAsStateWithLifecycle()
    val tagsByDocument by viewModel.tagsByDocument.collectAsStateWithLifecycle()
    val names by viewModel.names.collectAsStateWithLifecycle()

    tagsFor?.let { document ->
        TagPickerDialog(
            documentTitle = names.titleFor(document.uriString, document.displayName),
            allTags = allTags,
            selectedIds = tagsByDocument[document.uriString].orEmpty().map { it.id }.toSet(),
            onToggle = { viewModel.onToggleTag(document.uriString, document.displayName, it) },
            onCreate = { name, colour ->
                viewModel.onCreateTag(document.uriString, document.displayName, name, colour)
            },
            onRecolour = viewModel::onRecolourTag,
            onRename = viewModel::onRenameTag,
            onDelete = viewModel::onDeleteTag,
            onDismiss = onDismissTags,
        )
    }

    renaming?.let { document ->
        SavedNameDialog(
            current = names.titleFor(document.uriString, document.displayName),
            hasCustomName = names.isRenamed(document.uriString),
            onDismiss = onDismissRename,
            onConfirm = { name ->
                viewModel.onRenameDocument(document.uriString, document.displayName, name)
                onDismissRename()
            },
        )
    }
}

/**
 * Rename a PDF from a collection, in the app only.
 *
 * The same dialog the library raises, and it says the same thing under the
 * field: this is what Llegeix calls the document, not what the file is called.
 * The reader can reach it from either list and should not have to work out
 * whether the two do different things.
 */
@Composable
private fun SavedNameDialog(
    current: String,
    hasCustomName: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.document_rename_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.document_rename_label)) },
                    singleLine = true,
                )
                Text(
                    text = stringResource(Res.string.document_rename_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.md),
                )
                if (hasCustomName) {
                    TextButton(
                        onClick = { onConfirm("") },
                        modifier = Modifier.padding(top = Space.sm),
                    ) { Text(stringResource(Res.string.document_rename_reset)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(Res.string.action_rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
