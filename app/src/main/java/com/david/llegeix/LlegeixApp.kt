package com.david.llegeix

import android.app.Application
import com.david.llegeix.data.db.LlegeixDatabase
import com.david.llegeix.data.exam.ExamRepository
import com.david.llegeix.data.settings.SearchHistoryRepository
import com.david.llegeix.data.settings.SettingsRepository
import com.david.llegeix.lang.ApertureLexicon
import com.david.llegeix.lang.CatalanIpa
import com.david.llegeix.lang.Speech
import com.david.llegeix.data.source.LibraryDataRepository
import com.david.llegeix.data.source.PdfRepository
import com.david.llegeix.data.DataEraser
import com.david.llegeix.pdf.PdfThumbnails
import com.david.llegeix.update.UpdateRepository

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
    }

    /** What is on the device. */
    val pdfRepository: PdfRepository by lazy { PdfRepository(this, settingsRepository) }

    /**
     * The one database, shared.
     *
     * Hoisted out of [libraryDataRepository], where it used to be built inline.
     * Room permits only one open instance per file, and the moment a second
     * repository needed it — exams — building it in whichever lazy happened to
     * run first would have been two instances of the same file.
     */
    private val database: LlegeixDatabase by lazy { LlegeixDatabase.build(this) }

    /** What the app remembers about it. */
    val libraryDataRepository: LibraryDataRepository by lazy {
        LibraryDataRepository(database)
    }

    /** The exam papers the reader has brought in, and the copies behind them. */
    val examRepository: ExamRepository by lazy { ExamRepository(this, database) }

    /** First-page covers for the library, shared so the cache outlives a screen. */
    val pdfThumbnails: PdfThumbnails by lazy { PdfThumbnails(this) }

    /** Puts the app back to how it was before it was ever opened. */
    val dataEraser: DataEraser by lazy {
        DataEraser(
            this,
            libraryDataRepository,
            settingsRepository,
            searchHistoryRepository,
            pdfThumbnails,
            examRepository,
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
}
