package com.david.llegeix.desktop

import com.david.llegeix.data.db.LlegeixDatabase
import com.david.llegeix.data.db.openDesktop
import com.david.llegeix.data.settings.SettingsRepository
import com.david.llegeix.data.settings.desktopSettingsRepository
import java.io.File

/**
 * What the Mac app keeps for its lifetime, as LlegeixApp does on the phone.
 *
 * The library, saved words and flashcards live in Application Support, where
 * a Mac app's own data belongs; the settings in the user's preferences.
 */
object DesktopApp {

    val dataDirectory: File =
        File(System.getProperty("user.home"), "Library/Application Support/Llegeix")

    val settings: SettingsRepository by lazy { desktopSettingsRepository() }

    val database: LlegeixDatabase by lazy {
        LlegeixDatabase.openDesktop(dataDirectory.resolve("llegeix.db"))
    }
}
