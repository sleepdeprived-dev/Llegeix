package com.david.llegeix

import android.app.Application
import com.david.llegeix.data.db.LlegeixDatabase
import com.david.llegeix.data.settings.SettingsRepository
import com.david.llegeix.data.source.LibraryDataRepository
import com.david.llegeix.data.source.PdfRepository

/**
 * Holds the app's singletons.
 *
 * A hand-rolled container is enough at this size; if the graph grows past a
 * handful of objects once the translation pipeline lands, this is the seam to
 * swap for Hilt without touching call sites.
 */
class LlegeixApp : Application() {

    /** What is on the device. */
    val pdfRepository: PdfRepository by lazy { PdfRepository(this) }

    /** What the app remembers about it. */
    val libraryDataRepository: LibraryDataRepository by lazy {
        LibraryDataRepository(LlegeixDatabase.build(this))
    }

    /** Theme, accent and language, as chosen on the Settings screen. */
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
}
