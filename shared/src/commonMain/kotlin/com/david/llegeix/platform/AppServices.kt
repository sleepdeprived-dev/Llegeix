package com.david.llegeix.platform

import com.david.llegeix.data.flashcards.FlashcardRepository
import com.david.llegeix.data.flashcards.PictureSearch
import com.david.llegeix.data.settings.SettingsRepository
import com.david.llegeix.lang.Speech

/**
 * What the app keeps for its lifetime, provided by the platform when it starts:
 * LlegeixApp on the phone, DesktopApp on the Mac.
 *
 * Shared screens and ViewModels reach their repositories through [Services]
 * rather than through Android's Application, which the Mac does not have. It
 * grows as the code that needs it moves here.
 */
interface AppServices {
    val settingsRepository: SettingsRepository
    val speech: Speech
    val flashcardRepository: FlashcardRepository
    val pictureSearch: PictureSearch
}

object Services {
    /** Set once, first thing at start-up, before any screen can ask for it. */
    lateinit var app: AppServices
}
