package com.david.llegeix.desktop

import com.david.llegeix.data.db.LlegeixDatabase
import com.david.llegeix.data.db.openDesktop
import com.david.llegeix.data.flashcards.FlashcardBackupFiles
import com.david.llegeix.data.flashcards.FlashcardImages
import com.david.llegeix.data.flashcards.FlashcardRepository
import com.david.llegeix.data.flashcards.PictureSearch
import com.david.llegeix.data.flashcards.desktopFlashcardPrefs
import com.david.llegeix.data.settings.SearchHistoryRepository
import com.david.llegeix.data.settings.SettingsRepository
import com.david.llegeix.data.settings.desktopSearchHistory
import com.david.llegeix.data.settings.desktopSettingsRepository
import com.david.llegeix.data.source.DesktopPdfLibrary
import com.david.llegeix.data.source.LibraryDataRepository
import com.david.llegeix.data.source.desktopPdfLibrary
import com.david.llegeix.pdf.PdfPageRenderer
import com.david.llegeix.pdf.PdfThumbnails
import com.david.llegeix.pdf.PdfiumPageRenderer
import com.david.llegeix.pdf.open
import com.david.llegeix.data.DataEraser
import com.david.llegeix.data.DesktopDataEraser
import com.david.llegeix.update.AppUpdater
import com.david.llegeix.update.desktopUpdater
import java.io.File
import java.net.URI
import com.david.llegeix.lang.ApertureLexicon
import com.david.llegeix.lang.CatalanIpa
import com.david.llegeix.lang.Speech
import com.david.llegeix.platform.AppServices
import com.david.llegeix.platform.DesktopAppFiles
import com.david.llegeix.platform.Services
import com.david.llegeix.platform.desktopDataDirectory

/**
 * What the Mac app keeps for its lifetime, as LlegeixApp does on the phone.
 *
 * The library, saved words and flashcards live in Application Support, where
 * a Mac app's own data belongs; the settings in the user's preferences.
 */
object DesktopApp : AppServices {

    /** First thing at start-up, as LlegeixApp.onCreate does on the phone. */
    fun start() {
        Services.app = this
        CatalanIpa.useLexicon(ApertureLexicon.get())
    }

    private val files by lazy { DesktopAppFiles() }

    private val database: LlegeixDatabase by lazy {
        LlegeixDatabase.openDesktop(desktopDataDirectory.resolve("llegeix.db"))
    }

    override val settingsRepository: SettingsRepository by lazy { desktopSettingsRepository() }

    override val speech: Speech by lazy { Speech() }

    override val flashcardRepository: FlashcardRepository by lazy {
        FlashcardRepository(
            database,
            FlashcardImages(files),
            FlashcardBackupFiles(files),
            desktopFlashcardPrefs(),
        )
    }

    override val pictureSearch: PictureSearch by lazy { PictureSearch(files) }

    /** Saved words, and in time the library's folders, bookmarks and reading history. */
    override val libraryDataRepository: LibraryDataRepository by lazy {
        LibraryDataRepository(database)
    }

    /** What has been looked up lately, in the dictionary and the saved words. */
    override val searchHistoryRepository: SearchHistoryRepository by lazy { desktopSearchHistory() }

    /** Folders chosen, files chosen, and Spotlight. */
    override val pdfLibrary: DesktopPdfLibrary by lazy { desktopPdfLibrary(settingsRepository) }

    override val pdfThumbnails: PdfThumbnails by lazy { PdfThumbnails(::openPdf) }

    /** The disk image in each GitHub release, beside the phone's APKs. */
    override val updates: AppUpdater by lazy { desktopUpdater() }

    override val dataEraser: DataEraser by lazy {
        DesktopDataEraser(
            pdfLibrary,
            libraryDataRepository,
            flashcardRepository,
            settingsRepository,
            searchHistoryRepository,
            pdfThumbnails,
        )
    }

    override suspend fun openPdf(uriString: String): PdfPageRenderer =
        PdfiumPageRenderer.open(File(URI(uriString)))
}
