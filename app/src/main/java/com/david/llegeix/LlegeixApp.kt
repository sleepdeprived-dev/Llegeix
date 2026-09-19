package com.david.llegeix

import android.app.Application
import com.david.llegeix.data.db.LlegeixDatabase
import com.david.llegeix.data.settings.SearchHistoryRepository
import com.david.llegeix.data.settings.SettingsRepository
import com.david.llegeix.lang.ApertureLexicon
import com.david.llegeix.lang.CatalanIpa
import com.david.llegeix.lang.Speech
import com.david.llegeix.data.source.LibraryDataRepository
import com.david.llegeix.data.source.PdfRepository
import com.david.llegeix.data.DataEraser
import com.david.llegeix.data.flashcards.FlashcardImages
import com.david.llegeix.data.flashcards.FlashcardRepository
import com.david.llegeix.pdf.PdfThumbnails
import com.david.llegeix.update.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Holds the app's singletons.
 *
 * A hand-rolled container is enough at this size; if the graph grows past a
 * handful of objects once the translation pipeline lands, this is the seam to
 * swap for Hilt without touching call sites.
 */
class LlegeixApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // The transcriber is a plain object so it can be unit tested without a
        // context; this is where the device hands it the word list.
        CatalanIpa.useLexicon(ApertureLexicon.get(this))
        deleteStaleExamFiles()
    }

    /**
     * Reclaim the disk the exams feature was using, once, on the upgrade to v4.1.
     *
     * Exams were the one thing this app *stored* rather than merely referred to:
     * every paper was copied into `files/exams` and kept there, alongside any
     * recordings that came with it, which on a reader who had imported a few
     * sample papers is tens of megabytes. Dropping the tables in
     * [com.david.llegeix.data.db.MIGRATION_12_13] forgets the rows that named
     * those files; SQLite cannot unlink anything, so without this the files
     * themselves would sit in the app's private storage for ever with nothing
     * left in the app that could ever open, list or delete them.
     *
     * No flag is needed to make it happen only once. Nothing in the app creates
     * this directory any more, so after the first pass there is nothing to find,
     * and the check costs one `exists()` per launch.
     */
    private fun deleteStaleExamFiles() {
        val leftovers = File(filesDir, STALE_EXAM_DIRECTORY)
        if (!leftovers.exists()) return
        // Off the main thread: this is a recursive delete over files that can
        // run to tens of megabytes, and nothing on screen is waiting for it.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { leftovers.deleteRecursively() }
        }
    }

    /** What is on the device. */
    val pdfRepository: PdfRepository by lazy { PdfRepository(this, settingsRepository) }

    /**
     * The one database, shared.
     *
     * Hoisted out of [libraryDataRepository], where it used to be built inline.
     * Room permits only one open instance per file, so the builder lives here
     * rather than inside whichever repository happens to be constructed first.
     */
    private val database: LlegeixDatabase by lazy { LlegeixDatabase.build(this) }

    /** What the app remembers about it. */
    val libraryDataRepository: LibraryDataRepository by lazy {
        LibraryDataRepository(database)
    }

    /** The reader's own decks of vocabulary cards, and their pictures. */
    val flashcardRepository: FlashcardRepository by lazy {
        FlashcardRepository(database, FlashcardImages(this))
    }

    /** First-page covers for the library, shared so the cache outlives a screen. */
    val pdfThumbnails: PdfThumbnails by lazy { PdfThumbnails(this) }

    /** Puts the app back to how it was before it was ever opened. */
    val dataEraser: DataEraser by lazy {
        DataEraser(
            this,
            libraryDataRepository,
            flashcardRepository,
            settingsRepository,
            searchHistoryRepository,
            pdfThumbnails,
        )
    }

    /** Theme, accent, language and reading preferences. */
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    /** What the reader has searched for lately, in the library and in a page. */
    val searchHistoryRepository: SearchHistoryRepository by lazy {
        SearchHistoryRepository(this)
    }

    /**
     * The device's own speech engine, for saying a Catalan word out loud.
     *
     * Lazy on purpose: starting it binds to another app's service, and a reader
     * who never opens a word should never cause that to happen. Once started it
     * lives as long as the process, because a word looked up is very often
     * followed by another one and paying the start-up cost per lookup would put
     * a pause in front of the first press every time.
     */
    val speech: Speech by lazy { Speech(this) }

    /** The app's only way of learning that a newer version of itself exists. */
    val updateRepository: UpdateRepository by lazy { UpdateRepository(this) }

    private companion object {
        /** Where exam papers were copied to, before v4.1 dropped the feature. */
        const val STALE_EXAM_DIRECTORY = "exams"
    }
}
