package com.david.catalanpdfreader

import android.app.Application
import com.david.catalanpdfreader.data.db.CatalanPdfDatabase
import com.david.catalanpdfreader.data.source.LibraryDataRepository
import com.david.catalanpdfreader.data.source.PdfRepository

/**
 * Holds the app's singletons.
 *
 * A hand-rolled container is enough at this size; if the graph grows past a
 * handful of objects once the translation pipeline lands, this is the seam to
 * swap for Hilt without touching call sites.
 */
class CatalanPdfReaderApp : Application() {

    /** What is on the device. */
    val pdfRepository: PdfRepository by lazy { PdfRepository(this) }

    /** What the app remembers about it. */
    val libraryDataRepository: LibraryDataRepository by lazy {
        LibraryDataRepository(CatalanPdfDatabase.build(this))
    }
}
