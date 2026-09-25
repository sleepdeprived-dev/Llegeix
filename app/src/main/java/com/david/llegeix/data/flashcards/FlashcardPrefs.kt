package com.david.llegeix.data.flashcards

import android.content.Context
import androidx.core.content.edit

/**
 * Which way round the reader last chose to practise, so play offers it first.
 * There is no language to remember: practice always shows a card's English and
 * Romanian together.
 */
class FlashcardPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("flashcards", Context.MODE_PRIVATE)

    var direction: StudyDirection
        get() = StudyDirection.fromName(prefs.getString(KEY_DIRECTION, null))
        set(value) = prefs.edit { putString(KEY_DIRECTION, value.name) }

    /** How the tab lists its collections and decks. */
    var listSort: ListSort
        get() = ListSort.fromName(prefs.getString(KEY_LIST_SORT, null))
        set(value) = prefs.edit { putString(KEY_LIST_SORT, value.name) }

    /** How a deck lists its cards. */
    var cardSort: CardSort
        get() = CardSort.fromName(prefs.getString(KEY_CARD_SORT, null))
        set(value) = prefs.edit { putString(KEY_CARD_SORT, value.name) }

    fun clear() = prefs.edit { clear() }

    private companion object {
        const val KEY_DIRECTION = "direction"
        const val KEY_LIST_SORT = "listSort"
        const val KEY_CARD_SORT = "cardSort"
    }
}
