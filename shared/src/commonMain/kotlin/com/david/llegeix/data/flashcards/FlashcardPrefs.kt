package com.david.llegeix.data.flashcards

import com.david.llegeix.data.settings.SettingsStore

/**
 * Which way round the reader last chose to practise, so play offers it first.
 * There is no language to remember: a card's meaning is always its Romanian.
 */
class FlashcardPrefs(private val prefs: SettingsStore) {

    var direction: StudyDirection
        get() = StudyDirection.fromName(prefs.getString(KEY_DIRECTION, null))
        set(value) = prefs.putString(KEY_DIRECTION, value.name)

    /** How the tab lists its collections and decks. */
    var listSort: ListSort
        get() = ListSort.fromName(prefs.getString(KEY_LIST_SORT, null))
        set(value) = prefs.putString(KEY_LIST_SORT, value.name)

    /** How a deck lists its cards. */
    var cardSort: CardSort
        get() = CardSort.fromName(prefs.getString(KEY_CARD_SORT, null))
        set(value) = prefs.putString(KEY_CARD_SORT, value.name)

    /** How the tab lays its collections and decks out, by name. */
    var listLayout: String?
        get() = prefs.getString(KEY_LIST_LAYOUT, null)
        set(value) = if (value != null) prefs.putString(KEY_LIST_LAYOUT, value) else prefs.remove(KEY_LIST_LAYOUT)

    fun clear() = prefs.clear()

    companion object {
        /** The file the phone has always kept them in. */
        const val PREFS_NAME = "flashcards"
        private const val KEY_DIRECTION = "direction"
        const val KEY_LIST_SORT = "listSort"
        const val KEY_CARD_SORT = "cardSort"
        const val KEY_LIST_LAYOUT = "listLayout"
    }
}
