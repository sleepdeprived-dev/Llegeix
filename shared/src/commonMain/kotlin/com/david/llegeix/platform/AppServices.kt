package com.david.llegeix.platform

import com.david.llegeix.data.flashcards.FlashcardRepository
import com.david.llegeix.data.flashcards.PictureSearch
import com.david.llegeix.data.settings.SearchHistoryRepository
import com.david.llegeix.data.settings.SettingsRepository
import com.david.llegeix.data.source.LibraryDataRepository
import com.david.llegeix.data.source.PdfLibrary
import com.david.llegeix.lang.Speech
import com.david.llegeix.pdf.PdfPageRenderer
import com.david.llegeix.pdf.PdfThumbnails
import com.david.llegeix.update.AppUpdater
import com.david.llegeix.data.DataEraser

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
    val libraryDataRepository: LibraryDataRepository
    val searchHistoryRepository: SearchHistoryRepository
    val pdfLibrary: PdfLibrary
    val pdfThumbnails: PdfThumbnails
    val updates: AppUpdater
    val dataEraser: DataEraser

    /**
     * Open the PDF behind [uriString] for rendering: a content Uri on the
     * phone, a file URI on the Mac. Throws IOException when it cannot be read.
     */
    suspend fun openPdf(uriString: String): PdfPageRenderer
}

object Services {
    /** Set once, first thing at start-up, before any screen can ask for it. */
    lateinit var app: AppServices
}
