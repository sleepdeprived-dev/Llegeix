package com.david.llegeix.data.flashcards

import android.content.Context
import com.david.llegeix.data.settings.SharedPreferencesStore

/** The phone's flashcard preferences, in the SharedPreferences file they have always been in. */
fun FlashcardPrefs(context: Context): FlashcardPrefs = FlashcardPrefs(
    SharedPreferencesStore(
        context.applicationContext.getSharedPreferences(FlashcardPrefs.PREFS_NAME, Context.MODE_PRIVATE),
    ),
)

internal actual val USER_AGENT: String =
    "Llegeix (Android vocabulary app; https://github.com/sleepdeprived-dev/Llegeix-releases)"
