package com.david.llegeix.data.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which pile of past searches a field draws on. */
enum class SearchScope(val key: String) {
    /** Looking for a document by name, in the library. */
    DOCUMENTS("documents"),

    /**
     * Looking for a word: in a document with find, or among the saved words.
     *
     * One pile for both on purpose. To somebody reading in a language they are
     * learning, a word chased across a page and a word kept in their own list
     * are the same word — and the one they hunted for last week is exactly the
     * one they go looking for again.
     */
    WORDS("words"),
}

/** At most this many are kept: a history you have to scroll is not a shortcut. */
const val SEARCH_HISTORY_LIMIT = 8

/**
 * [history] with [query] at the front.
 *
 * De-duplicated without regard to case, so searching the same word twice moves
 * it up the list rather than filling the list with itself.
 */
fun remembered(
    history: List<String>,
    query: String,
    limit: Int = SEARCH_HISTORY_LIMIT,
): List<String> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return history
    return (listOf(trimmed) + history.filterNot { it.equals(trimmed, ignoreCase = true) })
        .take(limit)
}

/**
 * What the reader has searched for lately.
 *
 * Only searches that found something are ever recorded — the caller decides,
 * and every caller waits for a result before asking. That one rule is what
 * keeps the list worth offering: half-typed words and misspellings never reach
 * it. Even so, a word can be worth forgetting on its own, so [forgetOne] exists
 * beside the clear-the-lot: "all or nothing" is the wrong offer to make about a
 * list of five words, four of which the reader still wants.
 *
 * Its own preferences file rather than a table. This is a convenience, not
 * data: losing it costs nobody anything, and it should never be the reason a
 * database migration exists. Each platform hands it that file as a
 * [SettingsStore]: SharedPreferences on the phone, the user's preferences on
 * the Mac.
 */
class SearchHistoryRepository(private val prefs: SettingsStore) {

    private val flows: Map<SearchScope, MutableStateFlow<List<String>>> =
        SearchScope.entries.associateWith { MutableStateFlow(read(it)) }

    fun history(scope: SearchScope): StateFlow<List<String>> = flow(scope).asStateFlow()

    /** Record a search that found something. */
    fun record(scope: SearchScope, query: String) {
        val updated = remembered(flow(scope).value, query)
        if (updated == flow(scope).value) return
        write(scope, updated)
    }

    /** Forget one scope's history, from the "clear" beside it. */
    fun forget(scope: SearchScope) = write(scope, emptyList())

    /**
     * Drop a single past search, from the cross on its own row.
     *
     * Matched without regard to case, the same way [remembered] de-duplicates,
     * so what is on screen is what goes.
     */
    fun forgetOne(scope: SearchScope, query: String) {
        val remaining = flow(scope).value.filterNot { it.equals(query, ignoreCase = true) }
        if (remaining.size != flow(scope).value.size) write(scope, remaining)
    }

    /** Forget the lot, for "erase everything". */
    fun clear() = SearchScope.entries.forEach { forget(it) }

    private fun flow(scope: SearchScope) = flows.getValue(scope)

    private fun write(scope: SearchScope, queries: List<String>) {
        prefs.putString(scope.key, queries.joinToString(SEPARATOR))
        flow(scope).value = queries
    }

    private fun read(scope: SearchScope): List<String> =
        prefs.getString(scope.key, null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    companion object {
        const val PREFS_NAME = "llegeix.searches"

        /** Safe as a separator: every field that feeds this one is single-line. */
        private const val SEPARATOR = "\n"
    }
}
