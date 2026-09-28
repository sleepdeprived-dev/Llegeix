package com.david.llegeix.data.flashcards

import com.david.llegeix.data.settings.javaPrefsStore
import com.david.llegeix.platform.desktopPrefsRoot

/** The Mac's flashcard preferences, beside its other settings. */
fun desktopFlashcardPrefs(): FlashcardPrefs = FlashcardPrefs(
    javaPrefsStore(desktopPrefsRoot.node(FlashcardPrefs.PREFS_NAME)),
)

internal actual val USER_AGENT: String =
    "Llegeix (Mac vocabulary app; https://github.com/sleepdeprived-dev/Llegeix-releases)"
