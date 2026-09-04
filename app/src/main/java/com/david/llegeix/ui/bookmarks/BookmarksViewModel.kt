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
import com.david.llegeix.data.db.entity.WordBookmarkEntity
import com.david.llegeix.data.settings.SearchHistoryRepository
import com.david.llegeix.data.settings.SearchScope
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

    private var rememberJob: Job? = null

    val bookmarkedDocuments: StateFlow<List<DocumentEntity>> =
        libraryData.observeBookmarkedDocuments()
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

    fun removePageBookmark(bookmarkId: Long) = viewModelScope.launch {
        libraryData.deletePageBookmark(bookmarkId)
    }


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
