package com.david.llegeix.data.flashcards

import android.content.Context
import androidx.core.content.edit

/**
 * How the reader last chose to practise: which way round, and in which
 * language. Remembered so the Flashcards tab opens the way it was left rather
 * than asking the same two questions every time.
 */
class FlashcardPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("flashcards", Context.MODE_PRIVATE)

    var direction: StudyDirection
        get() = StudyDirection.fromName(prefs.getString(KEY_DIRECTION, null))
        set(value) = prefs.edit { putString(KEY_DIRECTION, value.name) }

    var language: MeaningLanguage
        get() = MeaningLanguage.fromName(prefs.getString(KEY_LANGUAGE, null))
        set(value) = prefs.edit { putString(KEY_LANGUAGE, value.name) }

    fun clear() = prefs.edit { clear() }

    private companion object {
        const val KEY_DIRECTION = "direction"
        const val KEY_LANGUAGE = "language"
    }
}
