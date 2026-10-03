package com.david.llegeix.data

import com.david.llegeix.data.flashcards.FlashcardRepository
import com.david.llegeix.data.settings.SearchHistoryRepository
import com.david.llegeix.data.settings.SettingsRepository
import com.david.llegeix.data.source.DesktopPdfLibrary
import com.david.llegeix.data.source.LibraryDataRepository
import com.david.llegeix.pdf.PdfThumbnails
import com.david.llegeix.platform.desktopDataDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Mac's "erase everything", in the phone's order: what the app remembers
 * about documents, the decks and their pictures, the covers, the searches, the
 * translation models it fetched, the folders and files it was given, and last
 * the settings. The PDFs themselves are never touched; they were never the
 * app's.
 */
class DesktopDataEraser(
    private val library: DesktopPdfLibrary,
    private val libraryData: LibraryDataRepository,
    private val flashcards: FlashcardRepository,
    private val settings: SettingsRepository,
    private val searchHistory: SearchHistoryRepository,
    private val thumbnails: PdfThumbnails,
) : DataEraser {

    override suspend fun eraseEverything() = withContext(Dispatchers.IO) {
        library.forgetAll()
        runCatching { desktopDataDirectory.resolve(TRANSLATION_MODELS).deleteRecursively() }
        runCatching { desktopDataDirectory.resolve(UPDATES).deleteRecursively() }
        runCatching { libraryData.eraseEverything() }
        runCatching { flashcards.eraseEverything() }
        thumbnails.clear()
        searchHistory.clear()
        settings.resetToDefaults()
        // After the reset, as on the phone, so the sweep stays off: it would
        // otherwise fill the library again the moment the reader looked.
        settings.setDeviceScanOptOut(true)
    }

    private companion object {
        /** Where Bergamot fetches its models to. */
        const val TRANSLATION_MODELS = "translation"
        const val UPDATES = "updates"
    }
}
