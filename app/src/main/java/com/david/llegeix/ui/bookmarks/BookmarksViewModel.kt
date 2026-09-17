package com.david.llegeix.ui.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.data.db.dao.DocumentTag
import com.david.llegeix.data.db.dao.FolderWithCount
import com.david.llegeix.data.db.dao.PageBookmark
import com.david.llegeix.data.db.entity.DocumentEntity
import com.david.llegeix.data.db.entity.TagEntity
import com.david.llegeix.data.db.entity.WordBookmarkEntity
import com.david.llegeix.data.settings.SearchHistoryRepository
import com.david.llegeix.data.settings.SearchScope
import com.david.llegeix.data.source.DocumentNames
import com.david.llegeix.data.source.LibraryDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * The three things that can be bookmarked, kept deliberately distinct.
 *
 * A bookmarked *PDF* is a whole document the user wants to come back to; a
 * bookmarked *page* is a position inside one. Collapsing them into one list
 * would make "my bookmarked books" and "my bookmarked passages" the same
 * thing, which they are not.
 */
class BookmarksViewModel(
    private val libraryData: LibraryDataRepository,
    private val searchHistory: SearchHistoryRepository,
) : ViewModel() {

    /**
     * Words searched for lately — the same list the reader's find bar keeps.
     *
     * Shared on purpose: the word you chased across a page is the one you come
     * back to look for in your own saved list.
     */
    val recentSearches: StateFlow<List<String>> = searchHistory.history(SearchScope.WORDS)

    fun onForgetSearches() = searchHistory.forget(SearchScope.WORDS)

    /** Drop one past search, from the cross on its row. */
    fun onForgetSearch(query: String) = searchHistory.forgetOne(SearchScope.WORDS, query)

    private var rememberJob: Job? = null

    val bookmarkedDocuments: StateFlow<List<DocumentEntity>> =
        libraryData.observeBookmarkedDocuments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * PDFs set aside to read later, for the collection of that name.
     *
     * The same flag the library's leftward swipe sets, read back. It was a
     * filter chip on the library until this release; it is a shelf the reader
     * built, so it is a collection.
     */
    val readLaterDocuments: StateFlow<List<DocumentEntity>> =
        libraryData.observeReadLaterDocuments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pageBookmarks: StateFlow<List<PageBookmark>> =
        libraryData.observePageBookmarks()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Words and phrases saved while reading.
     *
     * This replaced bookmarked folders in the Bookmarks screen. A folder you
     * starred was a shortcut to a shelf; a word you saved is the thing this app
     * is for, and the two were competing for the same tab.
     */
    val savedWords: StateFlow<List<WordBookmarkEntity>> = libraryData.observeWordBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun removeWord(id: Long) = viewModelScope.launch { libraryData.removeWordBookmark(id) }

    /**
     * How many saved words are waiting to be practised.
     *
     * The clock is read once, when the screen is built. A count that ticked up
     * while the reader was looking at it would be a number changing for reasons
     * nobody can see.
     */
    val dueCount: StateFlow<Int> =
        libraryData.observeDueCount(System.currentTimeMillis())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Words saved on each of the last fortnight's days, oldest first.
     *
     * A row of bars rather than a number. What it is actually for is showing
     * that anything is happening at all: a vocabulary list is a slow thing, and
     * fourteen small marks say "you have been at this" in a way that "137
     * saved" never does.
     */
    val savedPerDay: StateFlow<List<Int>> = savedWords
        .map { words -> countPerDay(words, System.currentTimeMillis()) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            List(ACTIVITY_DAYS) { 0 },
        )

    /**
     * The words today has actually given the reader.
     *
     * Saved today, or practised today. Both belong: meeting a word in a book
     * and keeping it is the moment it enters the language you are learning, and
     * being asked about one you kept in March is the moment it stays there. A
     * list of only the new ones would go empty on every day spent revising, and
     * a list of only the practised ones would go empty on every day spent
     * reading — and those are the two halves of what this app is for.
     *
     * Newest first, so the word just saved is the one at the front.
     */
    val learnedToday: StateFlow<List<WordBookmarkEntity>> = savedWords
        .map { words -> learnedSince(words, startOfToday(System.currentTimeMillis())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _wordQuery = MutableStateFlow("")
    val wordQuery: StateFlow<String> = _wordQuery.asStateFlow()

    private val _wordsAlphabetical = MutableStateFlow(false)
    val wordsAlphabetical: StateFlow<Boolean> = _wordsAlphabetical.asStateFlow()

    /** Kept once the typing has stopped and the search has actually matched. */
    private fun rememberSearchLater(query: String) {
        rememberJob?.cancel()
        if (query.isBlank()) return
        rememberJob = viewModelScope.launch {
            delay(SEARCH_SETTLE_MS)
            if (_wordQuery.value == query && visibleWords.value.isNotEmpty()) {
                searchHistory.record(SearchScope.WORDS, query)
            }
        }
    }

    fun onWordQueryChange(query: String) {
        _wordQuery.value = query
        rememberSearchLater(query)
    }

    fun onToggleWordSort() {
        _wordsAlphabetical.value = !_wordsAlphabetical.value
    }

    /**
     * Saved words, filtered and ordered for the screen.
     *
     * The search looks at the translation and the surrounding line as well as
     * the word: a reader hunting for something they saved is as likely to
     * remember the English or the sentence as the Catalan itself.
     */
    val visibleWords: StateFlow<List<WordBookmarkEntity>> =
        combine(savedWords, _wordQuery, _wordsAlphabetical) { words, query, alphabetical ->
            val needle = query.trim()
            val matched = if (needle.isEmpty()) {
                words
            } else {
                words.filter {
                    it.word.contains(needle, ignoreCase = true) ||
                        it.translation?.contains(needle, ignoreCase = true) == true ||
                        it.context?.contains(needle, ignoreCase = true) == true
                }
            }
            if (alphabetical) {
                matched.sortedBy { it.word.lowercase() }
            } else {
                matched
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tags keyed by document, so bookmark rows match the library's. */
    val tagsByDocument: StateFlow<Map<String, List<DocumentTag>>> =
        libraryData.observeTagsByDocument()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())


    fun removeDocumentBookmark(document: DocumentEntity) = viewModelScope.launch {
        libraryData.setDocumentBookmarked(document.uriString, document.displayName, false)
    }

    fun removeFromReadLater(document: DocumentEntity) = viewModelScope.launch {
        libraryData.setDocumentReadLater(document.uriString, document.displayName, false)
    }

    fun removePageBookmark(bookmarkId: Long) = viewModelScope.launch {
        libraryData.deletePageBookmark(bookmarkId)
    }

    /**
     * Everything the collections' rows need to offer their hold-to-open menu.
     *
     * Tags belong to a document rather than to a bookmark, so the picker raised
     * from a starred PDF is the same picker the library raises, working on the
     * same rows — which is why a tag made here turns up on the library row for
     * the same PDF a moment later.
     */
    val tags: StateFlow<List<TagEntity>> = libraryData.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val names: StateFlow<DocumentNames> = libraryData.observeDocumentNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentNames.Empty)

    fun onToggleTag(uriString: String, displayName: String, tag: TagEntity) =
        viewModelScope.launch { libraryData.toggleTag(uriString, displayName, tag.id) }

    fun onCreateTag(uriString: String, displayName: String, name: String, colorArgb: Int) =
        viewModelScope.launch {
            val tag = libraryData.createOrGetTag(name, colorArgb) ?: return@launch
            libraryData.toggleTag(uriString, displayName, tag.id)
        }

    fun onRecolourTag(tag: TagEntity, colorArgb: Int) = viewModelScope.launch {
        libraryData.renameTag(tag.id, tag.name, colorArgb)
    }

    fun onRenameTag(tag: TagEntity, name: String) = viewModelScope.launch {
        libraryData.renameTag(tag.id, name, tag.colorArgb)
    }

    fun onDeleteTag(tag: TagEntity) = viewModelScope.launch {
        libraryData.deleteTag(tag.id)
    }

    fun onRenameDocument(uriString: String, displayName: String, name: String) =
        viewModelScope.launch { libraryData.setDocumentName(uriString, displayName, name) }


    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LlegeixApp
                BookmarksViewModel(app.libraryDataRepository, app.searchHistoryRepository)
            }
        }
    }
}

/** Long enough that a pause means the reader stopped to read the result. */
private const val SEARCH_SETTLE_MS = 1_200L

/** How many days the activity strip covers. */
const val ACTIVITY_DAYS = 14

/**
 * Midnight at the start of the day [now] falls in, in the device's own zone.
 *
 * A real calendar day rather than the rolling 24-hour block [countPerDay] uses,
 * and the two are different on purpose. The strip is a texture and would flicker
 * if its buckets slid at midnight; "today" is a word the reader uses about the
 * calendar, and a list headed *today* that quietly included last night's words
 * until lunchtime would be lying about the only thing it says.
 */
internal fun startOfToday(now: Long): Long =
    Instant.ofEpochMilli(now)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

/**
 * The words saved or practised since [since], newest first.
 *
 * A word counts once however many times it was touched: this is a list of
 * words, not of events, and a word saved this morning and practised this
 * afternoon is one word the reader worked on.
 */
internal fun learnedSince(
    words: List<WordBookmarkEntity>,
    since: Long,
): List<WordBookmarkEntity> = words
    .filter { it.createdAt >= since || (it.lastReviewedAt ?: 0L) >= since }
    .sortedByDescending { maxOf(it.createdAt, it.lastReviewedAt ?: 0L) }

/**
 * How many words were saved on each of the last [ACTIVITY_DAYS] days.
 *
 * Days are counted back from [now] in whole 24-hour blocks rather than from
 * midnight. It is a texture rather than a diary — the point is whether the
 * marks are there at all — and counting in blocks means the strip does not
 * silently redraw itself as the clock passes midnight.
 */
internal fun countPerDay(words: List<WordBookmarkEntity>, now: Long): List<Int> {
    val day = 24L * 60L * 60L * 1000L
    val counts = IntArray(ACTIVITY_DAYS)
    for (word in words) {
        val age = now - word.createdAt
        if (age < 0) continue
        val daysAgo = (age / day).toInt()
        if (daysAgo < ACTIVITY_DAYS) counts[ACTIVITY_DAYS - 1 - daysAgo]++
    }
    return counts.toList()
}
