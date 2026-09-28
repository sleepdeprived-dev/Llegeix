package com.david.llegeix.data.flashcards

import com.david.llegeix.data.settings.javaPrefsStore
import java.util.prefs.Preferences

/** The Mac's flashcard preferences, beside its other settings. */
fun desktopFlashcardPrefs(): FlashcardPrefs = FlashcardPrefs(
    javaPrefsStore(Preferences.userRoot().node("com/david/llegeix").node(FlashcardPrefs.PREFS_NAME)),
)

internal actual val USER_AGENT: String =
    "Llegeix (Mac vocabulary app; https://github.com/sleepdeprived-dev/Llegeix-releases)"
